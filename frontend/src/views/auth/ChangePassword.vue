<script setup lang="ts">
/**
 * 首次改密页（A04）：受限会话唯一去处。
 * 成功后服务端撤销全部会话，前端清 Token 并回登录页重新登录（§5.1 / §10.8）。
 */
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { changePassword, logout } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { useDictionaryStore } from '@/stores/dictionary'
import { usePortalStore } from '@/stores/portal'
import { ApiError } from '@/api/errors'
import { applyFieldErrors } from '@/utils/formErrors'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const permission = usePermissionStore()
const dictionary = useDictionaryStore()
const portal = usePortalStore()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const formError = ref('')

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordMin = computed(() => dictionary.limits?.passwordMin ?? 12)
const passwordMax = computed(() => dictionary.limits?.passwordMax ?? 64)
const pageTitle = computed(() => (auth.restricted ? '修改初始密码' : '修改登录密码'))
const pageDescription = computed(() =>
  auth.restricted
    ? '首次登录或密码已被重置，请先设置新密码后进入平台'
    : '修改成功后需要重新登录，其他设备上的当前会话也会失效',
)

const rules: FormRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      validator: (_r, value: string, callback) => {
        if (value.length < passwordMin.value || value.length > passwordMax.value) {
          callback(new Error(`新密码长度为 ${passwordMin.value}–${passwordMax.value} 位`))
        } else if (value === form.currentPassword) {
          callback(new Error('新密码不得与当前密码相同'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r, value: string, callback) => {
        if (value !== form.newPassword) callback(new Error('两次输入的新密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await changePassword({ currentPassword: form.currentPassword, newPassword: form.newPassword })
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    auth.clear()
    permission.reset()
    dictionary.reset()
    portal.reset()
    await router.replace('/login')
  } catch (e) {
    if (e instanceof ApiError) {
      applyFieldErrors(formRef.value, e)
      formError.value = e.message
    } else {
      formError.value = '修改失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}

async function onLogout(): Promise<void> {
  try {
    await logout()
  } catch {
    /* 服务端撤销失败不阻塞本地会话清理 */
  }
  auth.clear()
  permission.reset()
  dictionary.reset()
  portal.reset()
  await router.replace('/login')
}

async function onCancel(): Promise<void> {
  if (auth.restricted) {
    await onLogout()
    return
  }
  await router.push('/portal')
}
</script>

<template>
  <div class="change-page">
    <div class="card change-card">
      <h2 class="title">{{ pageTitle }}</h2>
      <p class="sub">{{ pageDescription }}</p>

      <el-alert
        v-if="formError"
        :title="formError"
        type="error"
        show-icon
        :closable="false"
        data-test="change-error"
        class="error-bar"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
        <el-form-item label="当前密码" prop="currentPassword">
          <el-input v-model="form.currentPassword" type="password" show-password placeholder="请输入当前密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
        </el-form-item>
        <div class="actions">
          <el-button :disabled="submitting" @click="onCancel">
            {{ auth.restricted ? '暂不修改，退出' : '返回门户' }}
          </el-button>
          <el-button type="primary" :loading="submitting" data-test="change-submit" @click="onSubmit">确认修改</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped lang="scss">
.change-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--rbac-page-bg);
  padding: 24px;
}

.change-card {
  width: 420px;
  max-width: 100%;
}

.title {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
  color: var(--rbac-text-primary);
}

.sub {
  margin: 0 0 22px;
  font-size: 13px;
  color: var(--rbac-text-secondary);
}

.error-bar {
  margin-bottom: 16px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 4px;
}
</style>
