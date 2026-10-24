<script setup lang="ts">
/**
 * 欢迎页（平台封面）：单行标题居中 + 登录按钮。
 * 背景与登录页一致；点击按钮后仅按钮淡出并跳转登录页，
 * 标题与英文副标题原地不动，由登录页用相同姿态的元素无缝接力，
 * 完成“折行展开 + 左移 + 缩放”的动画。
 */
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const titleRef = ref<HTMLElement>()
const subRef = ref<HTMLElement>()
const leaving = ref(false)
let enterTimer: number | null = null

/** 量取元素当前位置与字号，供登录页换算封面姿态 */
function measure(el: HTMLElement | undefined) {
  if (!el) return null
  const rect = el.getBoundingClientRect()
  return { x: rect.x, y: rect.y, fontSize: parseFloat(getComputedStyle(el).fontSize) }
}

const goLogin = () => {
  if (leaving.value) return
  leaving.value = true
  const coverTitle = measure(titleRef.value)
  const coverSub = measure(subRef.value)
  // 仅按钮先淡出再跳转；两页共用同一张背景图，衔接无缝
  enterTimer = window.setTimeout(() => {
    router.push({ path: '/login', state: { coverTitle, coverSub } })
  }, 180)
}

onBeforeUnmount(() => {
  if (enterTimer !== null) window.clearTimeout(enterTimer)
})
</script>

<template>
  <div class="welcome-page" :class="{ leaving }">
    <div class="welcome-center">
      <h1 ref="titleRef" class="brand-title">市政公司统一权限管理平台</h1>
      <p ref="subRef" class="brand-sub">Municipal Unified Permission Management Platform</p>
      <button class="login-entry" type="button" @click="goLogin">
        登 录 <span class="login-entry-en">Login</span>
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.welcome-page {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  /* 与登录页同一背景图 */
  background: #e8eef5 url('@/assets/login-bg.png') center / cover no-repeat;
}

.welcome-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 10vh; /* 整体重心略偏上 */
}

/* 封面标题：单行居中，字号/字距与登录页标题成比例，
   以便登录页按 44/54 缩放后可无缝衔接 */
.brand-title {
  position: relative; /* 补偿 letter-spacing 尾部空白，保持视觉居中 */
  left: 4px;
  margin: 0;
  font-family: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei',
    'Noto Sans CJK SC', sans-serif;
  font-size: 54px;
  font-weight: 900;
  line-height: 1.28;
  letter-spacing: 8px;
  white-space: nowrap;
  color: #164080;
  filter: drop-shadow(0 2px 5px rgba(16, 48, 80, 0.3));

  &::after {
    content: '';
    display: block;
    width: 72px;
    height: 4px;
    margin: 14px auto 0;
    border-radius: 2px;
    background: linear-gradient(90deg, rgba(127, 182, 224, 0) 0%, #2e6cb0 30%, #7fb6e0 70%, rgba(127, 182, 224, 0) 100%);
  }
}

.brand-sub {
  margin: 18px 0 0;
  font-size: 13px;
  color: var(--rbac-text-secondary);
  letter-spacing: 3px;
  text-align: center;
}

.login-entry {
  margin-top: 52px;
  min-width: 240px;
  padding: 13px 44px;
  font-size: 17px;
  letter-spacing: 4px;
  color: var(--rbac-primary);
  background: rgba(255, 255, 255, 0.55);
  border: 1.5px solid var(--rbac-primary);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.25s ease;

  .login-entry-en {
    font-size: 14px;
    letter-spacing: 1px;
    opacity: 0.8;
  }

  &:hover {
    background: var(--rbac-primary);
    color: #fff;
    box-shadow: 0 8px 20px rgba(46, 91, 140, 0.3);

    .login-entry-en {
      opacity: 1;
    }
  }
}

.welcome-page.leaving {
  .login-entry {
    opacity: 0;
    pointer-events: none;
  }
}

@media (max-width: 900px) {
  .brand-title {
    font-size: 30px;
    letter-spacing: 4.4px;
    left: 2px;
  }

  .login-entry {
    min-width: 200px;
  }
}
</style>
