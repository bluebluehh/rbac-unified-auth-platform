/**
 * 认证 Store：Token 仅内存保存（不落 localStorage/URL/Cookie，§10.10），
 * 页面刷新后重新登录属于安全设计而非缺陷。
 */
import { defineStore } from 'pinia'
import { getMe } from '@/api/auth'
import type { ID, LoginVO, Timestamp, UserVO } from '@/types'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: null as string | null,
    tokenType: 'Bearer' as const,
    expiresAt: null as Timestamp | null,
    /** 受限会话：仅可访问改密/退出（§10.7） */
    restricted: false,
    userId: null as ID | null,
    user: null as UserVO | null,
  }),

  getters: {
    isLoggedIn: (s) => s.token !== null,
  },

  actions: {
    setSession(loginVO: LoginVO): void {
      this.token = loginVO.token
      this.expiresAt = loginVO.expiresAt
      this.restricted = loginVO.restricted
      this.userId = loginVO.userId
    },

    async fetchMe(): Promise<void> {
      this.user = await getMe()
    },

    /** 服务端确认当前会话尚未完成首次改密时，立即切换为受限会话。 */
    markRestricted(): void {
      this.restricted = true
    },

    clear(): void {
      this.token = null
      this.expiresAt = null
      this.restricted = false
      this.userId = null
      this.user = null
    },
  },
})
