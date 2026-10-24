/**
 * 业务权限显示目录。
 * 仅用于把四段式权限 key 转成中文名称；当前员工是否拥有权限始终以 A06 返回为准。
 * 来源：db/migration-002-business-permissions.sql 与工程演示种子数据。
 */

import courseCatalog from '@/generated/coursePermissionCatalog.json'

export const COURSE_APPLICATION_KEYS: readonly string[] = courseCatalog.applicationKeys
const COURSE_PERMISSIONS = new Map<string, { permKey: string; moduleName: string; resourceName: string; actionName: string }>(
  courseCatalog.permissions.map((item) => [item.permKey, item]),
)

type CatalogRow = readonly [
  appKey: string,
  moduleKey: string,
  moduleName: string,
  resourceKey: string,
  resourceName: string,
]

const CATALOG_ROWS = [
  ['oa', 'doc', '公文管理', 'receive_register', '收文登记'],
  ['oa', 'doc', '公文管理', 'draft', '发文拟稿'],
  ['oa', 'doc', '公文管理', 'approve', '公文审批'],
  ['oa', 'doc', '公文管理', 'archive', '公文归档'],
  ['oa', 'approval', '审批流程', 'leave', '请假申请'],
  ['oa', 'approval', '审批流程', 'expense', '费用报销'],
  ['oa', 'approval', '审批流程', 'seal', '用章申请'],
  ['oa', 'approval', '审批流程', 'contract', '合同审批'],
  ['oa', 'meeting', '会议管理', 'notice', '会议通知'],
  ['oa', 'meeting', '会议管理', 'minutes', '会议纪要'],
  ['oa', 'notice', '公告通知', 'company_notice', '公司公告'],
  ['oa', 'notice', '公告通知', 'dept_notice', '部门通知'],
  ['hr', 'org', '组织管理', 'structure', '组织架构维护'],
  ['hr', 'staff', '人员管理', 'employee_profile', '员工档案'],
  ['hr', 'staff', '人员管理', 'contract', '合同管理'],
  ['hr', 'attendance', '考勤管理', 'clock_record', '打卡记录'],
  ['hr', 'attendance', '考勤管理', 'leave_record', '请假销假'],
  ['hr', 'performance', '薪酬绩效', 'payroll', '工资核算'],
  ['hr', 'performance', '薪酬绩效', 'appraisal', '绩效考核'],
  ['hr', 'recruit', '招聘培训', 'demand', '招聘需求'],
  ['hr', 'recruit', '招聘培训', 'training', '培训管理'],
  ['finance', 'budget', '预算管理', 'budgeting', '预算编制'],
  ['finance', 'budget', '预算管理', 'execution', '预算执行'],
  ['finance', 'accounting', '账务处理', 'voucher', '凭证录入'],
  ['finance', 'accounting', '账务处理', 'statement', '财务报表'],
  ['finance', 'fund', '资金管理', 'payment', '付款申请'],
  ['finance', 'fund', '资金管理', 'reconciliation', '银行对账'],
  ['finance', 'asset', '固定资产', 'ledger', '资产台账'],
  ['finance', 'cost', '成本核算', 'project_cost', '项目成本'],
  ['project', 'schedule', '进度管理', 'plan', '进度计划'],
  ['project', 'schedule', '进度管理', 'report', '进度填报'],
  ['project', 'schedule', '进度管理', 'warning', '进度预警'],
  ['project', 'quality', '质量管理', 'inspection', '质量检查'],
  ['project', 'quality', '质量管理', 'rectification', '质量整改'],
  ['project', 'safety', '安全管理', 'check', '安全检查'],
  ['project', 'safety', '安全管理', 'accident', '事故上报'],
  ['project', 'contract', '合同管理', 'ledger', '合同台账'],
  ['project', 'contract', '合同管理', 'variation', '变更签证'],
  ['project', 'drawing', '图纸管理', 'version', '图纸版本'],
  ['project', 'acceptance', '竣工验收', 'archives', '竣工资料'],
  ['bidding', 'tender', '招标管理', 'plan', '招标计划'],
  ['bidding', 'tender', '招标管理', 'document', '招标文件'],
  ['bidding', 'bid', '投标管理', 'registration', '投标报名'],
  ['bidding', 'evaluation', '评标管理', 'expert', '专家抽取'],
  ['bidding', 'evaluation', '评标管理', 'report', '评标报告'],
  ['bidding', 'award', '中标管理', 'notice', '中标通知'],
  ['material', 'purchase', '采购管理', 'request', '采购申请'],
  ['material', 'purchase', '采购管理', 'supplier', '供应商库'],
  ['material', 'inventory', '库存管理', 'in_out', '入库出库'],
  ['material', 'inventory', '库存管理', 'stocktake', '库存盘点'],
  ['material', 'equipment', '设备管理', 'ledger', '设备台账'],
  ['material', 'equipment', '设备管理', 'maintenance', '维保记录'],
  ['archive', 'document', '文书档案', 'archive', '公文归档'],
  ['archive', 'engineering', '工程档案', 'archive', '竣工归档'],
  ['archive', 'utilization', '档案利用', 'search', '档案检索'],
  ['archive', 'utilization', '档案利用', 'borrow', '借阅审批'],
  ['gis', 'map', '管线一张图', 'browse', '管网浏览'],
  ['gis', 'map', '管线一张图', 'query', '查询统计'],
  ['gis', 'data', '数据维护', 'input', '管线录入'],
  ['gis', 'data', '数据维护', 'update', '数据更新'],
  ['gis', 'emergency', '应急分析', 'collision', '碰撞分析'],
  ['inspection', 'patrol', '现场巡检', 'checkin', '巡检打卡'],
  ['inspection', 'patrol', '现场巡检', 'issue_report', '问题上报'],
  ['inspection', 'task', '任务管理', 'receive', '任务接收'],
  ['inspection', 'task', '任务管理', 'feedback', '整改反馈'],
  ['collab', 'im', '即时通讯', 'org', '组织架构'],
  ['collab', 'im', '即时通讯', 'chat', '聊天群组'],
  ['collab', 'meeting', '视频会议', 'initiate', '会议发起'],
  ['collab', 'notice', '公告推送', 'push', '消息推送'],
  ['knowledge', 'regulation', '制度文档', 'publish', '制度发布'],
  ['knowledge', 'regulation', '制度文档', 'search', '文档检索'],
  ['knowledge', 'training', '培训学习', 'learn', '在线学习'],
  ['knowledge', 'training', '培训学习', 'exam', '考试测评'],
  ['vehicle', 'dispatch', '车辆调度', 'request', '用车申请'],
  ['vehicle', 'dispatch', '车辆调度', 'dispatch', '派车管理'],
  ['vehicle', 'cost', '费用管理', 'fuel_maintenance', '油耗维修'],
  ['video', 'live', '实时监控', 'live_video', '现场视频'],
  ['video', 'playback', '录像回放', 'playback', '历史录像'],
  ['video', 'alert', 'AI预警', 'violation', '违规识别'],
  ['publicity', 'news', '新闻管理', 'publish', '新闻发布'],
  ['publicity', 'news', '新闻管理', 'review', '外宣审核'],
  ['publicity', 'showcase', '形象展示', 'project_display', '项目展示'],
  ['engineering', 'demo', '演示模块', 'project', '演示项目'],
] as const satisfies readonly CatalogRow[]

interface CatalogDisplay {
  appKey: string
  moduleKey: string
  moduleName: string
  resourceKey: string
  resourceName: string
  order: number
}

export interface PermissionItem {
  key: string
  moduleKey: string
  resourceKey: string
  moduleName: string
  resourceName: string
  actionCode: string
  actionName: string
  displayName: string
  order: number
}

export interface PermissionModuleGroup {
  key: string
  name: string
  permissions: PermissionItem[]
}

const CATALOG = new Map<string, CatalogDisplay>(
  CATALOG_ROWS.map(([appKey, moduleKey, moduleName, resourceKey, resourceName], order) => [
    `${appKey}:${moduleKey}:${resourceKey}`,
    { appKey, moduleKey, moduleName, resourceKey, resourceName, order },
  ]),
)

function humanizeKey(value: string): string {
  return value.replaceAll('_', ' ')
}

function resolveDisplay(permissionKey: string): PermissionItem | null {
  const [appKey, moduleKey, resourceKey, actionCode] = permissionKey.split(':')
  if (!appKey || !moduleKey || !resourceKey || !actionCode) return null

  const catalog = CATALOG.get(`${appKey}:${moduleKey}:${resourceKey}`)
  const course = COURSE_PERMISSIONS.get(permissionKey)
  return {
    key: permissionKey,
    moduleKey,
    resourceKey,
    moduleName: course?.moduleName ?? catalog?.moduleName ?? humanizeKey(moduleKey),
    resourceName: course?.resourceName ?? catalog?.resourceName ?? humanizeKey(resourceKey),
    actionCode,
    actionName: actionCode,
    displayName: '',
    order: catalog?.order ?? Number.MAX_SAFE_INTEGER,
  }
}

export function getPermissionDisplay(
  permissionKey: string,
  actionLabel: (code: string) => string,
): PermissionItem | null {
  const item = resolveDisplay(permissionKey)
  if (!item) return null

  const actionName = COURSE_PERMISSIONS.get(permissionKey)?.actionName ?? actionLabel(item.actionCode)
  const hasKnownResource = COURSE_PERMISSIONS.has(permissionKey) || CATALOG.has(`${item.key.split(':').slice(0, 3).join(':')}`)
  return {
    ...item,
    actionName,
    displayName: hasKnownResource ? `${actionName}${item.resourceName}` : `${actionName} · ${item.resourceName}`,
  }
}

export function getApplicationPermissionGroups(
  appKey: string,
  permissionKeys: string[],
  actionLabel: (code: string) => string,
): PermissionModuleGroup[] {
  const groups = new Map<string, PermissionModuleGroup & { order: number }>()
  const prefix = `${appKey}:`

  for (const key of permissionKeys) {
    if (!key.startsWith(prefix)) continue
    const permission = getPermissionDisplay(key, actionLabel)
    if (!permission) continue

    const group = groups.get(permission.moduleKey) ?? {
      key: permission.moduleKey,
      name: permission.moduleName,
      permissions: [],
      order: permission.order,
    }
    group.permissions.push(permission)
    groups.set(permission.moduleKey, group)
  }

  return [...groups.values()]
    .sort((left, right) => left.order - right.order)
    .map(({ order: _order, ...group }) => ({
      ...group,
      permissions: group.permissions.sort((left, right) => left.order - right.order),
    }))
}
