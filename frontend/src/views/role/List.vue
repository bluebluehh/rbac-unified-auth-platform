<script setup lang="ts">
/**
 * 角色管理（A20–A24，文档 §10.7）：
 * - A20 分页/搜索；roleKey 创建后不可变（A23 仅改名称/描述）。
 * - 保留角色 PLATFORM_ADMIN 只读：按钮禁用 + 后端双重校验（§8.1）。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import ClassificationPanel from '@/components/ClassificationPanel.vue'
import { gradeLabel, roleClassification, type HierarchyNode } from '@/utils/staffClassification'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listRoles, createRole, patchRole, patchRoleStatus } from '@/api/role'
import { ApiError } from '@/api/errors'
import { P } from '@/constants/permissions'
import { useDictionaryStore } from '@/stores/dictionary'
import { usePermissionStore } from '@/stores/permission'
import { applyFieldErrors } from '@/utils/formErrors'
import { showApiError } from '@/utils/apiMessages'
import type { Page, RoleVO } from '@/types'

const router = useRouter()
const dictionary = useDictionaryStore()
const permission = usePermissionStore()

const loading = ref(false)
const loadFailed = ref(false)
const roles = ref<Page<RoleVO>>({ records: [], total: 0, page: 1, size: 20 })
const query = reactive({ page: 1, size: 20, keyword: '', status: '' as 0 | 1 | '' })
let loadSeq = 0
const selectedKey = ref('all')
const department = ref('')
const grade = ref('')
const hierarchy = computed<HierarchyNode[]>(() => {
  const depts = new Map<string, Set<string>>()
  for (const r of roles.value.records) {
    const c = roleClassification(r.roleKey)
    for (const d of c.departments) {
      if (!depts.has(d)) depts.set(d, new Set())
      depts.get(d)!.add(c.grade)
    }
  }
  return [{ key: 'all', label: '全部角色', children: [...depts].map(([d, gs]) => ({
    key: d, label: d, department: d, children: [...gs].map(g => ({key: `${d}-${g}`, label: gradeLabel(g), department: d, grade: g})),
  })) }]
})
const filteredRoles = computed(() => roles.value.records.filter(r => {
  const c = roleClassification(r.roleKey)
  return (!department.value || c.departments.includes(department.value)) && (!grade.value || c.grade === grade.value)
}))
const visibleRoles = computed(() => filteredRoles.value.slice((query.page - 1) * query.size, query.page * query.size))
function selectClassification(node: HierarchyNode): void {
  selectedKey.value = node.key
  department.value = node.department ?? ''
  grade.value = node.grade ?? ''
  query.page = 1
}

async function load(): Promise<void> {
  const seq = ++loadSeq
  loading.value = true
  loadFailed.value = false
  try {
    // Fetch the complete role result before classifying; never filter a single server page.
    const records: RoleVO[] = []
    let page = 1
    let total = 0
    do {
      const result = await listRoles({ page, size: 100, keyword: query.keyword.trim() || undefined,
        status: query.status === '' ? undefined : query.status })
      records.push(...result.records)
      total = result.total
      if (result.records.length === 0) break
      page++
    } while (records.length < total)
    const result = { records, total, page: 1, size: 100 }
    if (seq !== loadSeq) return
    roles.value = result
  } catch {
    if (seq === loadSeq) loadFailed.value = true
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function onSearch(): void {
  query.page = 1
  void load()
}

function onReset(): void {
  query.keyword = ''
  department.value = ''
  grade.value = ''
  selectedKey.value = 'all'
  query.status = ''
  query.page = 1
  void load()
}

function onPageChange(p: number): void {
  query.page = p
}

function onSizeChange(size: number): void {
  query.size = size
  query.page = 1
}

function isReserved(role: RoleVO): boolean {
  return role.roleKey === 'PLATFORM_ADMIN' || (dictionary.dict?.reservedRoleKeys ?? []).includes(role.roleKey)
}

/* ---------- 新增 / 编辑 ---------- */

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref<RoleVO | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  roleName: '',
  roleKey: '',
  description: '',
  status: 1 as 0 | 1,
})

const rules: FormRules = {
  roleName: [
    { required: true, message: '请输入角色名称', trigger: 'blur' },
    { max: 80, message: '角色名称不超过 80 个字符', trigger: 'blur' },
  ],
  roleKey: [
    { required: true, message: '请输入角色编码', trigger: 'blur' },
    { max: 50, message: '角色编码不超过 50 个字符', trigger: 'blur' },
    { pattern: /^[A-Z][A-Z0-9_]*$/, message: '编码须以大写字母开头，仅含大写字母/数字/下划线', trigger: 'blur' },
  ],
}

function openCreate(): void {
  editing.value = null
  form.roleName = ''
  form.roleKey = ''
  form.description = ''
  form.status = 1
  dialogVisible.value = true
}

function openEdit(role: RoleVO): void {
  editing.value = role
  form.roleName = role.roleName
  form.roleKey = role.roleKey
  form.description = role.description ?? ''
  form.status = role.status
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      await patchRole(
        editing.value.id,
        { roleName: form.roleName, description: form.description || null },
        editing.value.version,
      )
      ElMessage.success('角色已更新')
    } else {
      await createRole({
        roleName: form.roleName,
        roleKey: form.roleKey,
        description: form.description || null,
        status: form.status,
      })
      ElMessage.success('角色已创建（初始无权限）')
    }
    dialogVisible.value = false
    void load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      dialogVisible.value = false
      ElMessage.warning('角色信息已有其他更新，列表已刷新，请重新操作')
      void load()
      return
    }
    if (e instanceof ApiError) applyFieldErrors(formRef.value, e)
    showApiError(e, '保存失败')
  } finally {
    submitting.value = false
  }
}

/* ---------- 启停 ---------- */

async function onStatus(role: RoleVO): Promise<void> {
  const action = role.status === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定${action}角色「${role.roleName}」吗？${role.status === 1 ? '停用后保留关系但不产生有效权限。' : ''}`,
      `${action}角色`,
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await patchRoleStatus(role.id, { status: role.status === 1 ? 0 : 1 }, role.version)
    ElMessage.success(`${action}成功`)
    await permission.fetch().catch(() => undefined)
    void load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      ElMessage.warning('角色信息已有其他更新，列表已刷新，请重新操作')
      void load()
      return
    }
    showApiError(e, '操作失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">角色管理</h2>
        <p class="page-subtitle">维护岗位角色及其应用功能权限</p>
      </div>
      <el-button v-permission="P.role.create" type="primary" @click="openCreate">新增角色</el-button>
    </div>

    <div class="management-layout">
    <ClassificationPanel :nodes="hierarchy" :selected-key="selectedKey" title="部门职责 / 职级" @select="selectClassification" />
    <section class="management-results">
    <div class="card toolbar">
      <el-input v-model="query.keyword" placeholder="按角色名称或角色编码搜索" clearable style="width: 240px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="onSearch">
        <el-option v-for="s in dictionary.dict?.binaryStatuses ?? []" :key="String(s.value)" :label="s.label" :value="s.value" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <div class="card">
      <el-alert
        v-if="loadFailed && roles.records.length > 0"
        title="列表刷新失败，当前显示的是上一次加载的数据"
        type="warning"
        show-icon
        :closable="false"
        class="load-alert"
      />
      <p class="classification-note">{{ department || '全部部门职责' }} / {{ grade ? gradeLabel(grade) : '全部职级' }}。分类用于查找，不表示权限继承；新增自定义角色暂归“其他角色”。</p>
      <el-table v-loading="loading" :data="visibleRoles" class="table">
        <el-table-column prop="roleName" label="角色名称" min-width="150" />
        <el-table-column prop="roleKey" label="角色编码" min-width="180">
          <template #default="{ row }">
            <span>{{ (row as RoleVO).roleKey }}</span>
            <el-tag v-if="isReserved(row as RoleVO)" type="danger" size="small" class="reserved-tag">系统角色</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="部门职责" min-width="130"><template #default="{ row }">{{ roleClassification((row as RoleVO).roleKey).departments.join('、') }}</template></el-table-column>
        <el-table-column label="职级" min-width="110"><template #default="{ row }">{{ gradeLabel(roleClassification((row as RoleVO).roleKey).grade) }}</template></el-table-column>
        <el-table-column label="适用原始岗位" min-width="160" show-overflow-tooltip><template #default="{ row }">{{ roleClassification((row as RoleVO).roleKey).positions.join('、') || '—' }}</template></el-table-column>
        <el-table-column prop="description" label="角色说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="(row as RoleVO).status === 1 ? 'success' : 'info'" size="small">
              {{ dictionary.binaryStatusLabel((row as RoleVO).status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="P.role.grant" link type="primary" @click="router.push(`/admin/roles/${(row as RoleVO).id}/permissions`)">分配权限</el-button>
            <el-button v-permission="P.role.edit" link type="primary" :disabled="isReserved(row as RoleVO)" @click="openEdit(row as RoleVO)">编辑</el-button>
            <el-button v-permission="P.role.status" link :type="(row as RoleVO).status === 1 ? 'danger' : 'primary'" :disabled="isReserved(row as RoleVO)" @click="onStatus(row as RoleVO)">
              {{ (row as RoleVO).status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="loadFailed" description="加载失败">
            <el-button type="primary" @click="load">重试</el-button>
          </el-empty>
          <el-empty v-else description="暂无角色" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="filteredRoles.length"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[20, 50, 100]"
        class="pagination"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑角色' : '新增角色'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleKey">
          <el-input v-model="form.roleKey" :disabled="!!editing" placeholder="大写字母开头，可包含数字和下划线" />
        </el-form-item>
        <el-form-item label="角色说明" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="300" show-word-limit />
        </el-form-item>
        <el-form-item v-if="!editing" label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.management-layout { display:flex; gap:18px; align-items:flex-start; }
.management-results { flex:1; min-width:0; }
.classification-note { color:var(--rbac-text-secondary); font-size:12px; margin:0 0 12px; }
@media(max-width:900px) { .management-layout { flex-direction:column; } .management-results { width:100%; } }

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.table {
  width: 100%;
}

.load-alert {
  margin-bottom: 12px;
}

.pagination {
  margin-top: 14px;
  justify-content: flex-end;
}

.reserved-tag {
  margin-left: 8px;
}
</style>
