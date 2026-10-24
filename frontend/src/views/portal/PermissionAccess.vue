<script setup lang="ts">
/**
 * 权限访问结果页：验证目标权限仍属于当前账号的有效权限集合。
 * 本页只演示前端权限入口与跳转结果，不调用未定义的后端业务接口。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, CircleCheckFilled, Grid } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useDictionaryStore } from '@/stores/dictionary'
import { usePermissionStore } from '@/stores/permission'
import { usePortalStore } from '@/stores/portal'
import { getPermissionDisplay } from '@/utils/permissionCatalog'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const dictionary = useDictionaryStore()
const permission = usePermissionStore()
const portal = usePortalStore()

const loading = ref(true)
const appKey = computed(() => String(route.params.appKey ?? ''))
const permissionKey = computed(() =>
  typeof route.query.permission === 'string' ? route.query.permission : '',
)
const app = computed(() => portal.apps.find((item) => item.appKey === appKey.value))
const detail = computed(() => getPermissionDisplay(permissionKey.value, dictionary.actionLabel))
const isAllowed = computed(() =>
  permissionKey.value.startsWith(`${appKey.value}:`) && permission.has(permissionKey.value),
)

async function load(): Promise<void> {
  loading.value = true
  try {
    const tasks: Promise<unknown>[] = []
    if (permission.permissionKeys.length === 0) tasks.push(permission.fetch())
    if (!portal.loaded) tasks.push(portal.load())
    if (dictionary.dict === null) tasks.push(dictionary.load())
    await Promise.allSettled(tasks)
  } finally {
    loading.value = false
  }
  if (!isAllowed.value) {
    void router.replace('/403')
  }
}

function backToApplication(): void {
  void router.push({ name: 'portal-application', params: { appKey: appKey.value } })
}

function backToPortal(): void {
  void router.push('/portal')
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="access-page page">
    <div v-loading="loading" class="access-content">
      <template v-if="isAllowed">
        <el-result
          icon="success"
          title="访问成功"
          :sub-title="`${auth.user?.realName ?? auth.user?.username ?? '当前账号'} 已具备“${detail?.displayName ?? permissionKey}”权限`"
        >
          <template #icon>
            <el-icon class="result-icon"><CircleCheckFilled /></el-icon>
          </template>
          <template #extra>
            <el-button type="primary" @click="backToApplication">返回应用首页</el-button>
            <el-button @click="backToPortal">返回应用门户</el-button>
          </template>
        </el-result>

        <div class="access-card">
          <div class="access-card-title">
            <el-icon><Grid /></el-icon>
            本次访问
          </div>
          <dl class="access-details">
            <div>
              <dt>应用</dt>
              <dd>{{ app?.appName ?? appKey }}</dd>
            </div>
            <div>
              <dt>功能模块</dt>
              <dd>{{ detail?.moduleName ?? '—' }}</dd>
            </div>
            <div>
              <dt>功能操作</dt>
              <dd>{{ detail?.displayName ?? permissionKey }}</dd>
            </div>
            <div>
              <dt>权限标识</dt>
              <dd class="permission-key">{{ permissionKey }}</dd>
            </div>
          </dl>
        </div>
      </template>

      <el-empty v-else-if="!loading" description="正在返回权限校验结果">
        <el-button type="primary" :icon="ArrowLeft" @click="backToPortal">返回应用门户</el-button>
      </el-empty>
    </div>
  </div>
</template>

<style scoped lang="scss">
.access-page {
  max-width: 760px;
  margin: 0 auto;
}

.access-content {
  min-height: 360px;
}

.result-icon {
  color: var(--rbac-success);
  font-size: 68px;
}

.access-card {
  margin-top: 8px;
  padding: 20px 22px;
  background: var(--rbac-card-bg);
  border: 1px solid var(--rbac-border-light);
  border-radius: var(--rbac-radius);
  box-shadow: var(--rbac-shadow-card);
}

.access-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  color: var(--rbac-text-primary);
  font-size: 15px;
  font-weight: 600;
}

.access-details {
  margin: 0;

  > div {
    display: grid;
    grid-template-columns: 110px 1fr;
    gap: 16px;
    padding: 10px 0;
    border-bottom: 1px solid var(--rbac-border-light);

    &:last-child {
      border-bottom: 0;
    }
  }

  dt {
    color: var(--rbac-text-secondary);
  }

  dd {
    min-width: 0;
    margin: 0;
    color: var(--rbac-text-primary);
  }
}

.permission-key {
  overflow-wrap: anywhere;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}
</style>
