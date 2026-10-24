<script setup lang="ts">
/**
 * 通用应用工作台：应用与权限均由当前登录身份的 A05/A06 结果决定。
 * 权限按“应用:模块:资源:动作”中的模块分组，点击后进入访问结果页。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Grid, Key } from '@element-plus/icons-vue'
import { useDictionaryStore } from '@/stores/dictionary'
import { usePermissionStore } from '@/stores/permission'
import { usePortalStore } from '@/stores/portal'
import { getApplicationPermissionGroups } from '@/utils/permissionCatalog'
import { P } from '@/constants/permissions'
import { getMyPermissions } from '@/api/auth'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const dictionary = useDictionaryStore()
const permission = usePermissionStore()
const portal = usePortalStore()

const loading = ref(true)
const loadFailed = ref(false)
const detailVisible = ref(false)
const selectedPermissionKey = ref('')
const checking = ref(false)
const permissionSearch = ref('')
const applicationPermissionKeys = ref<string[] | null>(null)
const appKey = computed(() => String(route.params.appKey ?? ''))
const app = computed(() => portal.apps.find((item) => item.appKey === appKey.value))
const groups = computed(() =>
  getApplicationPermissionGroups(appKey.value, applicationPermissionKeys.value?.filter((key) => permission.has(key)) ?? permission.permissionKeys, dictionary.actionLabel),
)
const permissionCount = computed(() =>
  groups.value.reduce((total, group) => total + group.permissions.length, 0),
)
const activeModuleKey = ref('')
const activeGroup = computed(
  () => groups.value.find((group) => group.key === activeModuleKey.value) ?? groups.value[0],
)
const visiblePermissions = computed(() => activeGroup.value?.permissions.filter(item =>
  `${item.resourceName} ${item.actionName} ${item.key}`.toLowerCase().includes(permissionSearch.value.trim().toLowerCase()),
) ?? [])
const selectedPermission = computed(() => groups.value.flatMap(g => g.permissions).find(p => p.key === selectedPermissionKey.value))
const queryEndpoint = computed(() => `/api/v1/auth/permissions?applicationId=${encodeURIComponent(app.value?.id ?? '')}`)
const businessEndpoint = computed(() => {
  if (selectedPermissionKey.value === P.demo.view) return 'GET /api/v1/demo/projects'
  if (selectedPermissionKey.value === P.demo.submit) return 'POST /api/v1/demo/projects/submit'
  return ''
})
const queryCode = computed(() => `// 当前登录员工查询自己的有效权限，不传员工ID\nconst response = await fetch('${queryEndpoint.value}', {\n  headers: { Authorization: 'Bearer <当前登录会话令牌>' }\n});\nif (!response.ok) throw new Error('权限查询失败');\nconst { data } = await response.json();\nconst allowed = data.permissionKeys.includes(${JSON.stringify(selectedPermissionKey.value)});`)
const enforcementCode = computed(() => `// 供业务系统接入时参考：由业务系统后端执行鉴权\n@RequiresPermission(${JSON.stringify(selectedPermissionKey.value)})\n// 在对应业务接口执行前校验当前用户有效权限。\n// 仅在前端检查或隐藏按钮不能替代后端鉴权。`)

async function openPermission(key: string): Promise<void> {
  if (!app.value || checking.value) return
  checking.value = true
  detailVisible.value = false
  try {
    // Always recheck with A06, including when the list was loaded before a revocation.
    applicationPermissionKeys.value = (await getMyPermissions({ applicationId: app.value.id })).permissionKeys
    if (!applicationPermissionKeys.value.includes(key) || !permission.has(key)) {
      ElMessage.warning('该操作权限已失效，请刷新应用权限')
      return
    }
    selectedPermissionKey.value = key
    detailVisible.value = true
  } catch {
    applicationPermissionKeys.value = []
    ElMessage.error('权限查询失败，请重试')
  } finally {
    checking.value = false
  }
}

watch(
  groups,
  (items) => {
    if (!items.some((group) => group.key === activeModuleKey.value)) {
      activeModuleKey.value = items[0]?.key ?? ''
    }
  },
  { immediate: true },
)

async function load(): Promise<void> {
  loading.value = true
  loadFailed.value = false
  applicationPermissionKeys.value = null
  try {
    const tasks: Promise<unknown>[] = []
    if (!portal.loaded) tasks.push(portal.load())
    if (permission.permissionKeys.length === 0) tasks.push(permission.fetch())
    if (dictionary.dict === null) tasks.push(dictionary.load())
    await Promise.all(tasks)
    if (app.value) {
      applicationPermissionKeys.value = (await getMyPermissions({ applicationId: app.value.id })).permissionKeys
    }
  } catch {
    loadFailed.value = true
    applicationPermissionKeys.value = []
  } finally {
    loading.value = false
  }
}

function backToPortal(): void {
  void router.push('/portal')
}

onMounted(() => {
  void load()
})

watch(appKey, () => { void load() })
watch(() => permission.policyVersion, () => { if (!loading.value) void load() })
watch(selectedPermission, (value) => { if (!value) detailVisible.value = false })
</script>

<template>
  <div class="application-page">
    <div v-if="loading && !app" v-loading="true" class="loading-state"></div>

    <el-result
      v-else-if="loadFailed && !app"
      class="page-result"
      icon="error"
      title="应用加载失败"
      sub-title="暂时无法读取当前账号的应用权限，请稍后重试"
    >
      <template #extra>
        <el-button type="primary" @click="load">重新加载</el-button>
      </template>
    </el-result>

    <el-result
      v-else-if="!app"
      class="page-result"
      icon="warning"
      title="应用暂不可用"
      sub-title="当前账号没有该应用的有效权限，或应用已停用"
    >
      <template #extra>
        <el-button type="primary" @click="backToPortal">返回应用门户</el-button>
      </template>
    </el-result>

    <div v-else v-loading="loading" class="application-shell">
      <aside class="module-sidebar">
        <div class="sidebar-brand">
          <div class="brand-logo">{{ app.appName.slice(0, 1) }}</div>
          <div class="brand-copy">
            <strong>{{ app.appName }}</strong>
            <span>应用权限中心</span>
          </div>
        </div>

        <div class="sidebar-label">功能模块</div>
        <nav v-if="groups.length > 0" class="module-menu" aria-label="功能模块">
          <button
            v-for="group in groups"
            :key="group.key"
            class="module-menu-item"
            :class="{ active: group.key === activeGroup?.key }"
            type="button"
            @click="activeModuleKey = group.key"
          >
            <el-icon :size="16"><Grid /></el-icon>
            <span class="module-menu-name">{{ group.name }}</span>
            <span class="module-menu-count">{{ group.permissions.length }}</span>
          </button>
        </nav>
        <div v-else class="sidebar-empty">暂无可用模块</div>

        <div class="sidebar-footer">
          <button class="back-menu-item" type="button" @click="backToPortal">
            <el-icon><ArrowLeft /></el-icon>
            <span>返回应用门户</span>
          </button>
        </div>
      </aside>

      <main class="application-main">
        <header class="application-topbar">
          <div class="topbar-title">
            <span>{{ activeGroup?.name ?? '应用首页' }}</span>
            <small>{{ app.appName }}</small>
          </div>
          <div class="permission-summary">
            <el-icon><Key /></el-icon>
            <span>{{ permissionCount }} 项权限</span>
          </div>
        </header>

        <section class="application-content">
          <el-alert title="我的应用权限" description="点击操作卡片，可查询权限编码、权限查询接口和接入代码。尚未接入的业务功能会在详情中说明。" type="info" :closable="false" show-icon />
          <el-alert v-if="loadFailed" title="权限刷新失败，请重新加载" type="error" :closable="false"><el-button @click="load">重新加载</el-button></el-alert>
          <el-button v-if="appKey === 'engineering' && permission.hasAny([P.demo.view, P.demo.submit])" class="demo-entry" @click="router.push('/portal/demo/projects')">进入合成业务演示</el-button>
          <el-empty
            v-if="!loading && permissionCount === 0"
            description="当前账号在该应用下暂无可用权限"
          />

          <template v-else-if="activeGroup">
            <div class="module-header">
              <div>
                <h1>{{ activeGroup.name }}</h1>
                <p>仅展示当前账号在该模块下拥有的功能权限</p>
              </div>
              <span class="module-count">{{ activeGroup.permissions.length }} 项</span>
            </div>

            <el-input v-model="permissionSearch" placeholder="搜索功能、操作或权限编码" clearable class="permission-search" />
            <div class="permission-grid" :aria-busy="checking">
              <button
                v-for="item in visiblePermissions"
                :key="item.key"
                type="button"
                class="permission-card"
                data-test="permission-item"
                :disabled="checking || loading"
                @click="openPermission(item.key)"
              >
                <span class="permission-action">{{ item.actionName }}</span>
                <span class="permission-name">{{ item.resourceName }}</span>
                <span class="permission-arrow">查看权限 ›</span>
              </button>
            </div>
            <el-empty v-if="visiblePermissions.length === 0" description="没有匹配的权限" />
          </template>
        </section>
      </main>
    </div>
    <el-dialog v-model="detailVisible" title="操作权限与接口" width="min(780px, 94vw)" destroy-on-close>
      <template v-if="selectedPermission">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="应用">{{ app?.appName }}</el-descriptions-item>
          <el-descriptions-item label="模块 / 功能">{{ selectedPermission.moduleName }} / {{ selectedPermission.resourceName }}</el-descriptions-item>
          <el-descriptions-item label="授权操作">{{ selectedPermission.actionName }}（当前账号已授权）</el-descriptions-item>
          <el-descriptions-item label="权限编码"><code>{{ selectedPermission.key }}</code></el-descriptions-item>
          <el-descriptions-item label="权限查询接口"><code>GET {{ queryEndpoint }}</code></el-descriptions-item>
          <el-descriptions-item label="业务接口">{{ businessEndpoint || '尚未接入，当前没有对应的真实业务接口' }}</el-descriptions-item>
        </el-descriptions>
        <h3>权限查询调用代码</h3>
        <pre class="interface-code">{{ queryCode }}</pre>
        <h3>业务系统后端鉴权示例</h3>
        <pre class="interface-code">{{ enforcementCode }}</pre>
        <p>以上示例不包含登录令牌。权限查询接口只返回当前员工权限，不执行审批、导出等业务操作。</p>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.application-page {
  min-height: 100%;
  background: var(--rbac-page-bg);
}

.loading-state {
  min-height: 360px;
}

.page-result {
  min-height: 360px;
}

.application-shell {
  display: flex;
  min-height: calc(100vh - var(--rbac-header-height));
}

.module-sidebar {
  width: var(--rbac-sidebar-width);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  color: var(--rbac-sidebar-text);
  background: var(--rbac-sidebar-bg);
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 18px 20px;
}

.brand-logo {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--rbac-radius-sm);
  color: #fff;
  background: var(--rbac-primary);
  font-size: 16px;
  font-weight: 700;
}

.brand-copy {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;

  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: var(--rbac-sidebar-text-active);
    font-size: 14px;
    font-weight: 600;
  }

  span {
    color: var(--rbac-sidebar-text);
    font-size: 11px;
  }
}

.sidebar-label {
  padding: 10px 20px 6px;
  color: #7f90a3;
  font-size: 11px;
  letter-spacing: 1px;
}

.module-menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 12px 12px;
}

.module-menu-item,
.back-menu-item {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  height: 40px;
  padding: 0 12px;
  color: var(--rbac-sidebar-text);
  font: inherit;
  font-size: 14px;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: var(--rbac-radius-sm);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;

  &:hover {
    color: var(--rbac-sidebar-text-active);
    background: var(--rbac-sidebar-hover);
  }

  &:focus-visible {
    outline: 2px solid #8ab4dd;
    outline-offset: -2px;
  }
}

.module-menu-item {
  gap: 10px;

  &.active {
    color: var(--rbac-sidebar-text-active);
    background: var(--rbac-sidebar-active);
  }
}

.module-menu-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.module-menu-count {
  min-width: 18px;
  color: inherit;
  font-size: 11px;
  text-align: right;
  opacity: 0.68;
}

.sidebar-empty {
  flex: 1;
  padding: 12px 20px;
  color: var(--rbac-sidebar-text);
  font-size: 13px;
}

.sidebar-footer {
  padding: 8px 12px 16px;
}

.back-menu-item {
  gap: 10px;
}

.application-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.application-topbar {
  height: var(--rbac-header-height);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 24px;
  background: var(--rbac-card-bg);
  border-bottom: 1px solid var(--rbac-border-light);
}

.topbar-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;

  span {
    color: var(--rbac-text-primary);
    font-size: 15px;
    font-weight: 600;
  }

  small {
    overflow: hidden;
    color: var(--rbac-text-secondary);
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.permission-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  color: var(--rbac-primary);
  font-size: 13px;
}

.application-content {
  flex: 1;
  padding: 20px 24px 28px;

  > .el-alert { margin-bottom: 20px; }
}

.demo-entry { margin-bottom: 20px; }

.module-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;

  h1 {
    margin: 0;
    color: var(--rbac-text-primary);
    font-size: 18px;
    font-weight: 600;
  }

  p {
    margin: 5px 0 0;
    color: var(--rbac-text-secondary);
    font-size: 12px;
  }
}

.module-count {
  flex-shrink: 0;
  color: var(--rbac-text-secondary);
  font-size: 12px;
}

.permission-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.permission-card {
  display: grid;
  grid-template-columns: 1fr auto;
  grid-template-areas:
    'action arrow'
    'name arrow';
  gap: 5px 12px;
  min-height: 78px;
  padding: 16px 18px;
  color: var(--rbac-text-primary);
  text-align: left;
  background: var(--rbac-card-bg);
  border: 1px solid var(--rbac-border-light);
  border-radius: var(--rbac-radius);
  box-shadow: var(--rbac-shadow-card);
  cursor: pointer;
  font: inherit;
  &:hover, &:focus-visible { border-color: var(--rbac-primary); outline: 2px solid #d8e9fa; }
  &:disabled { cursor: wait; opacity: .65; }
}
.permission-search { max-width: 460px; margin-bottom: 16px; }
.interface-code { padding: 16px; background: #15283e; color: #e3edf7; border-radius: 8px; white-space: pre-wrap; overflow-wrap: anywhere; font: 13px/1.7 Consolas, monospace; }

.permission-action {
  grid-area: action;
  color: var(--rbac-primary);
  font-size: 12px;
  font-weight: 500;
}

.permission-name {
  grid-area: name;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.permission-arrow {
  grid-area: arrow;
  align-self: center;
  color: var(--rbac-text-placeholder);
}

@media (max-width: 760px) {
  .application-shell {
    flex-direction: column;
  }

  .module-sidebar {
    width: 100%;
  }

  .sidebar-brand {
    padding-bottom: 12px;
  }

  .sidebar-label {
    display: none;
  }

  .module-menu {
    flex: none;
    flex-direction: row;
    overflow-x: auto;
    padding-top: 0;
  }

  .module-menu-item {
    width: auto;
    flex-shrink: 0;
  }

  .sidebar-empty {
    flex: none;
  }

  .sidebar-footer {
    display: none;
  }

  .topbar-title small {
    display: none;
  }

  .application-topbar,
  .application-content {
    padding-right: 16px;
    padding-left: 16px;
  }
}
</style>
