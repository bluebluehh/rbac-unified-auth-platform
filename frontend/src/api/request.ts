/**
 * 统一请求层（文档 §10.10 / §10.9）：
 * - 自动注入 Authorization / X-Request-Id；写操作通过 ifMatch 配置注入 If-Match（带引号）。
 * - 统一解包 Result.data；全项目页面均接收解包后的 VO。
 * - 集中处理错误分支：会话 401 清 Token 退出（并发只跳一次）、403 刷新权限、
 *   412 交页面处理版本冲突、400 fieldErrors 交表单、429 读 Retry-After、503 提示不可用。
 * - Token 仅存内存（Pinia）；刷新页面即重新登录，符合安全设计。
 */
import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { ApiErrorBody, ApiSuccess } from '@/types'
import { ApiError } from './errors'
import { genRequestId } from '@/utils/requestId'
import { useAuthStore } from '@/stores/auth'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 乐观锁版本（字符串），非空时自动注入 If-Match: "<version>" */
    ifMatch?: string
    /** 为 true 时拦截器不再弹出错误提示（页面自行处理，如登录表单） */
    silent?: boolean
  }
}

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 15000,
})

service.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.set('Authorization', `Bearer ${auth.token}`)
  }
  if (!config.headers.has('X-Request-Id')) {
    config.headers.set('X-Request-Id', genRequestId())
  }
  if (config.ifMatch) {
    // 文档要求：前端由正文 version 构造带引号的 If-Match
    config.headers.set('If-Match', `"${config.ifMatch}"`)
  }
  return config
})

/* 会话 401 退出跳转标志：并发多个 401 只执行一次，避免提示风暴 */
let sessionExpiredHandled = false
function handleSessionExpired(): void {
  if (sessionExpiredHandled) return
  sessionExpiredHandled = true
  // 硬跳转让内存 Token 天然失效；重置标志以便下次会话失效可再触发
  window.location.assign('/login?reason=session-expired')
}

function toApiError(error: AxiosError): ApiError {
  const status = error.response?.status ?? 0
  const body = error.response?.data as ApiErrorBody | undefined
  const retryAfterRaw = error.response?.headers?.['retry-after']
  const retryAfterSeconds = retryAfterRaw != null ? Number(retryAfterRaw) : null

  if (body && typeof body.code === 'string') {
    return new ApiError({
      httpStatus: status,
      code: body.code,
      message: body.message ?? '请求失败',
      requestId: body.requestId,
      fieldErrors: body.fieldErrors,
      retryAfterSeconds: Number.isFinite(retryAfterSeconds) ? retryAfterSeconds : null,
    })
  }

  // 非契约响应（网关/网络/超时）
  const message = status === 0 ? '网络异常，请检查连接后重试' : `服务器响应异常（HTTP ${status}）`
  return new ApiError({ httpStatus: status, code: 'INTERNAL_ERROR', message })
}

service.interceptors.response.use(
  (response) => {
    const body = response.data as ApiSuccess<unknown>
    if (body && body.code === 'OK') {
      return body.data as never
    }
    // 2xx 但 body 不是 OK（契约外情况）
    return Promise.reject(
      new ApiError({
        httpStatus: response.status,
        code: body?.code ?? 'INTERNAL_ERROR',
        message: body?.message ?? '响应格式异常',
        requestId: body?.requestId,
      }),
    )
  },
  (error: AxiosError) => {
    const apiError = toApiError(error)
    const config = (error.config ?? {}) as AxiosRequestConfig & { silent?: boolean }
    const isLoginRequest = typeof config.url === 'string' && config.url.includes('/auth/login')

    // 1) 会话失效（非登录接口的 401 SESSION_INVALID）：清 Token 退出，只跳一次
    if (apiError.code === 'SESSION_INVALID' && !isLoginRequest) {
      handleSessionExpired()
      return Promise.reject(apiError)
    }

    // 2) 改密要求：跳改密页（受限会话唯一去处）
    if (apiError.code === 'PASSWORD_CHANGE_REQUIRED') {
      passwordChangeRedirect?.()
      return Promise.reject(apiError)
    }

    // 3) 403 权限拒绝后刷新 A06，并继续由页面显示原始错误
    if (apiError.code === 'PERMISSION_DENIED') {
      permissionRefresher?.()
    }

    // 4) 400/404/412 与登录错误由页面处理，避免重复提示
    if (
      apiError.httpStatus === 412 ||
      apiError.httpStatus === 400 ||
      apiError.httpStatus === 404 ||
      isLoginRequest
    ) {
      return Promise.reject(apiError)
    }

    // 5) 其余错误集中提示（silent 请求除外）
    if (!config.silent) {
      apiError.userNotified = true
      void showGlobalError(apiError)
    }
    return Promise.reject(apiError)
  },
)

let messageShower: ((msg: string, type: 'error' | 'warning') => void) | null = null
let permissionRefresher: (() => void) | null = null
let passwordChangeRedirect: (() => void) | null = null
/** 由 main.ts 注入 ElMessage，避免本层直接依赖 UI 库 */
export function registerMessageShower(fn: (msg: string, type: 'error' | 'warning') => void): void {
  messageShower = fn
}

/** 由 main.ts 注入权限刷新动作，收到 403 后重新读取 A06。 */
export function registerPermissionRefresher(fn: () => void): void {
  permissionRefresher = fn
}

/** 由 main.ts 注入前端路由跳转，避免刷新页面时丢失仅保存在内存中的 Token。 */
export function registerPasswordChangeRedirect(fn: () => void): void {
  passwordChangeRedirect = fn
}

async function showGlobalError(e: ApiError): Promise<void> {
  let msg: string
  switch (e.code) {
    case 'PERMISSION_DENIED':
      msg = '你没有执行此操作的权限'
      break
    case 'RATE_LIMITED':
      msg = e.retryAfterSeconds ? `请求过于频繁，请 ${e.retryAfterSeconds} 秒后重试` : '请求过于频繁，请稍后重试'
      break
    case 'AUTH_UNAVAILABLE':
      msg = '权限服务暂不可用，请稍后重试'
      break
    case 'PRECONDITION_REQUIRED':
      msg = '页面信息已过期，请重新打开'
      break
    default:
      msg = e.isNetworkError ? e.message : e.message || '操作失败'
  }
  messageShower?.(msg, e.httpStatus === 429 ? 'warning' : 'error')
}

/* ---------- 统一动词封装：页面层一律通过这些函数发请求 ---------- */

export function httpGet<T>(url: string, params?: object): Promise<T> {
  return service.get(url, { params }) as Promise<T>
}

export function httpPost<T>(url: string, data?: unknown, ifMatch?: string): Promise<T> {
  return service.post(url, data ?? {}, { ifMatch }) as Promise<T>
}

/** 契约明确“无正文”的 POST 接口使用，不发送空对象正文。 */
export function httpPostNoBody<T>(url: string): Promise<T> {
  return service.post(url, undefined) as Promise<T>
}

export function httpPut<T>(url: string, data?: unknown, ifMatch?: string): Promise<T> {
  return service.put(url, data ?? {}, { ifMatch }) as Promise<T>
}

export function httpPatch<T>(url: string, data?: unknown, ifMatch?: string): Promise<T> {
  return service.patch(url, data ?? {}, { ifMatch }) as Promise<T>
}
