/** A07–A14：用户与角色分配 */
import { httpGet, httpPatch, httpPost, httpPut } from './request'
import type { ID, Page, UserCreateDTO, UserPatchDTO, UserRoleAssignDTO, UserRolesVO, UserStatusDTO, UserVO, UsersQuery } from '@/types'

/** A07 分页查询员工 */
export function listUsers(query: UsersQuery): Promise<Page<UserVO>> {
  return httpGet('/users', query)
}

/** A08 员工详情（返回 ETag，正文 version 与之一致） */
export function getUser(id: ID): Promise<UserVO> {
  return httpGet(`/users/${id}`)
}

/** A09 新增员工（201）：初始无密码、无角色、PENDING */
export function createUser(body: UserCreateDTO): Promise<UserVO> {
  return httpPost('/users', body)
}

/** A10 编辑员工 */
export function patchUser(id: ID, body: UserPatchDTO, version: ID): Promise<UserVO> {
  return httpPatch(`/users/${id}`, body, version)
}

/** A11 启停/恢复员工状态 */
export function patchUserStatus(id: ID, body: UserStatusDTO, version: ID): Promise<UserVO> {
  return httpPatch(`/users/${id}/status`, body, version)
}

/** A12 管理员重置密码（不回显；POST + 用户 version If-Match） */
export function resetUserPassword(id: ID, body: { temporaryPassword: string }, version: ID): Promise<UserVO> {
  return httpPost(`/users/${id}/password-reset`, body, version)
}

/** A13 查询员工已分配直接角色（显示当前分配，不伪造继承角色） */
export function getUserRoles(id: ID): Promise<UserRolesVO> {
  return httpGet(`/users/${id}/roles`)
}

/** A14 完整替换员工角色（跨页全量集合） */
export function assignUserRoles(id: ID, body: UserRoleAssignDTO, version: ID): Promise<UserRolesVO> {
  return httpPut(`/users/${id}/roles`, body, version)
}
