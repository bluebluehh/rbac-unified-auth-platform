/**
 * 权限 Store：保存 A06 结果，仅用于菜单/按钮显隐；服务端仍每次鉴权（§10.10）。
 * 刷新时机：登录后 / 本人完成角色相关变更后 / 收到 403 后 /
 *           重新获得窗口焦点且距上次读取超过 30 秒。刷新不是 Token 续期。
 */
import { defineStore } from 'pinia'
import { getMyPermissions } from '@/api/auth'
import type { ID } from '@/types'

const REFRESH_INTERVAL_MS = 30_000
let inFlightFetch: Promise<void> | null = null

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    permissionKeys: [] as string[],
    roleIds: [] as ID[],
    policyVersion: null as ID | null,
    lastFetchedAt: 0,
    loading: false,
  }),

  actions: {
    has(key: string): boolean {
      return this.permissionKeys.includes(key)
    },

    hasAny(keys: string[]): boolean {
      return keys.some((k) => this.permissionKeys.includes(k))
    },

    async fetch(): Promise<void> {
      if (inFlightFetch) return inFlightFetch
      this.loading = true
      inFlightFetch = (async () => {
        try {
          const vo = await getMyPermissions()
          this.permissionKeys = vo.permissionKeys
          this.roleIds = vo.roleIds
          this.policyVersion = vo.policyVersion
          this.lastFetchedAt = Date.now()
        } finally {
          this.loading = false
        }
      })()
      try {
        await inFlightFetch
      } finally {
        inFlightFetch = null
      }
    },

    /** 窗口重获焦点时调用；距上次读取超过 30 秒才真正刷新 */
    refreshIfStale(): void {
      if (Date.now() - this.lastFetchedAt > REFRESH_INTERVAL_MS) {
        void this.fetch().catch(() => undefined)
      }
    },

    reset(): void {
      this.permissionKeys = []
      this.roleIds = []
      this.policyVersion = null
      this.lastFetchedAt = 0
    },
  },
})
