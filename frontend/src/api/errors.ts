/**
 * 统一业务错误对象：页面层只 catch ApiError，
 * 携带 HTTP 状态、业务错误码、字段错误与 Retry-After（§10.9 / §10.10）。
 */
import type { FieldError } from '@/types'

export class ApiError extends Error {
  readonly httpStatus: number
  readonly code: string
  readonly requestId: string | null
  readonly fieldErrors: FieldError[]
  readonly retryAfterSeconds: number | null
  /** 请求层已向用户显示统一提示，页面层不再重复提示。 */
  userNotified = false

  constructor(params: {
    httpStatus: number
    code: string
    message: string
    requestId?: string | null
    fieldErrors?: FieldError[]
    retryAfterSeconds?: number | null
  }) {
    super(params.message)
    this.name = 'ApiError'
    this.httpStatus = params.httpStatus
    this.code = params.code
    this.requestId = params.requestId ?? null
    this.fieldErrors = params.fieldErrors ?? []
    this.retryAfterSeconds = params.retryAfterSeconds ?? null
  }

  get isVersionConflict(): boolean {
    return this.code === 'VERSION_CONFLICT'
  }

  get isNetworkError(): boolean {
    return this.httpStatus === 0
  }

  /** field -> message，供表单定位（400 fieldErrors） */
  fieldErrorMap(): Map<string, string> {
    const map = new Map<string, string>()
    for (const fe of this.fieldErrors) {
      if (!map.has(fe.field)) map.set(fe.field, fe.message)
    }
    return map
  }
}
