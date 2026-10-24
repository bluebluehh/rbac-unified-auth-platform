<script setup lang="ts">
/**
 * 权限门户首页（A05）：展示当前用户具有有效权限的应用卡片。
 * 当前账号有有效权限的应用统一进入通用应用工作台。
 */
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { usePortalStore } from '@/stores/portal'
import { usePermissionStore } from '@/stores/permission'
import { resolveAppRoute } from '@/router/routes'
import type { ApplicationVO } from '@/types'

const router = useRouter()
const portal = usePortalStore()
const permission = usePermissionStore()
const loading = computed(() => portal.loading)
const loadFailed = computed(() => portal.loadFailed)
const apps = computed(() =>
  portal.apps.filter((app) => app.appKey !== 'platform' || resolveAppRoute(app.appKey, permission.permissionKeys) !== null),
)

/** 卡片配色（蓝灰体系内循环） */
const CARD_TONES = ['#2e5b8c', '#243447', '#3a6fa8', '#52688a']

async function load(): Promise<void> {
  try {
    await portal.load()
  } catch {
    /* 错误状态由 Portal Store 保存，页面显示重试入口 */
  }
}

function enterApp(app: ApplicationVO): void {
  const route = resolveAppRoute(app.appKey, permission.permissionKeys)
  if (route) {
    void router.push(route)
  }
}

onMounted(() => {
  if (!portal.loaded || Date.now() - portal.lastFetchedAt > 1000) void load()
  window.addEventListener('focus', refreshOnFocus)
})

function refreshOnFocus(): void {
  if (Date.now() - portal.lastFetchedAt > 30_000) void load()
}

onBeforeUnmount(() => {
  window.removeEventListener('focus', refreshOnFocus)
})
</script>

<template>
  <div class="portal-home page">
    <div class="welcome">
      <h2 class="welcome-title">应用门户</h2>
      <p class="welcome-sub">以下为当前账号有权限使用的应用</p>
    </div>

    <div v-if="loading" class="app-grid" v-loading="true" element-loading-text="加载中"></div>

    <el-result v-else-if="loadFailed" icon="error" title="加载失败" sub-title="暂时无法加载可用应用，请稍后重试">
      <template #extra>
        <el-button type="primary" @click="load">重试</el-button>
      </template>
    </el-result>

    <el-empty v-else-if="apps.length === 0" description="当前账号暂无可使用的应用" />

    <div v-else class="app-grid">
      <div
        v-for="(app, i) in apps"
        :key="app.id"
        class="app-card"
        :aria-disabled="resolveAppRoute(app.appKey, permission.permissionKeys) === null"
        :data-test="`app-${app.appKey}`"
        role="button"
        tabindex="0"
        @click="enterApp(app)"
        @keyup.enter="enterApp(app)"
      >
        <div class="app-icon" :style="{ background: CARD_TONES[i % CARD_TONES.length] }">
          {{ app.appName.slice(0, 1) }}
        </div>
        <div class="app-info">
          <div class="app-name">{{ app.appName }}</div>
          <el-tag v-if="resolveAppRoute(app.appKey, permission.permissionKeys) === null" size="small" type="info">建设中</el-tag>
        </div>
        <el-icon class="app-arrow"><ArrowRight /></el-icon>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.portal-home {
  max-width: 1080px;
  margin: 0 auto;
}

.welcome {
  margin: 28px 0 24px;
}

.welcome-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
  color: var(--rbac-text-primary);
}

.welcome-sub {
  margin: 0;
  font-size: 13px;
  color: var(--rbac-text-secondary);
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  min-height: 200px;
}

.app-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 22px;
  background: var(--rbac-card-bg);
  border: 1px solid var(--rbac-border-light);
  border-radius: var(--rbac-radius);
  box-shadow: var(--rbac-shadow-card);
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--rbac-shadow-pop);
  }

  &:focus-visible {
    outline: 2px solid var(--rbac-primary);
    outline-offset: 2px;
  }
}

.app-icon {
  width: 46px;
  height: 46px;
  flex-shrink: 0;
  border-radius: 12px;
  color: #fff;
  font-size: 20px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.app-info {
  flex: 1;
  min-width: 0;
}

.app-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--rbac-text-primary);
}

.app-arrow {
  color: var(--rbac-text-placeholder);
}

</style>
