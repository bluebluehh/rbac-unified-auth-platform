<script setup lang="ts">
/**
 * 角色权限分配（A25/A26，完整替换，文档 §5.4 / §10.7）：
 * - A25 读已分配 permissionIds 与角色 version；候选按“应用→模块→功能”分组，叶子为 permissionId。
 * - 候选仅启用应用（A27）与启用权限（A32）；已绑定但因权限/应用停用而失效的关联可保留或取消。
 * - 保留角色 PLATFORM_ADMIN 只读（§8.1）；提交所有已选权限的完整集合。
 */
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type ElTree } from 'element-plus'
import { getRole, getRolePermissions, assignRolePermissions } from '@/api/role'
import { listApplications } from '@/api/application'
import { listPermissions } from '@/api/permission'
import { ApiError } from '@/api/errors'
import { useDictionaryStore } from '@/stores/dictionary'
import { usePermissionStore } from '@/stores/permission'
import { showApiError } from '@/utils/apiMessages'
import type { ApplicationVO, ID, PermissionVO, RoleVO } from '@/types'

interface PermTreeNode {
  key: string
  label: string
  children?: PermTreeNode[]
}

const route = useRoute()
const router = useRouter()
const dictionary = useDictionaryStore()
const permission = usePermissionStore()

const roleId = route.params.id as ID

const loading = ref(false)
const loadFailed = ref(false)
const saving = ref(false)
const role = ref<RoleVO | null>(null)
const version = ref<ID>('0')
const treeData = ref<PermTreeNode[]>([])
const treeRef = ref<InstanceType<typeof ElTree>>()
const boundDisabled = ref<PermissionVO[]>([])
const keptDisabled = ref<Set<ID>>(new Set())
const originalIds = ref<Set<ID>>(new Set())

const reserved = computed(
  () =>
    role.value?.roleKey === 'PLATFORM_ADMIN' ||
    (dictionary.dict?.reservedRoleKeys ?? []).includes(role.value?.roleKey ?? ''),
)

async function loadAllPermissions(): Promise<PermissionVO[]> {
  const out: PermissionVO[] = []
  let page = 1
  const size = 100
  for (;;) {
    const result = await listPermissions({ page, size, status: 1 })
    out.push(...result.records)
    if (out.length >= result.total || result.records.length === 0) break
    page += 1
    if (page > 100) break
  }
  return out
}

async function loadAllApplications(): Promise<ApplicationVO[]> {
  const out: ApplicationVO[] = []
  let page = 1
  const size = 100
  for (;;) {
    const result = await listApplications({ page, size })
    out.push(...result.records)
    if (out.length >= result.total || result.records.length === 0) break
    page += 1
    if (page > 100) break
  }
  return out
}

function buildTree(perms: PermissionVO[], apps: ApplicationVO[]): PermTreeNode[] {
  const nodes: PermTreeNode[] = []
  for (const app of apps) {
    const appPerms = perms.filter((p) => p.applicationId === app.id)
    if (appPerms.length === 0) continue

    const moduleMap = new Map<string, Map<string, PermissionVO[]>>()
    for (const p of appPerms) {
      if (!moduleMap.has(p.moduleKey)) moduleMap.set(p.moduleKey, new Map())
      const resMap = moduleMap.get(p.moduleKey)!
      if (!resMap.has(p.resourceKey)) resMap.set(p.resourceKey, [])
      resMap.get(p.resourceKey)!.push(p)
    }

    const moduleNodes: PermTreeNode[] = []
    for (const [moduleKey, resMap] of moduleMap) {
      const resourceNodes: PermTreeNode[] = []
      for (const [resourceKey, list] of resMap) {
        resourceNodes.push({
          key: `res:${app.id}:${moduleKey}:${resourceKey}`,
          label: list[0].resourceName,
          children: list.map((p) => ({ key: p.id, label: p.permName })),
        })
      }
      const first = resMap.values().next().value as PermissionVO[]
      moduleNodes.push({
        key: `mod:${app.id}:${moduleKey}`,
        label: first[0].moduleName,
        children: resourceNodes,
      })
    }

    nodes.push({
      key: `app:${app.id}`,
      label: app.appName,
      children: moduleNodes,
    })
  }
  return nodes
}

async function load(): Promise<void> {
  loading.value = true
  loadFailed.value = false
  try {
    const [roleVO, assigned, apps, perms] = await Promise.all([
      getRole(roleId),
      getRolePermissions(roleId),
      loadAllApplications(),
      loadAllPermissions(),
    ])
    role.value = roleVO
    version.value = assigned.version

    const assignedIds = new Set(assigned.permissionIds)
    const disabled = assigned.permissions.filter(
      (p) => p.status === 0 || apps.some((a) => a.id === p.applicationId && a.status === 0),
    )
    boundDisabled.value = disabled
    keptDisabled.value = new Set(disabled.map((p) => p.id))
    originalIds.value = new Set(assigned.permissionIds)

    const enabledPermIds = new Set(perms.map((p) => p.id))
    const enabledAssigned = [...assignedIds].filter((id) => enabledPermIds.has(id))
    treeData.value = buildTree(
      perms,
      apps.filter((app) => app.status === 1),
    )
    await nextTick()
    treeRef.value?.setCheckedKeys(enabledAssigned)
  } catch (e) {
    if (e instanceof ApiError && e.code === 'NOT_FOUND') {
      ElMessage.warning('角色不存在或已被删除，已返回角色列表')
      await router.replace('/admin/roles')
      return
    }
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function toggleDisabled(id: ID, checked: boolean | string | number): void {
  const next = new Set(keptDisabled.value)
  if (checked === true) next.add(id)
  else next.delete(id)
  keptDisabled.value = next
}

async function submit(): Promise<void> {
  const treeChecked = (treeRef.value?.getCheckedKeys(true) ?? []) as ID[]
  const permissionIds = Array.from(new Set([...treeChecked, ...keptDisabled.value]))
  const unchanged =
    permissionIds.length === originalIds.value.size &&
    permissionIds.every((id) => originalIds.value.has(id))

  if (unchanged) {
    ElMessage.info('权限分配没有变化')
    return
  }

  if (permissionIds.length === 0 && originalIds.value.size > 0) {
    try {
      await ElMessageBox.confirm('清空后将撤销该角色的全部权限，确定继续吗？', '清空全部权限', {
        confirmButtonText: '确认清空',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }
  }

  saving.value = true
  try {
    await assignRolePermissions(roleId, { permissionIds }, version.value)
    ElMessage.success('权限分配已保存')
    await permission.fetch().catch(() => undefined)
    void load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      try {
        const assigned = await getRolePermissions(roleId)
        version.value = assigned.version
        originalIds.value = new Set(assigned.permissionIds)
      ElMessage.warning('角色权限已有其他更新，请核对当前选择后再次保存')
      } catch {
        ElMessage.warning('角色权限已有其他更新；请重新打开页面后再次确认')
      }
    } else {
      showApiError(e, '保存失败')
    }
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <el-button link type="primary" @click="router.push('/admin/roles')">← 返回角色列表</el-button>
        <h2 class="page-title">角色授权 · {{ role?.roleName ?? '加载中' }}</h2>
      </div>
      <el-button type="primary" :loading="saving" :disabled="reserved || loading || loadFailed" @click="submit">保存权限</el-button>
    </div>

    <el-alert
      v-if="reserved"
      title="平台管理员是系统保留角色，不能修改其权限"
      type="warning"
      show-icon
      :closable="false"
      class="reserved-alert"
    />

    <div v-loading="loading" class="card assign-card">
      <el-result v-if="loadFailed" icon="error" title="加载失败" sub-title="无法获取权限数据">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>

      <template v-else>
        <el-tree
          ref="treeRef"
          :data="treeData"
          node-key="key"
          :props="{ label: 'label', children: 'children' }"
          :show-checkbox="!reserved"
          :default-expand-all="false"
          class="perm-tree"
        />

        <div v-if="boundDisabled.length > 0" class="bound-disabled">
          <p class="section-title">历史授权（对应权限或应用已停用，可保留或取消）</p>
          <el-checkbox
            v-for="p in boundDisabled"
            :key="p.id"
            :model-value="keptDisabled.has(p.id)"
            :disabled="reserved"
            @change="toggleDisabled(p.id, $event)"
          >
            {{ p.permName }}
          </el-checkbox>
        </div>

        <p class="hint">勾选表示该角色可以使用对应功能；保存后会按当前选择更新全部权限。</p>
      </template>
    </div>
  </div>
</template>

<style scoped lang="scss">
.reserved-alert {
  margin-bottom: 14px;
}

.assign-card {
  min-height: 360px;
}

.perm-tree {
  max-height: 560px;
  overflow: auto;
}

.bound-disabled {
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--rbac-border-light);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-title {
  margin: 0 0 4px;
  font-size: 13px;
  color: var(--rbac-text-secondary);
}

.hint {
  margin: 14px 0 0;
  font-size: 12px;
  color: var(--rbac-text-secondary);
}
</style>
