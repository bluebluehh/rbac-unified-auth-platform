/** A32–A35：权限点维护 */
import { httpGet, httpPatch, httpPost } from './request'
import type { BinaryStatusDTO, ID, Page, PermissionCreateDTO, PermissionVO, PermissionsQuery } from '@/types'

/** A32 分页查询权限点（keyword 匹配显示名或权限码） */
export function listPermissions(query: PermissionsQuery): Promise<Page<PermissionVO>> {
  return httpGet('/permissions', query)
}

/** A33 权限点详情 */
export function getPermission(id: ID): Promise<PermissionVO> {
  return httpGet(`/permissions/${id}`)
}

/** A34 新增权限点（四段码由后端生成，页面不提交 permKey） */
export function createPermission(body: PermissionCreateDTO): Promise<PermissionVO> {
  return httpPost('/permissions', body)
}

/** A35 权限点启停（固定技术权限不能停用） */
export function patchPermissionStatus(id: ID, body: BinaryStatusDTO, version: ID): Promise<PermissionVO> {
  return httpPatch(`/permissions/${id}/status`, body, version)
}
