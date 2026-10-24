<script setup lang="ts">
/**
 * 管理后台布局：深蓝灰侧栏 + 白色顶栏 + 浅灰内容区。
 * 菜单项按固定权限码过滤（仅前端显隐，服务端仍鉴权）。
 */
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, UserFilled, OfficeBuilding, Avatar, Shop, Key, Document, Grid } from '@element-plus/icons-vue'
import { P } from '@/constants/permissions'
import { useAuthStore } from '@/stores/auth'
import { usePermissionStore } from '@/stores/permission'
import { usePortalStore } from '@/stores/portal'
import { useDictionaryStore } from '@/stores/dictionary'
import { logout } from '@/api/auth'

interface MenuItem {
  path: string
  title: string
  icon: typeof User
  permission: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const permission = usePermissionStore()
const portal = usePortalStore()
const dictionary = useDictionaryStore()

const menus: MenuItem[] = [
  { path: '/admin/users', title: '员工管理', icon: User, permission: P.user.view },
  { path: '/admin/depts', title: '部门维护', icon: OfficeBuilding, permission: P.dept.view },
  { path: '/admin/roles', title: '角色管理', icon: Avatar, permission: P.role.view },
  { path: '/admin/applications', title: '应用管理', icon: Shop, permission: P.app.view },
  { path: '/admin/permissions', title: '权限管理', icon: Key, permission: P.permission.view },
  { path: '/admin/logs', title: '操作日志', icon: Document, permission: P.log.view },
]

const visibleMenus = computed(() => menus.filter((m) => permission.has(m.permission)))
const activePath = computed(() => {
  // 详情页归组到列表菜单
  if (route.path.startsWith('/admin/users/')) return '/admin/users'
  if (route.path.startsWith('/admin/roles/')) return '/admin/roles'
  return route.path
})

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
  // 退出失败也不阻断：前端仍可清本地 Token（§5.7）
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

function goPortal(): void {
  void router.push('/portal')
}

function goChangePassword(): void {
  void router.push('/change-password')
}
</script>

<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="brand" @click="goPortal">
        <div class="brand-logo">权</div>
        <div class="brand-name">统一权限管理平台</div>
      </div>
      <nav class="menu">
        <RouterLink
          v-for="m in visibleMenus"
          :key="m.path"
          :to="m.path"
          class="menu-item"
          :class="{ active: activePath === m.path }"
        >
          <el-icon :size="16"><component :is="m.icon" /></el-icon>
          <span>{{ m.title }}</span>
        </RouterLink>
      </nav>
      <div class="sidebar-footer">
        <RouterLink to="/portal" class="menu-item">
          <el-icon :size="16"><Grid /></el-icon>
          <span>返回门户</span>
        </RouterLink>
      </div>
    </aside>

    <div class="main">
      <header class="header">
        <div class="header-title">{{ route.meta.title }}</div>
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
      <main class="content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.admin-layout {
  display: flex;
  height: 100%;
}

.sidebar {
  width: var(--rbac-sidebar-width);
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--rbac-sidebar-bg);
  color: var(--rbac-sidebar-text);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 20px;
  cursor: pointer;
}

.brand-logo {
  width: 32px;
  height: 32px;
  border-radius: var(--rbac-radius-sm);
  background: var(--rbac-primary);
  color: #fff;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.brand-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--rbac-sidebar-text-active);
  letter-spacing: 0.5px;
}

.menu {
  flex: 1;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 12px;
  border-radius: var(--rbac-radius-sm);
  color: var(--rbac-sidebar-text);
  font-size: 14px;
  transition: background 0.15s, color 0.15s;

  &:hover {
    background: var(--rbac-sidebar-hover);
    color: var(--rbac-sidebar-text-active);
  }

  &.active {
    background: var(--rbac-sidebar-active);
    color: var(--rbac-sidebar-text-active);
  }
}

.sidebar-footer {
  padding: 8px 12px 16px;
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.header {
  height: var(--rbac-header-height);
  flex-shrink: 0;
  background: var(--rbac-card-bg);
  border-bottom: 1px solid var(--rbac-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--rbac-text-primary);
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

.content {
  flex: 1;
  overflow: auto;
}
</style>
