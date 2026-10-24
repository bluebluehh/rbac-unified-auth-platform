/**
 * 输出 VO 契约（§10.3 / §10.4）。
 * 所有字段必返回；允许 null 的字段明确标注 | null。
 */
import type {
  ActionCode,
  AuditResult,
  BinaryStatus,
  EventType,
  HttpMethod,
  ID,
  NodeType,
  TargetType,
  Timestamp,
  UserStatus,
} from './common'

export interface AuditDetails {
  changedFields: string[]
  before: Record<string, unknown>
  after: Record<string, unknown>
}

/** 实体公共输出 VO_C */
export interface EntityMeta {
  version: ID
  createTime: Timestamp
  updateTime: Timestamp
}

export interface UserVO extends EntityMeta {
  id: ID
  username: string
  realName: string
  companyId: ID
  deptId: ID
  deptName: string
  positionName: string
  phoneMasked: string | null
  status: UserStatus
  mustChangePassword: boolean
  lockedUntil: Timestamp | null
}

export interface DeptVO extends EntityMeta {
  id: ID
  deptName: string
  nodeKey: string
  parentId: ID | null
  nodeType: NodeType
  managerUserId: ID | null
  status: BinaryStatus
}

export interface DeptTreeVO extends DeptVO {
  directGradeCounts: Record<string, number>
  subtreeGradeCounts: Record<string, number>
  companyGradeCounts: Record<string, number>
  children: DeptTreeVO[]
}

export interface RoleVO extends EntityMeta {
  id: ID
  roleName: string
  roleKey: string
  description: string | null
  status: BinaryStatus
}

export interface ApplicationVO extends EntityMeta {
  id: ID
  appName: string
  appKey: string
  status: BinaryStatus
}

export interface PermissionVO extends EntityMeta {
  id: ID
  applicationId: ID
  moduleKey: string
  moduleName: string
  resourceKey: string
  resourceName: string
  resourceDescription: string | null
  actionCode: ActionCode
  permName: string
  permKey: string
  sourceRow: number | null
  status: BinaryStatus
}

export interface LoginVO {
  token: string
  tokenType: 'Bearer'
  expiresAt: Timestamp
  restricted: boolean
  userId: ID
}

export interface UserRolesVO {
  userId: ID
  roleIds: ID[]
  roles: RoleVO[]
  version: ID
}

export interface RolePermissionsVO {
  roleId: ID
  permissionIds: ID[]
  permissions: PermissionVO[]
  version: ID
}

export interface CurrentPermissionsVO {
  permissionKeys: string[]
  roleIds: ID[]
  policyVersion: ID
}

export interface LogVO {
  id: ID
  userId: ID | null
  usernameSnapshot: string | null
  eventType: EventType
  targetType: TargetType | null
  targetId: string | null
  httpMethod: HttpMethod
  requestPath: string
  requestId: string
  result: AuditResult
  reasonCode: string
  createTime: Timestamp
  details: AuditDetails | null
}

export interface DemoProjectVO {
  id: ID
  name: string
}

export interface DemoActionVO {
  accepted: boolean
  operation: string
}

export interface DeptStatusVO {
  item: DeptVO
  affectedUserCount: number
}

export interface Option<T> {
  value: T
  label: string
}

export interface ActionOption {
  code: ActionCode
  label: string
}

export interface DictionaryVO {
  dictionaryVersion: string
  userStatuses: Option<UserStatus>[]
  binaryStatuses: Option<BinaryStatus>[]
  nodeTypes: Option<NodeType>[]
  auditResults: Option<AuditResult>[]
  eventTypes: Option<EventType>[]
  actions: ActionOption[]
  limits: {
    pageSizeMax: number
    roleSelectionMax: number
    permissionSelectionMax: number
    passwordMin: number
    passwordMax: number
    logRangeDaysMax: number
  }
  reservedRoleKeys: string[]
  reservedAppKeys: string[]
}
