<script setup lang="ts">
/**
 * 根组件：窗口重获焦点且距上次权限读取超过 30 秒时刷新权限（§10.10）。
 * 该刷新仅更新菜单/按钮显隐，不是 Token 续期。
 */
import { onBeforeUnmount, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'

const auth = useAuthStore()
const permission = usePermissionStore()

function onFocus(): void {
  if (auth.isLoggedIn && !auth.restricted) {
    permission.refreshIfStale()
  }
}

onMounted(() => {
  window.addEventListener('focus', onFocus)
})

onBeforeUnmount(() => {
  window.removeEventListener('focus', onFocus)
})
</script>

<template>
  <RouterView />
</template>
