/** 门户应用卡片状态：登录初始化预取，门户失败时仍可单独重试。 */
import { defineStore } from 'pinia'
import { getMyApps } from '@/api/auth'
import type { ApplicationVO } from '@/types'

export const usePortalStore = defineStore('portal', {
  state: () => ({
    apps: [] as ApplicationVO[],
    loaded: false,
    loading: false,
    loadFailed: false,
    lastFetchedAt: 0,
  }),

  actions: {
    async load(): Promise<void> {
      if (this.loading) return
      this.loading = true
      this.loadFailed = false
      try {
        this.apps = await getMyApps()
        this.loaded = true
        this.lastFetchedAt = Date.now()
      } catch (error) {
        this.loadFailed = true
        throw error
      } finally {
        this.loading = false
      }
    },

    reset(): void {
      this.apps = []
      this.loaded = false
      this.loading = false
      this.loadFailed = false
      this.lastFetchedAt = 0
    },
  },
})
