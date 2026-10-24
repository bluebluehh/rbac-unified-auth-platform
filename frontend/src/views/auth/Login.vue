<script setup lang="ts">
/**
 * 登录页（A01）：工号 + 密码。
 * - LOGIN_FAILED / LOGIN_LOCKED / RATE_LIMITED 等只在本页展示表单错误，不做全局提示（§10.6）。
 * - restricted=true 直接进入改密页，不发起门户初始化请求（§10.7）。
 */
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { useDictionaryStore } from '@/stores/dictionary'
import { usePortalStore } from '@/stores/portal'
import { ApiError } from '@/api/errors'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const permission = usePermissionStore()
const dictionary = useDictionaryStore()
const portal = usePortalStore()

/* ================= 登录表单 ================= */

const formRef = ref<FormInstance>()
const submitting = ref(false)
const formError = ref(route.query.reason === 'session-expired' ? '登录已失效，请重新登录' : '')

const form = reactive({
  username: '',
  password: '',
})

/* 欢迎页 → 登录页：标题折行展开、左移和缩放在一次过渡中完成。 */
interface CoverBox {
  x: number
  y: number
  fontSize: number
}

interface CoverState {
  coverTitle?: CoverBox | null
  coverSub?: CoverBox | null
}

const bootState = window.history.state as CoverState | null
const pendingCover: CoverBox | null = bootState?.coverTitle ?? null
const pendingSub: CoverBox | null = bootState?.coverSub ?? null

const titleRef = ref<HTMLElement>()
const line1Ref = ref<HTMLElement>()
const subRef = ref<HTMLElement>()
const contentShown = ref(!pendingCover)
const animating = ref(!!pendingCover)
const animStyle = ref<Record<string, string>>(pendingCover ? { visibility: 'hidden' } : {})
const foldStyle = ref<Record<string, string>>({})
const subStyle = ref<Record<string, string>>(pendingCover ? { visibility: 'hidden' } : {})
const enterTimers: number[] = []
const enterFrames: number[] = []

function settleEnter(): void {
  contentShown.value = true
  animating.value = false
  animStyle.value = {}
  foldStyle.value = {}
  subStyle.value = {}
}

function coverPose(el: HTMLElement, cover: CoverBox): Record<string, string> {
  const natural = el.getBoundingClientRect()
  const baseFont = parseFloat(getComputedStyle(el).fontSize)
  const scale = cover.fontSize / baseFont
  return {
    visibility: 'visible',
    transformOrigin: 'top left',
    willChange: 'transform',
    transition: 'none',
    transform: `translate(${cover.x - natural.x}px, ${cover.y - natural.y}px) scale(${scale})`,
  }
}

function playEnterAnimation(cover: CoverBox, sub: CoverBox | null): void {
  const el = titleRef.value
  const line1 = line1Ref.value
  if (!el || !line1) return

  const lineHeight = parseFloat(getComputedStyle(el).lineHeight)
  foldStyle.value = {
    willChange: 'transform',
    transition: 'none',
    transform: `translate(${line1.offsetWidth}px, ${-lineHeight}px)`,
  }
  animStyle.value = coverPose(el, cover)

  const subEl = subRef.value
  const subAnimated = !!(sub && subEl)
  if (sub && subEl) subStyle.value = coverPose(subEl, sub)

  enterFrames.push(requestAnimationFrame(() => {
    enterFrames.push(requestAnimationFrame(() => {
      const ease = 'transform 0.7s cubic-bezier(0.22, 0.61, 0.36, 1)'
      enterTimers.push(window.setTimeout(() => {
        foldStyle.value = { ...foldStyle.value, transition: ease, transform: 'translate(0px, 0px)' }
        animStyle.value = { ...animStyle.value, transition: ease, transform: 'translate(0px, 0px) scale(1)' }
        if (subAnimated) {
          subStyle.value = { ...subStyle.value, transition: ease, transform: 'translate(0px, 0px) scale(1)' }
        }
      }, 100))
      enterTimers.push(window.setTimeout(() => {
        contentShown.value = true
        if (!subAnimated) subStyle.value = {}
      }, 850))
      enterTimers.push(window.setTimeout(() => {
        animStyle.value = {}
        foldStyle.value = {}
        subStyle.value = {}
        animating.value = false
      }, 1250))
    }))
  }))
}

onMounted(() => {
  if (!pendingCover) return
  const state = window.history.state as CoverState | null
  if (state) window.history.replaceState({ ...state, coverTitle: null, coverSub: null }, '')
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    settleEnter()
    return
  }
  nextTick(() => {
    if (titleRef.value && line1Ref.value) playEnterAnimation(pendingCover, pendingSub)
    else settleEnter()
  })
})

onBeforeUnmount(() => {
  enterFrames.forEach((frame) => window.cancelAnimationFrame(frame))
  enterTimers.forEach((timer) => window.clearTimeout(timer))
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入工号', trigger: 'blur' },
    { max: 50, message: '工号不超过 50 个字符', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]+$/, message: '工号仅支持字母、数字和下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 12, max: 64, message: '密码长度为 12–64 位', trigger: 'blur' },
  ],
}

async function onSubmit(): Promise<void> {
  formError.value = ''
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const vo = await login({ username: form.username.trim(), password: form.password })
    auth.setSession(vo)

    if (vo.restricted) {
      // 受限会话：直接进入改密页，不调用 A03/A05/A06/A40
      await router.replace('/change-password')
      return
    }

    // 正常登录：按 §10.7 并行拉取 A03/A05/A06/A40
    await Promise.allSettled([auth.fetchMe(), portal.load(), permission.fetch(), dictionary.load()])
    ElMessage.success('登录成功')
    await router.replace('/portal')
  } catch (e) {
    if (e instanceof ApiError) {
      formError.value = e.message
    } else {
      formError.value = '登录失败，请稍后重试'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="page-inner">
      <div class="login-hero">
        <h1
          ref="titleRef"
          class="brand-title"
          :class="{ 'is-animating': animating }"
          :style="animStyle"
        >
          <span ref="line1Ref" class="brand-title-line">市政公司</span><span class="brand-title-line" :style="foldStyle">统一权限管理平台</span>
        </h1>
        <p ref="subRef" class="brand-sub" :style="subStyle">Municipal Unified Permission Management Platform</p>
      </div>

      <div class="login-card" :class="{ 'is-hidden': !contentShown }">
        <h2 class="form-title">员工登录</h2>
        <el-alert
          v-if="formError"
          :title="formError"
          type="error"
          show-icon
          :closable="false"
          data-test="login-error"
          class="form-error"
        />
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @keyup.enter="onSubmit">
          <el-form-item label="工号" prop="username">
            <el-input v-model="form.username" placeholder="请输入工号" :prefix-icon="User" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="submit-btn"
            :loading="submitting"
            data-test="login-submit"
            @click="onSubmit"
          >
            登 录
          </el-button>
        </el-form>
        <p class="form-tip">首次登录或密码重置后需先修改密码</p>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login-page {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  padding: 24px;
  /* 参考图背景：淡蓝城市线稿（已去水印），渐变色作加载兜底 */
  background: #e8eef5 url('@/assets/login-bg.png') center / cover no-repeat;
}

/* 页面内容容器：左侧标题组 + 右侧登录卡片 */
.page-inner {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 1040px; /* 收窄容器，整体左移 */
  display: flex;
  align-items: flex-start; /* 标题组与卡片顶部对齐，整体偏上 */
  gap: 40px;
  margin-bottom: 8vh; /* 内容重心略偏上 */
}

/* 左侧标题组：左对齐，位置略低于卡片顶部 */
.login-hero {
  flex: 1.2;
  min-width: 0;
  padding: 96px 0 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 18px;
}

.login-card {
  position: relative;
  z-index: 1;
  width: 460px;
  flex-shrink: 0;
  border-radius: 14px;
  /* 右侧白色登录卡片：轻悬浮 */
  background: #fff;
  border: 1px solid rgba(46, 91, 140, 0.08);
  box-shadow:
    0 2px 8px rgba(31, 45, 61, 0.05),
    0 14px 36px rgba(31, 45, 61, 0.13);
  padding: 48px 44px;
  transition:
    opacity 0.35s ease,
    transform 0.35s ease;
}

.brand-title {
  position: relative;
  z-index: 1;
  margin: 0;
  /* 标题专用字体栈：苹方/雅黑粗体，字形更挺括 */
  font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei',
    'Noto Sans CJK SC', sans-serif;
  font-size: 44px;
  font-weight: 900;
  line-height: 1.28;
  letter-spacing: 6.5px;
  display: block;
  white-space: nowrap; /* 每行不折行 */
  /* 纯色深海军蓝 */
  color: #164080;
  /* 轻投影增强标题层次 */
  filter: drop-shadow(0 2px 5px rgba(16, 48, 80, 0.3));
}

.brand-title.is-animating {
  filter: none;
}

.brand-title.is-animating::after {
  display: none;
}

.brand-title-line {
  display: block; /* 第二行独占一行 */
}

.brand-title-line:first-child {
  display: inline-block;
}

.login-card.is-hidden {
  opacity: 0;
  transform: translateY(10px);
  pointer-events: none;
}

/* 标题下装饰短横线（渐变收尾），增强艺术字样式的完整感 */
.brand-title::after {
  content: '';
  width: 64px;
  height: 4px;
  margin-top: 12px;
  border-radius: 2px;
  background: linear-gradient(90deg, #2e6cb0 0%, #7fb6e0 55%, rgba(127, 182, 224, 0) 100%);
}

.brand-sub {
  margin: 0;
  font-size: 10px;
  color: var(--rbac-text-secondary);
  letter-spacing: 2.3px;
}

.form-title {
  margin: 0 0 20px;
  font-size: 20px;
  font-weight: 600;
  color: var(--rbac-text-primary);
}

.form-error {
  margin-bottom: 16px;
}

.submit-btn {
  width: 100%;
  margin-top: 6px;
  letter-spacing: 6px;
}

.form-tip {
  margin: 18px 0 0;
  font-size: 11px;
  color: var(--rbac-text-regular); /* 深灰蓝灰，弱于正文 */
  text-align: center;
}

/* 窄窗口：上下堆叠，标题组置顶居左，卡片占满；允许滚动防裁切
   断点取 1120px，确保并排时左栏始终能容纳标题 */
@media (max-width: 1120px) {
  .login-page {
    overflow-y: auto;
    align-items: flex-start;
  }

  .page-inner {
    flex-direction: column;
    align-items: stretch;
    gap: 24px;
    max-width: 460px;
    margin: 48px auto;
  }

  .login-hero {
    padding: 24px 0 0;
    align-items: flex-start;
  }

  .login-card {
    width: 100%;
  }
}

/* 窄窗口标题字号 */
@media (max-width: 900px) {
  .brand-title {
    font-size: 30px;
    letter-spacing: 4.4px;
  }
}
</style>
