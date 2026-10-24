/** 生成请求追踪 ID（X-Request-Id），用于日志联查（A36/A37 可按 requestId 筛选）。 */
export function genRequestId(): string {
  const c = globalThis.crypto
  if (c && typeof c.randomUUID === 'function') {
    return `fe-${c.randomUUID()}`
  }
  return `fe-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}
