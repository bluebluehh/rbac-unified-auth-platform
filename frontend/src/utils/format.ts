/** 展示工具：时间按本地时区渲染，请求时间统一带时区（文档 §10.7）。 */

function pad(n: number): string {
  return n.toString().padStart(2, '0')
}

/**
 * Spring 的 LocalDateTime 可能输出不带时区的 UTC 时间；兼容该格式，
 * 带 Z 或 ±HH:mm 的正式契约时间保持原样。
 */
function parseApiTimestamp(iso: string): Date {
  const value = iso.trim()
  const hasTimezone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)
  return new Date(hasTimezone ? value : `${value}Z`)
}

/** ISO 8601 UTC → 本地时区 yyyy-MM-dd HH:mm:ss；空值显示 — */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = parseApiTimestamp(iso)
  if (Number.isNaN(d.getTime())) return iso
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** 判断锁定时间是否仍在未来；无效或空值按未锁定处理。 */
export function isFutureTimestamp(iso: string | null | undefined): boolean {
  if (!iso) return false
  const time = parseApiTimestamp(iso).getTime()
  return !Number.isNaN(time) && time > Date.now()
}

/** 当天 UTC [from, to) 用于日志默认筛选（A36 默认当天 UTC 范围） */
export function todayUtcRange(): { from: string; to: string } {
  const now = new Date()
  const from = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()))
  const to = new Date(from.getTime() + 24 * 3600 * 1000)
  return { from: from.toISOString(), to: to.toISOString() }
}
