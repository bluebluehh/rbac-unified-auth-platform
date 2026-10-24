/** A38–A39：固定演示接口（合成数据，不修改真实业务） */
import { httpGet, httpPostNoBody } from './request'
import type { DemoActionVO, DemoProjectVO } from '@/types'

/** A38 固定合成项目列表 */
export function listDemoProjects(): Promise<DemoProjectVO[]> {
  return httpGet('/demo/projects')
}

/** A39 演示提交（按钮需固定 submit 权限；审计失败返回 500） */
export function submitDemoProject(): Promise<DemoActionVO> {
  return httpPostNoBody('/demo/projects/submit')
}
