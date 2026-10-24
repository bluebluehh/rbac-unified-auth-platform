/**
 * v-permission 按钮级权限控制：无权限时隐藏元素，并在 A06 权限刷新后同步恢复。
 * 用法：v-permission="'platform:role:role:grant'" 或 v-permission="[k1, k2]"。
 * 仅前端显隐；后端始终重新鉴权。
 */
import { watch, type Directive, type WatchStopHandle } from 'vue'
import { usePermissionStore } from '@/stores/permission'

interface DirectiveState {
  baseDisplay: string
  signature: string
  stop: WatchStopHandle
}

const states = new WeakMap<HTMLElement, DirectiveState>()

function keysOf(binding: { value: string | string[] }): string[] {
  return Array.isArray(binding.value) ? binding.value : [binding.value]
}

function signatureOf(binding: { value: string | string[] }): string {
  return JSON.stringify(keysOf(binding))
}

function bind(el: HTMLElement, binding: { value: string | string[] }): void {
  const permission = usePermissionStore()
  const previous = states.get(el)
  previous?.stop()

  const baseDisplay = previous?.baseDisplay ?? el.style.display
  const keys = keysOf(binding)
  const state: DirectiveState = {
    baseDisplay,
    signature: signatureOf(binding),
    stop: () => undefined,
  }

  el.style.display = permission.hasAny(keys) ? baseDisplay : 'none'
  state.stop = watch(
    () => permission.permissionKeys.slice(),
    () => {
      el.style.display = permission.hasAny(keys) ? baseDisplay : 'none'
    },
    { immediate: true, flush: 'post' },
  )
  states.set(el, state)
}

export const vPermission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    bind(el, binding)
  },
  updated(el, binding) {
    const state = states.get(el)
    if (!state || state.signature !== signatureOf(binding)) {
      bind(el, binding)
    }
  },
  unmounted(el) {
    states.get(el)?.stop()
    states.delete(el)
  },
}
