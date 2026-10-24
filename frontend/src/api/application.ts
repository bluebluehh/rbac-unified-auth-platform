/** A27–A31：应用登记维护 */
import { httpGet, httpPatch, httpPost } from './request'
import type { ApplicationCreateDTO, ApplicationPatchDTO, ApplicationVO, ApplicationsQuery, BinaryStatusDTO, ID, Page } from '@/types'

/** A27 分页查询应用（门户卡片使用 A05，不走此接口） */
export function listApplications(query: ApplicationsQuery): Promise<Page<ApplicationVO>> {
  return httpGet('/applications', query)
}

/** A28 应用详情 */
export function getApplication(id: ID): Promise<ApplicationVO> {
  return httpGet(`/applications/${id}`)
}

/** A29 新增应用（不自动授权限） */
export function createApplication(body: ApplicationCreateDTO): Promise<ApplicationVO> {
  return httpPost('/applications', body)
}

/** A30 编辑应用（appKey 不可变） */
export function patchApplication(id: ID, body: ApplicationPatchDTO, version: ID): Promise<ApplicationVO> {
  return httpPatch(`/applications/${id}`, body, version)
}

/** A31 应用启停（platform 应用保护，禁用启停） */
export function patchApplicationStatus(id: ID, body: BinaryStatusDTO, version: ID): Promise<ApplicationVO> {
  return httpPatch(`/applications/${id}/status`, body, version)
}
