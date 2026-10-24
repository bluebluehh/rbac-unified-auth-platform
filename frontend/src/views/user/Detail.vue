<script setup lang="ts">
/**
 * 员工详情（A08/A10–A14，文档 §10.7）：
 * - A08 读取当前 VO 与 version，ETag/If-Match 用正文 version 构造。
 * - 字段变化使用 PATCH；phone 默认空且不提交，修改才提交明文或 null（不回填掩码）。
 * - 启停（A11）、重置密码（A12）、角色分配（A13/A14）均按最新 version 提交。
 */
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getUser, patchUser, patchUserStatus, resetUserPassword } from '@/api/user'
import { getDeptTree } from '@/api/dept'
import { ApiError } from '@/api/errors'
import { P } from '@/constants/permissions'
import { useDictionaryStore } from '@/stores/dictionary'
import RoleAssignDialog from '@/components/RoleAssignDialog.vue'
import { companyOptions, subtreeOptions, type DeptOption } from '@/utils/dept'
import { applyFieldErrors } from '@/utils/formErrors'
import { showApiError } from '@/utils/apiMessages'
import { formatDateTime, isFutureTimestamp } from '@/utils/format'
import type { DeptTreeVO, ID, UserPatchDTO, UserStatus, UserVO } from '@/types'

const route = useRoute()
const router = useRouter()
const dictionary = useDictionaryStore()

const userId = route.params.id as ID

const loading = ref(false)
const loadFailed = ref(false)
const saving = ref(false)
const user = ref<UserVO | null>(null)
const deptTree = ref<DeptTreeVO[]>([])
const companyOptionsList = ref<DeptOption[]>([])
const deptOptionsList = ref<DeptOption[]>([])
const phoneTouched = ref(false)
const phoneClearRequested = ref(false)

const formRef = ref<FormInstance>()
const form = reactive({
  realName: '',
  companyId: '' as ID,
  deptId: '' as ID,
  positionName: '',
  phone: '',
})

async function load(): Promise<void> {
  loading.value = true
  loadFailed.value = false
  try {
    user.value = await getUser(userId)
    form.realName = user.value.realName
    form.companyId = user.value.companyId
    form.deptId = user.value.deptId
    form.positionName = user.value.positionName
    form.phone = ''
    phoneTouched.value = false
    phoneClearRequested.value = false
    applyDeptOptions()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'NOT_FOUND') {
      ElMessage.warning('员工不存在或已被删除，已返回员工列表')
      await router.replace('/admin/users')
      return
    }
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function loadDeptTree(): Promise<void> {
  try {
    deptTree.value = await getDeptTree({ includeDisabled: false })
    companyOptionsList.value = companyOptions(deptTree.value)
    applyDeptOptions()
  } catch {
    /* 组织树加载失败不阻断详情展示 */
  }
}

function applyDeptOptions(): void {
  companyOptionsList.value = companyOptions(deptTree.value, true)
  deptOptionsList.value = form.companyId ? subtreeOptions(deptTree.value, form.companyId, true) : []
}

function onCompanyChange(): void {
  form.deptId = ''
  deptOptionsList.value = form.companyId ? subtreeOptions(deptTree.value, form.companyId, true) : []
}

const rules: FormRules = {
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

function buildPatch(): UserPatchDTO | null {
  if (!user.value) return null
  const body: UserPatchDTO = {}
  if (form.realName !== user.value.realName) body.realName = form.realName
  if (form.companyId !== user.value.companyId) body.companyId = form.companyId
  if (form.deptId !== user.value.deptId) body.deptId = form.deptId
  if (form.positionName !== user.value.positionName) body.positionName = form.positionName
  if (phoneClearRequested.value) {
    body.phone = null
  } else if (phoneTouched.value) {
    body.phone = form.phone || null
  }
  return Object.keys(body).length > 0 ? body : null
}

async function onSave(): Promise<void> {
  if (!user.value) return
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  const body = buildPatch()
  if (!body) {
    ElMessage.info('没有需要保存的变更')
    return
  }
  saving.value = true
  try {
    user.value = await patchUser(userId, body, user.value.version)
    form.realName = user.value.realName
    form.companyId = user.value.companyId
    form.deptId = user.value.deptId
    form.positionName = user.value.positionName
    form.phone = ''
    phoneTouched.value = false
    phoneClearRequested.value = false
    applyDeptOptions()
    ElMessage.success('已保存')
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      ElMessage.warning('员工信息已有其他更新，请重新加载并确认')
      void load()
    } else {
      if (e instanceof ApiError) applyFieldErrors(formRef.value, e)
      showApiError(e, '保存失败')
    }
  } finally {
    saving.value = false
  }
}

function statusTarget(u: UserVO): { status: UserStatus; action: string } {
  if (u.status === 'ENABLED') return { status: 'DISABLED', action: '停用' }
  if (u.status === 'PENDING_ACTIVATION') return { status: 'DISABLED', action: '停用' }
  return {
    status: u.mustChangePassword ? 'PENDING_ACTIVATION' : 'ENABLED',
    action: '恢复',
  }
}

async function onStatus(): Promise<void> {
  if (!user.value) return
  const target = statusTarget(user.value)
  try {
    await ElMessageBox.confirm(
      `确定${target.action}员工「${user.value.realName}」吗？`,
      `${target.action}员工`,
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    user.value = await patchUserStatus(userId, { status: target.status }, user.value.version)
    ElMessage.success(`${target.action}成功`)
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      ElMessage.warning('员工信息已有其他更新，详情已刷新，请重新操作')
      void load()
      return
    }
    showApiError(e, '操作失败')
  }
}

const resetVisible = ref(false)
const resetSubmitting = ref(false)
const resetRef = ref<FormInstance>()
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

function openReset(): void {
  resetForm.temporaryPassword = ''
  resetVisible.value = true
}

async function submitReset(): Promise<void> {
  if (!user.value) return
  const valid = await resetRef.value?.validate().catch(() => false)
  if (!valid) return
  resetSubmitting.value = true
  try {
    user.value = await resetUserPassword(userId, { temporaryPassword: resetForm.temporaryPassword }, user.value.version)
    ElMessage.success('密码已重置')
    resetVisible.value = false
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      resetVisible.value = false
      ElMessage.warning('员工信息已有其他更新，详情已刷新，请重新操作')
      void load()
      return
    }
    if (e instanceof ApiError) applyFieldErrors(resetRef.value, e)
    showApiError(e, '重置失败')
  } finally {
    resetSubmitting.value = false
  }
}

const roleAssignVisible = ref(false)

function statusTagType(status: UserStatus): 'success' | 'danger' | 'warning' {
  if (status === 'ENABLED') return 'success'
  if (status === 'DISABLED') return 'danger'
  return 'warning'
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
        <el-button link type="primary" @click="router.push('/admin/users')">← 返回员工列表</el-button>
        <h2 class="page-title">员工详情</h2>
      </div>
      <el-button v-permission="P.user.assignRole" type="primary" @click="roleAssignVisible = true">分配角色</el-button>
    </div>

    <div v-loading="loading" class="card detail-card">
      <el-result v-if="loadFailed" icon="error" title="加载失败" sub-title="无法获取员工详情">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>

      <template v-else-if="user">
        <el-descriptions :column="2" border class="meta">
          <el-descriptions-item label="工号">{{ user.username }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagType(user.status)" size="small">{{ dictionary.userStatusLabel(user.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="电话">{{ user.phoneMasked ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="最近更新">{{ formatDateTime(user.updateTime) }}</el-descriptions-item>
          <el-descriptions-item label="登录限制" :span="2">
            <el-tag v-if="isFutureTimestamp(user.lockedUntil)" type="warning" size="small">
              暂时锁定至 {{ formatDateTime(user.lockedUntil) }}
            </el-tag>
            <span v-else class="text-secondary">正常</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" class="edit-form">
          <el-form-item label="姓名" prop="realName">
            <el-input v-model="form.realName" />
          </el-form-item>
          <el-form-item label="所属公司" prop="companyId">
            <el-select v-model="form.companyId" style="width: 100%" @change="onCompanyChange">
              <el-option v-for="c in companyOptionsList" :key="c.id" :label="c.label" :value="c.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属部门" prop="deptId">
            <el-select v-model="form.deptId" filterable style="width: 100%">
              <el-option v-for="d in deptOptionsList" :key="d.id" :label="d.label" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="岗位" prop="positionName">
            <el-input v-model="form.positionName" />
          </el-form-item>
          <el-form-item label="电话" prop="phone">
            <el-input
              v-model="form.phone"
              placeholder="留空不修改；输入 11 位数字后保存即可更新"
              maxlength="11"
              :disabled="phoneClearRequested"
              @input="phoneTouched = true"
            />
            <el-checkbox
              v-if="user.phoneMasked"
              v-model="phoneClearRequested"
              class="clear-phone"
              @change="phoneTouched = false"
            >
              清除已登记电话
            </el-checkbox>
          </el-form-item>
        </el-form>

        <div class="actions">
          <el-button v-permission="P.user.edit" type="primary" :loading="saving" @click="onSave">保存资料</el-button>
          <el-button v-permission="P.user.resetPassword" type="warning" @click="openReset">重置密码</el-button>
          <el-button v-permission="P.user.status" :type="statusTarget(user).action === '停用' ? 'danger' : 'primary'" @click="onStatus">
            {{ statusTarget(user).action }}
          </el-button>
        </div>
      </template>
    </div>

    <el-dialog v-model="resetVisible" title="重置密码" width="480px" destroy-on-close>
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

    <RoleAssignDialog v-model:visible="roleAssignVisible" :user-id="userId" @saved="load" />
  </div>
</template>

<style scoped lang="scss">
.detail-card {
  max-width: 760px;
}

.meta {
  margin-bottom: 20px;
}

.edit-form {
  max-width: 520px;
}

.clear-phone {
  margin-top: 4px;
}

.actions {
  display: flex;
  gap: 12px;
  margin-top: 8px;
}
</style>
