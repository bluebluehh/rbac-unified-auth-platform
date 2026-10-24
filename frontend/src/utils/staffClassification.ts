import roleCatalog from '@/generated/roleClassification.json'

export const GRADES = [
  { value: 'LEADER', label: '公司领导' },
  { value: 'DEPT_MANAGER', label: '部门负责人' },
  { value: 'PROJECT_MANAGER', label: '项目负责人' },
  { value: 'EMPLOYEE', label: '普通员工' },
  { value: 'PLATFORM', label: '平台管理人员' },
] as const

const leaderPositions = new Set(['董事长','总经理','副总经理','总工程师','总会计师','党委副书记','工会主席','总经理助理','董事会秘书','经理','副经理','安全总监','经理助理'])
export function staffGrade(position: string): string {
  if (position === '控制面管理员') return 'PLATFORM'
  if (leaderPositions.has(position)) return 'LEADER'
  if (['主任','副主任','部长','副部长'].includes(position)) return 'DEPT_MANAGER'
  if (['项目经理','项目副经理','项目总工程师'].includes(position)) return 'PROJECT_MANAGER'
  return 'EMPLOYEE'
}
export function gradeLabel(grade: string): string {
  return GRADES.find(g => g.value === grade)?.label ?? grade
}
export interface RoleClass { departments: string[]; grade: string; positions: string[] }
const knownRoles: Record<string, RoleClass> = roleCatalog
export function roleClassification(key: string): RoleClass {
  return knownRoles[key] ?? { departments: ['其他角色'], grade: '待分类', positions: [] }
}

export interface HierarchyNode {
  key: string
  label: string
  deptId?: string
  grade?: string
  department?: string
  companyId?: string
  children?: HierarchyNode[]
}
