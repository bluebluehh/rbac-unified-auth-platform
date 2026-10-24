import { ElMessage } from 'element-plus'
import { ApiError } from '@/api/errors'

/** 请求层已显示统一错误时，页面不再重复提示。 */
export function showApiError(error: unknown, fallback: string): void {
  if (error instanceof ApiError && error.userNotified) return
  ElMessage.error(error instanceof ApiError ? error.message : fallback)
}
