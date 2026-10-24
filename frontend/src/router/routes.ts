/**
 * 路由表与应用工作台入口（文档 §10.10）。
 * 红线：应用卡片只可进入通用应用工作台，权限内容来自 A06，不拼接业务系统 URL。
 */
import type { RouteRecordRaw } from 'vue-router'
import { P } from '@/constants/permissions'
import { COURSE_APPLICATION_KEYS } from '@/utils/permissionCatalog'

/** platform 卡片与管理后台默认首页按权限依次选择，不固定落到员工管理。 */
export const ADMIN_ENTRY_ROUTES = [
  { path: '/admin/users', permission: P.user.view },
  { path: '/admin/depts', permission: P.dept.view },
  { path: '/admin/roles', permission: P.role.view },
  { path: '/admin/applications', permission: P.app.view },
  { path: '/admin/permissions', permission: P.permission.view },
  { path: '/admin/logs', permission: P.log.view },
] as const

export function resolveAppRoute(appKey: string, permissionKeys: string[]): string | null {
  if (appKey === 'platform') {
    return ADMIN_ENTRY_ROUTES.find((entry) => permissionKeys.includes(entry.permission))?.path ?? null
  }
  return (appKey === 'engineering' || COURSE_APPLICATION_KEYS.includes(appKey)) && permissionKeys.some((key) => key.startsWith(`${appKey}:`))
    ? `/portal/apps/${encodeURIComponent(appKey)}`
    : null
}

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/welcome' },
  {
    path: '/welcome',
    component: () => import('@/views/auth/Welcome.vue'),
    meta: { title: '欢迎', requiresAuth: false },
  },
  {
    path: '/login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/change-password',
    component: () => import('@/views/auth/ChangePassword.vue'),
    meta: { title: '修改密码', requiresAuth: true, allowRestricted: true },
  },

  {
    path: '/portal',
    component: () => import('@/layouts/PortalLayout.vue'),
    meta: { title: '权限门户', requiresAuth: true },
    children: [
      {
        path: '',
        name: 'portal-home',
        component: () => import('@/views/portal/Home.vue'),
        meta: { title: '权限门户', requiresAuth: true },
      },
      {
        path: 'demo/projects',
        name: 'demo-projects',
        component: () => import('@/views/demo/Projects.vue'),
        meta: {
          title: '工程业务演示',
          requiresAuth: true,
          permissionAny: [P.demo.view, P.demo.submit],
        },
      },
      {
        path: 'apps/:appKey',
        name: 'portal-application',
        component: () => import('@/views/portal/ApplicationHome.vue'),
        meta: { title: '应用首页', requiresAuth: true },
      },
      {
        path: 'apps/:appKey/access',
        name: 'portal-permission-access',
        component: () => import('@/views/portal/PermissionAccess.vue'),
        meta: { title: '访问成功', requiresAuth: true },
      },
    ],
  },

  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { title: '管理后台', requiresAuth: true },
    children: [
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/views/user/List.vue'),
        meta: { title: '员工管理', requiresAuth: true, permission: P.user.view },
      },
      {
        path: 'users/:id',
        name: 'admin-user-detail',
        component: () => import('@/views/user/Detail.vue'),
        meta: { title: '员工详情', requiresAuth: true, permission: P.user.view },
      },
      {
        path: 'depts',
        name: 'admin-depts',
        component: () => import('@/views/dept/Tree.vue'),
        meta: { title: '部门维护', requiresAuth: true, permission: P.dept.view },
      },
      {
        path: 'roles',
        name: 'admin-roles',
        component: () => import('@/views/role/List.vue'),
        meta: { title: '角色管理', requiresAuth: true, permission: P.role.view },
      },
      {
        path: 'roles/:id/permissions',
        name: 'admin-role-permissions',
        component: () => import('@/views/role/PermissionAssign.vue'),
        meta: { title: '角色授权', requiresAuth: true, permission: P.role.grant },
      },
      {
        path: 'applications',
        name: 'admin-applications',
        component: () => import('@/views/application/List.vue'),
        meta: { title: '应用管理', requiresAuth: true, permission: P.app.view },
      },
      {
        path: 'permissions',
        name: 'admin-permissions',
        component: () => import('@/views/permission/List.vue'),
        meta: { title: '权限管理', requiresAuth: true, permission: P.permission.view },
      },
      {
        path: 'logs',
        name: 'admin-logs',
        component: () => import('@/views/log/List.vue'),
        meta: { title: '操作日志', requiresAuth: true, permission: P.log.view },
      },
    ],
  },

  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/Forbidden.vue'),
    meta: { title: '无权访问', requiresAuth: false },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/NotFound.vue'),
    meta: { title: '页面不存在', requiresAuth: false },
  },
]
