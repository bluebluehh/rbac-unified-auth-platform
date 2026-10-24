/**
 * 基础契约：来自设计文档 §10.4 / §9。
 * 红线：ID 与 version 一律为字符串，禁止转 Number 参与比较或算术。
 */

export type ID = string
export type Timestamp = string
export type BinaryStatus = 0 | 1
export type UserStatus = 'ENABLED' | 'DISABLED' | 'PENDING_ACTIVATION'
export type NodeType = 'ROOT' | 'COMPANY' | 'DEPARTMENT' | 'PROJECT_DEPT'
export type AuditResult = 'SUCCESS' | 'FAILURE'
export type TargetType = 'USER' | 'ROLE' | 'DEPT' | 'APP' | 'PERMISSION' | 'SESSION'
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export type ActionCode =
  | 'view'
  | 'create'
  | 'edit'
  | 'export'
  | 'approve'
  | 'review'
  | 'register'
  | 'forward'
  | 'draft'
  | 'archive'
  | 'organize'
  | 'publish'
  | 'score'
  | 'enroll'
  | 'borrow'
  | 'apply'
  | 'check_in'
  | 'handle'
  | 'report'
  | 'dispatch'
  | 'receive'
  | 'feedback'
  | 'accept'
  | 'initiate'
  | 'host'
  | 'push'
  | 'search'
  | 'learn'
  | 'take_exam'
  | 'grade'
  | 'statistics'
  | 'dispose'
  | 'status'
  | 'reset_password'
  | 'assign_role'
  | 'grant'
  | 'submit'

export type EventType =
  | 'LOGIN'
  | 'LOGOUT'
  | 'USER_CREATE'
  | 'USER_UPDATE'
  | 'USER_STATUS'
  | 'PASSWORD_RESET'
  | 'PASSWORD_CHANGE'
  | 'USER_ROLE_ASSIGN'
  | 'ROLE_CREATE'
  | 'ROLE_UPDATE'
  | 'ROLE_STATUS'
  | 'ROLE_PERMISSION_ASSIGN'
  | 'DEPT_CREATE'
  | 'DEPT_UPDATE'
  | 'DEPT_STATUS'
  | 'APP_CREATE'
  | 'APP_UPDATE'
  | 'APP_STATUS'
  | 'PERMISSION_CREATE'
  | 'PERMISSION_STATUS'
  | 'AUTHZ_DENY'
  | 'DEMO_SUBMIT'

export type ErrorCode =
  | 'INVALID_ARGUMENT'
  | 'CURRENT_PASSWORD_INVALID'
  | 'WEAK_PASSWORD'
  | 'LOGIN_FAILED'
  | 'SESSION_INVALID'
  | 'PERMISSION_DENIED'
  | 'PASSWORD_CHANGE_REQUIRED'
  | 'NOT_FOUND'
  | 'USERNAME_EXISTS'
  | 'ROLE_KEY_EXISTS'
  | 'APP_KEY_EXISTS'
  | 'PERMISSION_KEY_EXISTS'
  | 'NODE_KEY_EXISTS'
  | 'LAST_ADMIN_REQUIRED'
  | 'RESERVED_RESOURCE'
  | 'STATE_CONFLICT'
  | 'TRANSACTION_RETRY'
  | 'VERSION_CONFLICT'
  | 'PRECONDITION_REQUIRED'
  | 'RATE_LIMITED'
  | 'LOGIN_LOCKED'
  | 'AUTH_UNAVAILABLE'
  | 'INTERNAL_ERROR'

export interface FieldError {
  field: string
  code: 'REQUIRED' | 'FORMAT' | 'LENGTH' | 'RANGE' | 'DUPLICATE' | 'UNKNOWN_FIELD' | 'REFERENCE_INVALID'
  message: string
}

/** 统一成功响应体（§9.1） */
export interface ApiSuccess<T> {
  code: 'OK'
  message: string
  data: T
  requestId: string
}

/** 统一失败响应体（§9.2 / §10.9） */
export interface ApiErrorBody {
  code: ErrorCode
  message: string
  data: null
  requestId: string
  fieldErrors?: FieldError[]
}

/** 分页响应（§9.3）：total/page/size 为安全整数 */
export interface Page<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface PageQuery {
  page?: number
  size?: number
}
