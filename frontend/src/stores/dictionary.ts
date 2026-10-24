/**
 * 字典 Store（A40）：状态/动作/事件的中文标签全部来自后端版本化字典，
 * 前端不另建映射（§10.5）。dictionaryVersion 改变时整体替换。
 */
import { defineStore } from 'pinia'
import { getDictionaries } from '@/api/meta'
import type { AuditResult, BinaryStatus, DictionaryVO, EventType, NodeType, UserStatus } from '@/types'

type LabelOption = { value: string | number; label: string }

/** 模块级工具：按 value 查 label，查不到回退原值 */
function labelFrom(options: LabelOption[] | undefined, value: string | number): string {
  return options?.find((o) => o.value === value)?.label ?? String(value)
}

export const useDictionaryStore = defineStore('dictionary', {
  state: () => ({
    dict: null as DictionaryVO | null,
    loading: false,
  }),

  actions: {
    async load(): Promise<void> {
      if (this.loading) return
      this.loading = true
      try {
        this.dict = await getDictionaries()
      } finally {
        this.loading = false
      }
    },

    reset(): void {
      this.dict = null
    },
  },

  getters: {
    userStatusLabel(): (s: UserStatus) => string {
      return (s) => labelFrom(this.dict?.userStatuses, s)
    },
    binaryStatusLabel(): (v: BinaryStatus) => string {
      return (v) => labelFrom(this.dict?.binaryStatuses, v)
    },
    nodeTypeLabel(): (t: NodeType) => string {
      return (t) => labelFrom(this.dict?.nodeTypes, t)
    },
    auditResultLabel(): (r: AuditResult) => string {
      return (r) => labelFrom(this.dict?.auditResults, r)
    },
    eventTypeLabel(): (t: EventType) => string {
      return (t) => labelFrom(this.dict?.eventTypes, t)
    },
    actionLabel(): (code: string) => string {
      return (code) => this.dict?.actions.find((a) => a.code === code)?.label ?? code
    },
    limits(): DictionaryVO['limits'] | null {
      return this.dict?.limits ?? null
    },
  },
})
