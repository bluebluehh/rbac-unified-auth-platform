<script setup lang="ts">
/**
 * 员工管理（A07–A14，文档 §10.7）：
 * - A07 分页/搜索（keyword 匹配工号或姓名，deptId/status 筛选）。
 * - 新增（A09）：初始无密码、无角色、PENDING；公司候选 ROOT/COMPANY，部门取所选公司子树。
 * - 启停（A11）、重置密码（A12）、角色分配（A13/A14 弹窗）均按最新 version 提交。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import ClassificationPanel from '@/components/ClassificationPanel.vue'
import { GRADES, gradeLabel, staffGrade, type HierarchyNode } from '@/utils/staffClassification'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listUsers, createUser, patchUserStatus, resetUserPassword } from '@/api/user'
import { getDeptTree } from '@/api/dept'
import { ApiError } from '@/api/errors'
import { P } from '@/constants/permissions'
import { useDictionaryStore } from '@/stores/dictionary'
import RoleAssignDialog from '@/components/RoleAssignDialog.vue'
import { companyOptions, flattenDeptTree, subtreeOptions, type DeptOption } from '@/utils/dept'
import { applyFieldErrors } from '@/utils/formErrors'
import { showApiError } from '@/utils/apiMessages'
import { formatDateTime, isFutureTimestamp } from '@/utils/format'
import type { DeptTreeVO, ID, Page, UserStatus, UserVO } from '@/types'

const dictionary = useDictionaryStore()

const loading = ref(false)
const loadFailed = ref(false)
const users = ref<Page<UserVO>>({ records: [], total: 0, page: 1, size: 20 })
const query = reactive({ page: 1, size: 20, keyword: '', deptId: '' as ID | '', companyId: '' as ID | '', status: '' as UserStatus | '', grade: '' })
let loadSeq = 0

const deptTree = ref<DeptTreeVO[]>([])
const deptFilterOptions = computed(() => flattenDeptTree(deptTree.value.flatMap(d => d.nodeType === 'ROOT' ? d.children : [d])))
const selectedKey = ref('all')
const selectedCompanyName = ref('')
const treeLoadFailed = ref(false)
const totalCount = (counts: Record<string, number> | undefined): number => Object.values(counts ?? {}).reduce((sum, count) => sum + Number(count), 0)
const hierarchy = computed<HierarchyNode[]>(() => {
  function nodes(items: DeptTreeVO[], parentCompanyId?: string): HierarchyNode[] {
    return items.flatMap(d => {
      // The source's "公司领导" department and the company leader category contain
      // the same employees. Show one category instead of two identical folders.
      const visibleChildren = d.children.filter(child => !(d.nodeType === 'COMPANY'
        && child.deptName === '公司领导' && child.children.length === 0
        && Object.keys(child.directGradeCounts).length === 1
        && Number(child.directGradeCounts.LEADER ?? 0) > 0
        && Number(child.directGradeCounts.LEADER) === Number(d.companyGradeCounts.LEADER)))
      const children = nodes(visibleChildren, d.nodeType === 'COMPANY' ? d.id : parentCompanyId)
      const total = totalCount(d.subtreeGradeCounts)
      if (d.nodeType === 'ROOT') return children
      if (total === 0) return []
      const company = d.nodeType === 'COMPANY'
      const counts = company ? d.companyGradeCounts : d.subtreeGradeCounts
      const grades: HierarchyNode[] = GRADES.filter(g => Number(counts?.[g.value] ?? 0) > 0).map(g => ({
        key: `dept-${d.id}-${g.value}`,
        label: `${company && parentCompanyId && g.value === 'LEADER' ? '分公司领导' : g.label}（${counts[g.value]}人）`,
        deptId: d.id, grade: g.value, companyId: company ? d.id : undefined,
      }))
      return [{ key: `dept-${d.id}`, label: `${d.deptName}（${total}人）`, deptId: d.id, children: [...grades, ...children] }]
    })
  }
  return [{ key: 'all', label: '全部员工', children: nodes(deptTree.value) }]
})
function selectClassification(node: HierarchyNode): void {
  selectedKey.value = node.key
  query.deptId = node.deptId ?? ''
  query.companyId = node.companyId ?? ''
  selectedCompanyName.value = node.companyId ? deptFilterOptions.value.find(d => d.id === node.companyId)?.label.split(' / ').pop() ?? '' : ''
  query.grade = node.grade ?? ''
  onSearch()
}

async function load(): Promise<void> {
  const seq = ++loadSeq
  loading.value = true
  loadFailed.value = false
  try {
    const result = await listUsers({
      page: query.page,
      size: query.size,
      keyword: query.keyword.trim() || undefined,
      deptId: query.deptId || undefined,
      companyId: query.companyId || undefined,
      includeDescendants: true,
      status: query.status || undefined,
      grade: query.grade || undefined,
    })
    if (seq !== loadSeq) return
    users.value = result
  } catch {
    if (seq === loadSeq) loadFailed.value = true
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

async function loadDeptTree(): Promise<void> {
  treeLoadFailed.value = false
  try {
    deptTree.value = await getDeptTree({ includeDisabled: true })
  } catch {
    treeLoadFailed.value = true
  }
}

function onSearch(): void {
  query.page = 1
  void load()
}

function onReset(): void {
  query.keyword = ''
  query.deptId = ''
  query.companyId = ''
  query.grade = ''
  selectedKey.value = 'all'
  query.status = ''
  query.page = 1
  void load()
}

function onPageChange(p: number): void {
  query.page = p
  void load()
}

function onSizeChange(size: number): void {
  query.size = size
  query.page = 1
  void load()
}

/* ---------- 新增员工 ---------- */

const createVisible = ref(false)
const createSubmitting = ref(false)
const createRef = ref<FormInstance>()
const createForm = reactive({
  username: '',
  realName: '',
  companyId: '' as ID,
  deptId: '' as ID,
  positionName: '',
  phone: '',
})

const companyOptionsList = ref<DeptOption[]>([])
const deptOptionsList = ref<DeptOption[]>([])

function refreshCompanyOptions(): void {
  companyOptionsList.value = companyOptions(deptTree.value, true)
}

function onCompanyChange(): void {
  createForm.deptId = ''
  deptOptionsList.value = createForm.companyId
    ? subtreeOptions(deptTree.value, createForm.companyId, true)
    : []
}

function openCreate(): void {
  refreshCompanyOptions()
  createForm.username = ''
  createForm.realName = ''
  createForm.companyId = ''
  createForm.deptId = ''
  createForm.positionName = ''
  createForm.phone = ''
  deptOptionsList.value = []
  createVisible.value = true
}

const createRules: FormRules = {
  username: [
    { required: true, message: '请输入工号', trigger: 'blur' },
    { max: 50, message: '工号不超过 50 个字符', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '工号仅支持字母、数字和下划线', trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 80, message: '姓名不超过 80 个字符', trigger: 'blur' },
  ],
  companyId: [{ required: true, message: '请选择所属公司', trigger: 'change' }],
  deptId: [{ required: true, message: '请选择所属部门', trigger: 'change' }],
  positionName: [
    { required: true, message: '请输入岗位', trigger: 'blur' },
    { max: 80, message: '岗位不超过 80 个字符', trigger: 'blur' },
  ],
  phone: [{ pattern: /^\d{11}$/, message: '电话须为 11 位数字', trigger: 'blur' }],
}

async function submitCreate(): Promise<void> {
  const valid = await createRef.value?.validate().catch(() => false)
  if (!valid) return
  createSubmitting.value = true
  try {
    await createUser({
      username: createForm.username.trim(),
      realName: createForm.realName,
      companyId: createForm.companyId,
      deptId: createForm.deptId,
      positionName: createForm.positionName,
      phone: createForm.phone || null,
    })
    ElMessage.success('员工已创建（初始无密码、无角色，待激活）')
    createVisible.value = false
    onSearch()
    void loadDeptTree()
  } catch (e) {
    if (e instanceof ApiError) applyFieldErrors(createRef.value, e)
    showApiError(e, '创建失败')
  } finally {
    createSubmitting.value = false
  }
}

/* ---------- 启停 / 重置密码 ---------- */

function statusTarget(user: UserVO): { status: UserStatus; action: string } {
  if (user.status === 'ENABLED') return { status: 'DISABLED', action: '停用' }
  if (user.status === 'PENDING_ACTIVATION') return { status: 'DISABLED', action: '停用' }
  return {
    status: user.mustChangePassword ? 'PENDING_ACTIVATION' : 'ENABLED',
    action: '恢复',
  }
}

async function onStatus(user: UserVO): Promise<void> {
  const target = statusTarget(user)
  try {
    await ElMessageBox.confirm(
      `确定${target.action}员工「${user.realName}（${user.username}）」吗？`,
      `${target.action}员工`,
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await patchUserStatus(user.id, { status: target.status }, user.version)
    ElMessage.success(`${target.action}成功`)
    void load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      ElMessage.warning('员工信息已有其他更新，列表已刷新，请重新操作')
      void load()
      return
    }
    showApiError(e, '操作失败')
  }
}

const resetVisible = ref(false)
const resetSubmitting = ref(false)
const resetRef = ref<FormInstance>()
const resetTarget = ref<UserVO | null>(null)
const resetForm = reactive({ temporaryPassword: '' })

const resetRules: FormRules = {
  temporaryPassword: [
    { required: true, message: '请输入临时密码', trigger: 'blur' },
    {
      validator: (_r, value: string, callback) => {
        const min = dictionary.limits?.passwordMin ?? 12
        const max = dictionary.limits?.passwordMax ?? 64
        if (value.length < min || value.length > max) callback(new Error(`密码长度为 ${min}–${max} 位`))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

function openReset(user: UserVO): void {
  resetTarget.value = user
  resetForm.temporaryPassword = ''
  resetVisible.value = true
}

async function submitReset(): Promise<void> {
  const valid = await resetRef.value?.validate().catch(() => false)
  if (!valid || !resetTarget.value) return
  resetSubmitting.value = true
  try {
    await resetUserPassword(resetTarget.value.id, { temporaryPassword: resetForm.temporaryPassword }, resetTarget.value.version)
    ElMessage.success('密码已重置，员工需使用临时密码首次改密')
    resetVisible.value = false
    void load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      resetVisible.value = false
      ElMessage.warning('员工信息已有其他更新，列表已刷新，请重新操作')
      void load()
      return
    }
    if (e instanceof ApiError) applyFieldErrors(resetRef.value, e)
    showApiError(e, '重置失败')
  } finally {
    resetSubmitting.value = false
  }
}

/* ---------- 角色分配 ---------- */

const roleAssignVisible = ref(false)
const roleAssignUserId = ref<ID>('')

function openRoleAssign(user: UserVO): void {
  roleAssignUserId.value = user.id
  roleAssignVisible.value = true
}

function statusTagType(status: UserStatus): 'success' | 'danger' | 'warning' {
  if (status === 'ENABLED') return 'success'
  if (status === 'DISABLED') return 'danger'
  return 'warning'
}

function isTemporarilyLocked(user: UserVO): boolean {
  return isFutureTimestamp(user.lockedUntil)
}

onMounted(() => {
  void load()
  void loadDeptTree()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">员工管理</h2>
        <p class="page-subtitle">维护员工资料、账号状态、登录密码和角色权限</p>
      </div>
      <el-button v-permission="P.user.create" type="primary" @click="openCreate">新增员工</el-button>
    </div>

    <div class="management-layout">
    <ClassificationPanel :nodes="hierarchy" :selected-key="selectedKey" title="部门 / 职级" hint="仅显示实际有人的组织和职级。点击公司包含下属组织；公司下的职级只汇总本公司人员，不含下属分公司。" @select="selectClassification" />
    <section class="management-results">
    <div class="card toolbar">
      <el-input v-model="query.keyword" placeholder="按工号或姓名搜索" clearable style="width: 220px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-select v-model="query.deptId" placeholder="所属部门" clearable filterable style="width: 260px" @change="selectedKey = ''; query.companyId = ''; onSearch()">
        <el-option v-for="d in deptFilterOptions" :key="d.id" :label="d.label" :value="d.id" />
      </el-select>
      <el-select v-model="query.grade" placeholder="全部职级" clearable style="width: 160px" @change="selectedKey = ''; onSearch()">
        <el-option v-for="g in GRADES" :key="g.value" :label="g.label" :value="g.value" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="onSearch">
        <el-option v-for="s in dictionary.dict?.userStatuses ?? []" :key="String(s.value)" :label="s.label" :value="s.value" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <div class="card">
      <el-alert v-if="treeLoadFailed" title="组织分类加载失败" type="warning" :closable="false"><el-button @click="loadDeptTree">重新加载分类</el-button></el-alert>
      <el-alert
        v-if="loadFailed && users.records.length > 0"
        title="列表刷新失败，当前显示的是上一次加载的数据"
        type="warning"
        show-icon
        :closable="false"
        class="load-alert"
      />
      <p class="classification-note">{{ query.companyId ? `当前职级汇总${selectedCompanyName}本公司人员（含本公司各部门，不含下属分公司）。` : query.deptId ? '当前显示所选组织及其下属组织的员工。' : '当前显示全部组织的员工。' }}职级按原始岗位精确分类，不决定员工权限。</p>
      <el-table v-loading="loading" :data="users.records" class="table">
        <el-table-column prop="username" label="工号" min-width="110" />
        <el-table-column prop="realName" label="姓名" min-width="85" />
        <el-table-column label="岗位 / 职级" min-width="145"><template #default="{ row }"><div>{{ (row as UserVO).positionName }}</div><small class="text-secondary">{{ gradeLabel(staffGrade((row as UserVO).positionName)) }}</small></template></el-table-column>
        <el-table-column prop="deptName" label="部门" min-width="130" show-overflow-tooltip />
        <el-table-column prop="phoneMasked" label="电话" min-width="120" />
        <el-table-column label="状态" width="150">
          <template #default="{ row }">
            <el-tag :type="statusTagType((row as UserVO).status)" size="small">
              {{ dictionary.userStatusLabel((row as UserVO).status) }}
            </el-tag>
            <el-tooltip
              v-if="isTemporarilyLocked(row as UserVO)"
              :content="`登录暂时锁定至 ${formatDateTime((row as UserVO).lockedUntil)}`"
            >
              <el-tag type="warning" size="small" class="lock-tag">临时锁定</el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="待修改密码" width="110">
          <template #default="{ row }">
            <el-tag v-if="(row as UserVO).mustChangePassword" type="warning" size="small">是</el-tag>
            <span v-else class="text-secondary">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
            <el-button v-permission="P.user.view" link type="primary" @click="$router.push(`/admin/users/${(row as UserVO).id}`)">详情</el-button>
            <el-button v-permission="P.user.assignRole" link type="primary" @click="openRoleAssign(row as UserVO)">分配角色</el-button>
            <el-button v-permission="P.user.resetPassword" link type="warning" @click="openReset(row as UserVO)">重置密码</el-button>
            <el-button v-permission="P.user.status" link :type="statusTarget(row as UserVO).action === '停用' ? 'danger' : 'primary'" @click="onStatus(row as UserVO)">
              {{ statusTarget(row as UserVO).action }}
            </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="loadFailed" description="加载失败">
            <el-button type="primary" @click="load">重试</el-button>
          </el-empty>
          <el-empty v-else description="暂无员工" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="users.total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[20, 50, 100]"
        class="pagination"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    </section>
    </div>

    <el-dialog v-model="createVisible" title="新增员工" width="560px" destroy-on-close>
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="工号" prop="username">
          <el-input v-model="createForm.username" placeholder="唯一工号（可含前导零）" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="createForm.realName" placeholder="员工姓名" />
        </el-form-item>
        <el-form-item label="所属公司" prop="companyId">
          <el-select v-model="createForm.companyId" placeholder="选择总公司/分公司" style="width: 100%" @change="onCompanyChange">
            <el-option v-for="c in companyOptionsList" :key="c.id" :label="c.label" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属部门" prop="deptId">
          <el-select v-model="createForm.deptId" placeholder="先选公司，再从子树选择" filterable style="width: 100%">
            <el-option v-for="d in deptOptionsList" :key="d.id" :label="d.label" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位" prop="positionName">
          <el-input v-model="createForm.positionName" placeholder="岗位名称" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="createForm.phone" placeholder="11 位数字（可选）" maxlength="11" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="480px" destroy-on-close>
      <p v-if="resetTarget" class="reset-target">
        为「{{ resetTarget.realName }}（{{ resetTarget.username }}）」设置临时密码，员工需使用临时密码完成首次改密。
      </p>
      <el-form ref="resetRef" :model="resetForm" :rules="resetRules" label-width="90px">
        <el-form-item label="临时密码" prop="temporaryPassword">
          <el-input v-model="resetForm.temporaryPassword" type="password" show-password placeholder="12–64 位临时密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetSubmitting" @click="submitReset">确认重置</el-button>
      </template>
    </el-dialog>

    <RoleAssignDialog v-model:visible="roleAssignVisible" :user-id="roleAssignUserId" @saved="load" />
  </div>
</template>

<style scoped lang="scss">
.management-layout { display:flex; gap:18px; align-items:flex-start; }
.management-results { flex:1; min-width:0; }
.row-actions { display:grid; grid-template-columns:1fr 1fr; gap:8px 4px; justify-items:start; :deep(.el-button) { margin:0; } }
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

.reset-target {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--rbac-text-regular);
}

.lock-tag {
  margin-left: 4px;
}
</style>
