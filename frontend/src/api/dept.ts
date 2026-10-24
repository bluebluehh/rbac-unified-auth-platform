/** A15–A19：部门树与节点维护 */
import { httpGet, httpPatch, httpPost } from './request'
import type { BinaryStatusDTO, DeptCreateDTO, DeptPatchDTO, DeptStatusVO, DeptTreeVO, DeptVO, DeptsTreeQuery, ID } from '@/types'

/** A15 组织树（默认 ROOT；includeDisabled 控制含停用项） */
export function getDeptTree(query?: DeptsTreeQuery): Promise<DeptTreeVO[]> {
  return httpGet('/depts/tree', query)
}

/** A16 部门详情 */
export function getDept(id: ID): Promise<DeptVO> {
  return httpGet(`/depts/${id}`)
}

/** A17 新增部门（父节点需启用；不能创建 ROOT） */
export function createDept(body: DeptCreateDTO): Promise<DeptVO> {
  return httpPost('/depts', body)
}

/** A18 编辑部门（不改变父节点） */
export function patchDept(id: ID, body: DeptPatchDTO, version: ID): Promise<DeptVO> {
  return httpPatch(`/depts/${id}`, body, version)
}

/** A19 部门启停（返回 item + affectedUserCount） */
export function patchDeptStatus(id: ID, body: BinaryStatusDTO, version: ID): Promise<DeptStatusVO> {
  return httpPatch(`/depts/${id}/status`, body, version)
}
