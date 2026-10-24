/**
 * 全局守卫：
 * - 未登录 → /login；已登录访问 /login → 按会话状态回门户/改密。
 * - 受限会话（首次改密）只允许 /change-password（§10.7）。
 * - 管理路由校验固定权限码，无权限跳 /403（后端始终重新鉴权）。
 * - 登录后会话初始化：并行语义（A03/A06/A40 在守卫补齐，A05 由门户页自取）。
 */
import type { Router } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { useDictionaryStore } from '@/stores/dictionary'
import { ADMIN_ENTRY_ROUTES } from './routes'
import { COURSE_APPLICATION_KEYS } from '@/utils/permissionCatalog'

export function installRouterGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const auth = useAuthStore()
    const permission = usePermissionStore()
    const dictionary = useDictionaryStore()

    if (to.meta.title) {
      document.title = `${to.meta.title} · 市政公司统一权限管理平台`
    }

    // 未登录
    if (!auth.isLoggedIn) {
      return to.meta.requiresAuth ? { path: '/login' } : true
    }

    // 已登录访问欢迎页或登录页：受限去改密，否则回门户
    if (to.path === '/login' || to.path === '/welcome') {
      return auth.restricted ? { path: '/change-password' } : { path: '/portal' }
    }

    // 受限会话只能去改密页
    if (auth.restricted && to.meta.allowRestricted !== true) {
      return { path: '/change-password' }
    }

    // 会话初始化：权限与字典缺失时补拉（登录流程已并行拉取，这里兜底）
    if (!auth.restricted && permission.permissionKeys.length === 0) {
      try {
        await permission.fetch()
      } catch {
        // 拉取失败不阻断导航；页面内接口会再次触发相应错误处理
      }
    }
    if (!auth.restricted && dictionary.dict === null) {
      void dictionary.load()
    }

    if (to.path === '/admin' || to.path === '/admin/') {
      const entry = ADMIN_ENTRY_ROUTES.find((item) => permission.has(item.permission))
      return entry ? { path: entry.path } : { path: '/403' }
    }

    // 应用工作台与管理后台入口都由当前有效权限集合校验。
    if (to.name === 'portal-application' || to.name === 'portal-permission-access') {
      const appKey = String(to.params.appKey ?? '')
      if (appKey === 'platform') return { path: '/admin' }
      if (appKey !== 'engineering' && !COURSE_APPLICATION_KEYS.includes(appKey)) return { path: '/403' }
      if (!permission.permissionKeys.some((key) => key.startsWith(`${appKey}:`))) {
        return { path: '/403' }
      }
      if (to.name === 'portal-permission-access') {
        const permissionKey =
          typeof to.query.permission === 'string' ? to.query.permission : ''
        if (
          !permissionKey.startsWith(`${appKey}:`) ||
          !permission.has(permissionKey)
        ) {
          return { path: '/403' }
        }
      }
    }

    // 管理路由固定权限校验（仅前端显隐；服务端始终重新鉴权）
    const required = to.meta.permission as string | undefined
    if (required && !permission.has(required)) {
      return { path: '/403' }
    }
    const requiredPrefix = to.meta.permissionPrefix as string | undefined
    if (requiredPrefix && !permission.permissionKeys.some((key) => key.startsWith(requiredPrefix))) {
      return { path: '/403' }
    }
    const requiredAny = to.meta.permissionAny as string[] | undefined
    if (requiredAny && !permission.hasAny(requiredAny)) return { path: '/403' }

    return true
  })
}
