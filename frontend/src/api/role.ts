/** A20–A26：角色与权限分配 */
import { httpGet, httpPatch, httpPost, httpPut } from './request'
import type { BinaryStatusDTO, ID, Page, RoleCreateDTO, RolePatchDTO, RolePermissionAssignDTO, RolePermissionsVO, RoleVO, RolesQuery } from '@/types'

/** A20 分页查询角色 */
export function listRoles(query: RolesQuery): Promise<Page<RoleVO>> {
  return httpGet('/roles', query)
}

/** A21 角色详情 */
export function getRole(id: ID): Promise<RoleVO> {
  return httpGet(`/roles/${id}`)
}

/** A22 新增角色（初始无权限） */
export function createRole(body: RoleCreateDTO): Promise<RoleVO> {
  return httpPost('/roles', body)
}

/** A23 编辑角色（保留角色禁止变更） */
export function patchRole(id: ID, body: RolePatchDTO, version: ID): Promise<RoleVO> {
  return httpPatch(`/roles/${id}`, body, version)
}

/** A24 角色启停（停用保留关系但不产生有效权限） */
export function patchRoleStatus(id: ID, body: BinaryStatusDTO, version: ID): Promise<RoleVO> {
  return httpPatch(`/roles/${id}/status`, body, version)
}

/** A25 查询角色已分配权限（含已绑定停用权限） */
export function getRolePermissions(id: ID): Promise<RolePermissionsVO> {
  return httpGet(`/roles/${id}/permissions`)
}

/** A26 完整替换角色权限（跨页全量集合；不能修改 PLATFORM_ADMIN） */
export function assignRolePermissions(id: ID, body: RolePermissionAssignDTO, version: ID): Promise<RolePermissionsVO> {
  return httpPut(`/roles/${id}/permissions`, body, version)
}
