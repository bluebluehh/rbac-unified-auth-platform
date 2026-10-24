/** A36–A37：审计日志只读查询 */
import { httpGet } from './request'
import type { ID, LogVO, LogsQuery, Page } from '@/types'

/** A36 分页查询日志（from/to 必填，跨度 ≤31 日；时间/id 降序） */
export function listLogs(query: LogsQuery): Promise<Page<LogVO>> {
  return httpGet('/logs', query)
}

/** A37 日志详情（无 ETag，不可修改） */
export function getLog(id: ID): Promise<LogVO> {
  return httpGet(`/logs/${id}`)
}
