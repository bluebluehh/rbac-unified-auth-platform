<script setup lang="ts">
/**
 * 用户角色分配弹窗（A13/A14，完整替换语义，文档 §5.3 / §10.7）：
 * - 打开时读 A13 的 roleIds/version，候选角色走 A20（仅 status=1，分页）。
 * - 所有页选中的 ID 保存在 Set 中；翻页/搜索不清除其它页的选择。
 * - 已绑定停用角色单独显示，可保留或取消；候选仅启用角色，停用角色不作为新候选。
 * - 412 保留未提交选择并刷新版本，由用户再次确认，不自动用新版本覆盖。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assignUserRoles, getUserRoles } from '@/api/user'
import { listRoles } from '@/api/role'
import { ApiError } from '@/api/errors'
import { useDictionaryStore } from '@/stores/dictionary'
import { usePermissionStore } from '@/stores/permission'
import { showApiError } from '@/utils/apiMessages'
import type { ID, Page, RoleVO } from '@/types'

const props = defineProps<{
  userId: ID
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved'): void
}>()

const dictionary = useDictionaryStore()
const permission = usePermissionStore()

const loading = ref(false)
const saving = ref(false)
const loadFailed = ref(false)
const version = ref<ID>('0')
const selected = ref<Set<ID>>(new Set())
const originalSelected = ref<Set<ID>>(new Set())
const boundDisabled = ref<RoleVO[]>([])
const candidates = ref<Page<RoleVO>>({ records: [], total: 0, page: 1, size: 100 })
const page = ref(1)
const size = 100
const keyword = ref('')
let candidateSeq = 0
let assignedSeq = 0

async function loadAssigned(): Promise<void> {
  const seq = ++assignedSeq
  const assigned = await getUserRoles(props.userId)
  if (seq !== assignedSeq || !props.visible) return
  version.value = assigned.version
  selected.value = new Set(assigned.roleIds)
  originalSelected.value = new Set(assigned.roleIds)
  boundDisabled.value = assigned.roles.filter((r) => r.status === 0)
}

async function loadCandidates(): Promise<void> {
  const seq = ++candidateSeq
  const result = await listRoles({
    page: page.value,
    size,
    status: 1,
    keyword: keyword.value.trim() || undefined,
  })
  if (seq !== candidateSeq) return
  candidates.value = result
}

async function load(): Promise<void> {
  loading.value = true
  loadFailed.value = false
  try {
    await Promise.all([loadAssigned(), loadCandidates()])
  } catch (e) {
    loadFailed.value = true
    showApiError(e, '角色数据加载失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (open) => {
    if (open) {
      page.value = 1
      keyword.value = ''
      void load()
    }
  },
)

function toggle(id: ID, checked: boolean | string | number): void {
  const next = new Set(selected.value)
  if (checked === true) next.add(id)
  else next.delete(id)
  selected.value = next
}

const allPageSelected = computed(
  () =>
    candidates.value.records.length > 0 &&
    candidates.value.records.every((r) => selected.value.has(r.id)),
)

function togglePage(checked: boolean | string | number): void {
  const next = new Set(selected.value)
  const on = checked === true
  for (const r of candidates.value.records) {
    if (on) next.add(r.id)
    else next.delete(r.id)
  }
  selected.value = next
}

async function onPageChange(p: number): Promise<void> {
  page.value = p
  await loadCandidates()
}

async function onSearch(): Promise<void> {
  page.value = 1
  await loadCandidates()
}

async function submit(): Promise<void> {
  const currentIds = Array.from(selected.value)
  const unchanged =
    currentIds.length === originalSelected.value.size &&
    currentIds.every((id) => originalSelected.value.has(id))

  if (unchanged) {
    ElMessage.info('角色分配没有变化')
    emit('update:visible', false)
    return
  }

  if (currentIds.length === 0 && originalSelected.value.size > 0) {
    try {
      await ElMessageBox.confirm('清空后将撤销该员工的全部角色，确定继续吗？', '清空全部角色', {
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
    await assignUserRoles(props.userId, { roleIds: currentIds }, version.value)
    ElMessage.success('角色分配已保存')
    await permission.fetch().catch(() => undefined)
    emit('saved')
    emit('update:visible', false)
  } catch (e) {
    if (e instanceof ApiError) {
      if (e.code === 'VERSION_CONFLICT') {
        try {
          const assigned = await getUserRoles(props.userId)
          version.value = assigned.version
          originalSelected.value = new Set(assigned.roleIds)
          boundDisabled.value = assigned.roles.filter((r) => r.status === 0)
        } catch {
          /* 版本刷新失败时仍保留当前输入，由用户重试 */
        }
        ElMessage.warning('员工角色已有其他更新，请核对后重新确认保存')
      } else {
        showApiError(e, '角色分配保存失败')
      }
    }
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="分配角色"
    width="640px"
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <div v-loading="loading" class="role-assign">
      <template v-if="boundDisabled.length > 0">
        <p class="section-title">当前不可用角色（可保留或取消）</p>
        <div class="bound-disabled">
          <el-checkbox
            v-for="r in boundDisabled"
            :key="r.id"
            :model-value="selected.has(r.id)"
            @change="toggle(r.id, $event)"
          >
            {{ r.roleName }}（{{ r.roleKey }}）
          </el-checkbox>
        </div>
      </template>

      <el-alert
        v-if="loadFailed"
        title="角色数据加载失败，请关闭后重试"
        type="error"
        show-icon
        :closable="false"
        class="load-error"
      />

      <p class="section-title">可分配角色</p>
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="按角色名称或角色编码搜索"
          clearable
          style="width: 240px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button @click="onSearch">搜索</el-button>
      </div>

      <el-table :data="candidates.records" size="small" class="candidate-table">
        <el-table-column width="46">
          <template #header>
            <el-checkbox
              :model-value="allPageSelected"
              :indeterminate="!allPageSelected && candidates.records.some((r) => selected.has(r.id))"
              @change="togglePage($event)"
            />
          </template>
          <template #default="{ row }">
            <el-checkbox
              :model-value="selected.has((row as RoleVO).id)"
              @change="toggle((row as RoleVO).id, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="roleName" label="角色名称" min-width="160" />
        <el-table-column prop="roleKey" label="角色编码" min-width="180" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="(row as RoleVO).status === 1 ? 'success' : 'info'" size="small">
              {{ dictionary.binaryStatusLabel((row as RoleVO).status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="candidates.total"
        layout="total, prev, pager, next"
        class="pagination"
        @current-change="onPageChange"
      />

      <p class="hint">已选 {{ selected.size }} 个角色；保存后会按当前选择更新员工角色，全部取消将撤销现有角色。</p>
    </div>

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="loading || loadFailed" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.role-assign {
  min-height: 240px;
}

.section-title {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--rbac-text-secondary);
}

.load-error {
  margin-bottom: 12px;
}

.bound-disabled {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  margin-bottom: 16px;
}

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.candidate-table {
  width: 100%;
}

.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}

.hint {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--rbac-text-secondary);
}
</style>
