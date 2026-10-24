/** A01–A06：认证门户 */
import { httpGet, httpPost, httpPostNoBody, httpPut } from './request'
import type { ApplicationVO, CurrentPermissionsQuery, CurrentPermissionsVO, LoginDTO, LoginVO, PasswordChangeDTO, UserVO } from '@/types'

/** A01 登录（PUBLIC） */
export function login(body: LoginDTO): Promise<LoginVO> {
  return httpPost('/auth/login', body)
}

/** A02 退出（PUBLIC，可携带 Token；重复退出也 200） */
export function logout(): Promise<null> {
  return httpPostNoBody('/auth/logout')
}

/** A03 当前用户（SELF） */
export function getMe(): Promise<UserVO> {
  return httpGet('/auth/me')
}

/** A04 首次改密/改密（SELF 允许受限；无 If-Match） */
export function changePassword(body: PasswordChangeDTO): Promise<null> {
  return httpPut('/auth/password', body)
}

/** A05 门户应用卡片（SELF）：后端按当前身份计算，前端不传 userId */
export function getMyApps(): Promise<ApplicationVO[]> {
  return httpGet('/auth/apps')
}

/** A06 当前有效权限 key（SELF） */
export function getMyPermissions(query?: CurrentPermissionsQuery): Promise<CurrentPermissionsVO> {
  return httpGet('/auth/permissions', query)
}
