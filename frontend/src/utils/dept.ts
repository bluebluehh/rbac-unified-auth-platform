/**
 * 组织树扁平化与候选项工具（文档 §10.7）：
 * - 公司字段候选仅 ROOT/COMPANY；
 * - 部门字段候选取自所选公司的子树（含公司节点本身，源表存在直属分公司的员工）。
 */
import type { DeptTreeVO } from '@/types'

export interface DeptOption {
  id: string
  label: string
  nodeType: string
  status: 0 | 1
}

/** 将树扁平化为带完整路径的选项列表（先序） */
export function flattenDeptTree(nodes: DeptTreeVO[]): DeptOption[] {
  const out: DeptOption[] = []
  const walk = (list: DeptTreeVO[], prefix: string): void => {
    for (const node of list) {
      const label = prefix ? `${prefix} / ${node.deptName}` : node.deptName
      out.push({ id: node.id, label, nodeType: node.nodeType, status: node.status })
      if (node.children && node.children.length > 0) walk(node.children, label)
    }
  }
  walk(nodes, '')
  return out
}

/** 公司字段候选：仅 ROOT/COMPANY 节点；业务表单可要求只返回启用节点。 */
export function companyOptions(nodes: DeptTreeVO[], enabledOnly = false): DeptOption[] {
  return flattenDeptTree(nodes).filter(
    (n) =>
      (n.nodeType === 'ROOT' || n.nodeType === 'COMPANY') &&
      (!enabledOnly || n.status === 1),
  )
}

/** 部门字段候选：所选公司节点及其整个子树；业务表单可排除停用节点。 */
export function subtreeOptions(nodes: DeptTreeVO[], companyId: string, enabledOnly = false): DeptOption[] {
  const find = (list: DeptTreeVO[]): DeptTreeVO | null => {
    for (const node of list) {
      if (node.id === companyId) return node
      const found = find(node.children ?? [])
      if (found) return found
    }
    return null
  }
  const company = find(nodes)
  const options = company ? flattenDeptTree([company]) : []
  return enabledOnly ? options.filter((node) => node.status === 1) : options
}
