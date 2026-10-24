/**
 * 输入 DTO 与查询契约（§10.2 / §10.4）。
 * 可选输入字段才使用 ?；缺省按文档默认值处理。
 */
import type {
  ActionCode,
  AuditResult,
  BinaryStatus,
  EventType,
  ID,
  NodeType,
  PageQuery,
  Timestamp,
  UserStatus,
} from './common'

/* ---------- 认证 ---------- */

export interface LoginDTO {
  username: string
  password: string
}

export interface PasswordChangeDTO {
  currentPassword: string
  newPassword: string
}

export interface PasswordResetDTO {
  temporaryPassword: string
}

/* ---------- 用户 ---------- */

export interface UserCreateDTO {
  username: string
  realName: string
  companyId: ID
  deptId: ID
  positionName: string
  phone?: string | null
}

export interface UserPatchDTO {
  realName?: string
  companyId?: ID
  deptId?: ID
  positionName?: string
  phone?: string | null
}

export interface UserStatusDTO {
  status: UserStatus
}

export interface UserRoleAssignDTO {
  roleIds: ID[]
}

export interface UsersQuery extends PageQuery {
  includeDescendants?: boolean
  companyId?: ID
  grade?: string
  keyword?: string
  deptId?: ID
  status?: UserStatus
}

/* ---------- 部门 ---------- */

export interface DeptCreateDTO {
  deptName: string
  nodeKey: string
  parentId: ID
  nodeType: Exclude<NodeType, 'ROOT'>
  managerUserId?: ID | null
  status?: BinaryStatus
}

export interface DeptPatchDTO {
  deptName?: string
  managerUserId?: ID | null
}

export interface BinaryStatusDTO {
  status: BinaryStatus
}

export interface DeptsTreeQuery {
  rootId?: ID
  includeDisabled?: boolean
}

/* ---------- 角色 ---------- */

export interface RoleCreateDTO {
  roleName: string
  roleKey: string
  description?: string | null
  status?: BinaryStatus
}

export interface RolePatchDTO {
  roleName?: string
  description?: string | null
}

export interface RolePermissionAssignDTO {
  permissionIds: ID[]
}

export interface RolesQuery extends PageQuery {
  keyword?: string
  status?: BinaryStatus
}

/* ---------- 应用 ---------- */

export interface ApplicationCreateDTO {
  appName: string
  appKey: string
  status?: BinaryStatus
}

export interface ApplicationPatchDTO {
  appName?: string
}

export interface ApplicationsQuery extends PageQuery {
  keyword?: string
  status?: BinaryStatus
}

/* ---------- 权限点 ---------- */

export interface PermissionCreateDTO {
  applicationId: ID
  moduleKey: string
  moduleName: string
  resourceKey: string
  resourceName: string
  resourceDescription?: string | null
  actionCode: ActionCode
}

export interface PermissionsQuery extends PageQuery {
  applicationId?: ID
  moduleKey?: string
  keyword?: string
  status?: BinaryStatus
}

/* ---------- 日志 ---------- */

export interface LogsQuery extends PageQuery {
  from: Timestamp
  to: Timestamp
  userId?: ID
  eventType?: EventType
  result?: AuditResult
  requestId?: string
}

/* ---------- 门户 ---------- */

export interface CurrentPermissionsQuery {
  applicationId?: ID
}
