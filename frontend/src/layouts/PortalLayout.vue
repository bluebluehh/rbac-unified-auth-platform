<script setup lang="ts">
/**
 * 员工门户布局：顶部品牌栏 + 内容区。
 */
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { usePortalStore } from '@/stores/portal'
import { useDictionaryStore } from '@/stores/dictionary'
import { logout } from '@/api/auth'

const router = useRouter()
const auth = useAuthStore()
const permission = usePermissionStore()
const portal = usePortalStore()
const dictionary = useDictionaryStore()

function goChangePassword(): void {
  void router.push('/change-password')
}

async function onLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定退出当前账号吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await logout()
  } catch {
    /* 服务端撤销失败不阻塞本地清理 */
  }
  auth.clear()
  permission.reset()
  portal.reset()
  dictionary.reset()
  ElMessage.success('已退出登录')
  void router.push('/login')
}
</script>

<template>
  <div class="portal-layout">
    <header class="portal-header">
      <div class="brand">
        <div class="brand-logo">权</div>
        <div class="brand-name">市政公司统一权限管理平台</div>
      </div>
      <div class="header-right">
        <el-dropdown trigger="click">
          <span class="user-chip">
            <el-icon :size="16"><UserFilled /></el-icon>
            {{ auth.user?.realName ?? auth.user?.username ?? '当前用户' }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="goChangePassword">修改密码</el-dropdown-item>
              <el-dropdown-item data-test="logout" @click="onLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <main class="portal-content">
      <RouterView />
    </main>
  </div>
</template>

<style scoped lang="scss">
.portal-layout {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.portal-header {
  height: var(--rbac-header-height);
  flex-shrink: 0;
  background: var(--rbac-card-bg);
  border-bottom: 1px solid var(--rbac-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-logo {
  width: 30px;
  height: 30px;
  border-radius: var(--rbac-radius-sm);
  background: var(--rbac-primary);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--rbac-text-primary);
  letter-spacing: 0.5px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--rbac-text-regular);
  font-size: 14px;
  outline: none;
}

.portal-content {
  flex: 1;
  overflow: auto;
}
</style>
