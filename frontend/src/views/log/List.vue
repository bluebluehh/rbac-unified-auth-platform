<script setup lang="ts">
/**
 * 操作日志（A36/A37，只读，文档 §10.7）：
 * - from/to 必填，默认当天 UTC 范围；页面按本地时区显示；跨度 ≤31 天（客户端提示，后端再验）。
 * - A36 时间/id 降序；A37 详情无 ETag、不可修改；无新增/删除/导出。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listLogs, getLog } from '@/api/log'
import { ApiError } from '@/api/errors'
import { useDictionaryStore } from '@/stores/dictionary'
import { formatDateTime, todayUtcRange } from '@/utils/format'
import { showApiError } from '@/utils/apiMessages'
import type { ID, LogVO, Page } from '@/types'

const dictionary = useDictionaryStore()

const loading = ref(false)
const loadFailed = ref(false)
const logs = ref<Page<LogVO>>({ records: [], total: 0, page: 1, size: 20 })
let loadSeq = 0
const range = ref<[Date, Date] | null>(null)
const query = reactive({
  page: 1,
  size: 20,
  userId: '' as ID | '',
  eventType: '' as LogVO['eventType'] | '',
  result: '' as 'SUCCESS' | 'FAILURE' | '',
  requestId: '',
})

function initRange(): void {
  const { from, to } = todayUtcRange()
  range.value = [new Date(from), new Date(to)]
}

async function load(): Promise<void> {
  if (!range.value) return
  const seq = ++loadSeq
  const from = range.value[0].toISOString()
  const to = range.value[1].toISOString()
  loading.value = true
  loadFailed.value = false
  try {
    const result = await listLogs({
      page: query.page,
      size: query.size,
      from,
      to,
      userId: query.userId || undefined,
      eventType: query.eventType || undefined,
      result: query.result || undefined,
      requestId: query.requestId.trim() || undefined,
    })
    if (seq !== loadSeq) return
    logs.value = result
  } catch {
    if (seq === loadSeq) loadFailed.value = true
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function onSearch(): void {
  if (!range.value) {
    ElMessage.warning('请选择查询开始时间和结束时间')
    return
  }
  const spanDays = (range.value[1].getTime() - range.value[0].getTime()) / (24 * 3600 * 1000)
  if (spanDays > 31) {
    ElMessage.warning('查询时间跨度不能超过 31 天')
    return
  }
  query.page = 1
  void load()
}

function onReset(): void {
  initRange()
  query.page = 1
  query.userId = ''
  query.eventType = ''
  query.result = ''
  query.requestId = ''
  void load()
}

function onPageChange(p: number): void {
  query.page = p
  void load()
}

function onSizeChange(size: number): void {
  query.size = size
  query.page = 1
  void load()
}

/* ---------- 详情 ---------- */

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<LogVO | null>(null)

async function openDetail(row: LogVO): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getLog(row.id)
  } catch (e) {
    if (e instanceof ApiError && e.code === 'NOT_FOUND') {
      detailVisible.value = false
      ElMessage.warning('日志不存在或已被清理，列表已刷新')
      void load()
    } else {
      showApiError(e, '详情加载失败')
    }
  } finally {
    detailLoading.value = false
  }
}

function resultTagType(result: 'SUCCESS' | 'FAILURE'): 'success' | 'danger' {
  return result === 'SUCCESS' ? 'success' : 'danger'
}

function methodLabel(method: LogVO['httpMethod']): string {
  return {
    GET: '查询',
    POST: '提交',
    PUT: '更新',
    PATCH: '修改',
    DELETE: '删除',
  }[method] ?? method
}

function operationLabel(path: string): string {
  if (path.includes('/auth/login')) return '登录'
  if (path.includes('/auth/logout')) return '退出登录'
  if (path.includes('/auth/password')) return '修改密码'
  if (path.includes('/auth/permissions')) return '本人权限同步'
  if (path.includes('/users')) return '员工管理'
  if (path.includes('/depts')) return '组织管理'
  if (path.includes('/roles')) return '角色管理'
  if (path.includes('/applications')) return '应用管理'
  if (path.includes('/permissions')) return '功能权限管理'
  if (path.includes('/logs')) return '操作日志'
  if (path.includes('/demo/')) return '工程业务演示'
  return '其他操作'
}

function targetLabel(type: LogVO['targetType'], id: string | null): string {
  if (!type) return '—'
  const label = {
    USER: '员工',
    ROLE: '角色',
    DEPT: '组织',
    APP: '应用',
    PERMISSION: '功能权限',
    SESSION: '登录会话',
  }[type] ?? '业务数据'
  return id ? `${label} #${id}` : label
}

function reasonLabel(code: string): string {
  return {
    OK: '操作成功',
    LOGIN_FAILED: '登录失败',
    LOGIN_LOCKED: '账号暂时锁定',
    SESSION_INVALID: '登录已失效',
    PERMISSION_DENIED: '没有操作权限',
    VERSION_CONFLICT: '数据已有更新',
    AUTH_UNAVAILABLE: '权限服务暂不可用',
  }[code] ?? '其他处理结果'
}

function changeSummary(details: LogVO['details']): string {
  if (!details) return '没有可展示的变更内容'
  const labels: Record<string, string> = {
    realName: '姓名',
    positionName: '岗位',
    phone: '电话',
    companyId: '所属公司',
    deptId: '所属部门',
    status: '状态',
    roleIds: '角色',
    permissionIds: '功能权限',
    appName: '应用名称',
    deptName: '组织名称',
  }
  const fields = details.changedFields.map((field) => labels[field] ?? '业务信息')
  return fields.length > 0 ? `已更新：${[...new Set(fields)].join('、')}` : '已记录操作内容'
}

onMounted(() => {
  initRange()
  void load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">操作日志</h2>
        <p class="page-subtitle">查询员工在平台中的登录、维护和授权操作记录</p>
      </div>
    </div>

    <div class="card toolbar">
      <el-date-picker
        v-model="range"
        type="datetimerange"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        style="width: 360px"
      />
      <el-input v-model="query.userId" placeholder="员工记录编号" clearable style="width: 150px" @keyup.enter="onSearch" />
      <el-select v-model="query.eventType" placeholder="事件类型" clearable filterable style="width: 170px" @change="onSearch">
        <el-option v-for="e in dictionary.dict?.eventTypes ?? []" :key="e.value" :label="e.label" :value="e.value" />
      </el-select>
      <el-select v-model="query.result" placeholder="结果" clearable style="width: 120px" @change="onSearch">
        <el-option v-for="r in dictionary.dict?.auditResults ?? []" :key="r.value" :label="r.label" :value="r.value" />
      </el-select>
      <el-input v-model="query.requestId" placeholder="操作追踪编号" clearable style="width: 200px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <div class="card">
      <el-alert
        v-if="loadFailed && logs.records.length > 0"
        title="日志刷新失败，当前显示的是上一次加载的数据"
        type="warning"
        show-icon
        :closable="false"
        class="load-alert"
      />
      <el-table v-loading="loading" :data="logs.records" class="table">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime((row as LogVO).createTime) }}</template>
        </el-table-column>
        <el-table-column prop="usernameSnapshot" label="操作人" min-width="110" />
        <el-table-column label="事件" min-width="140">
          <template #default="{ row }">{{ dictionary.eventTypeLabel((row as LogVO).eventType) }}</template>
        </el-table-column>
        <el-table-column label="操作位置" min-width="180">
          <template #default="{ row }">{{ operationLabel((row as LogVO).requestPath) }}</template>
        </el-table-column>
        <el-table-column label="操作方式" width="100">
          <template #default="{ row }">{{ methodLabel((row as LogVO).httpMethod) }}</template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <el-tag :type="resultTagType((row as LogVO).result)" size="small">
              {{ dictionary.auditResultLabel((row as LogVO).result) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理说明" width="160">
          <template #default="{ row }">{{ reasonLabel((row as LogVO).reasonCode) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row as LogVO)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="loadFailed" description="加载失败">
            <el-button type="primary" @click="load">重试</el-button>
          </el-empty>
          <el-empty v-else description="暂无日志" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="logs.total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[20, 50, 100]"
        class="pagination"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="detailVisible" title="日志详情" width="680px" destroy-on-close>
      <div v-loading="detailLoading" class="detail-wrap">
        <el-descriptions v-if="detail" :column="2" border>
          <el-descriptions-item label="记录编号">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="追踪编号">{{ detail.requestId }}</el-descriptions-item>
          <el-descriptions-item label="事件">{{ dictionary.eventTypeLabel(detail.eventType) }}</el-descriptions-item>
          <el-descriptions-item label="结果">
            <el-tag :type="resultTagType(detail.result)" size="small">{{ dictionary.auditResultLabel(detail.result) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.usernameSnapshot ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="操作对象">{{ targetLabel(detail.targetType, detail.targetId) }}</el-descriptions-item>
          <el-descriptions-item label="操作方式">{{ methodLabel(detail.httpMethod) }}</el-descriptions-item>
          <el-descriptions-item label="处理说明">{{ reasonLabel(detail.reasonCode) }}</el-descriptions-item>
          <el-descriptions-item label="操作位置" :span="2">{{ operationLabel(detail.requestPath) }}</el-descriptions-item>
          <el-descriptions-item label="时间" :span="2">{{ formatDateTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.details" label="变更详情" :span="2">
            {{ changeSummary(detail.details) }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.table {
  width: 100%;
}

.load-alert {
  margin-bottom: 12px;
}

.pagination {
  margin-top: 14px;
  justify-content: flex-end;
}

.detail-wrap {
  min-height: 160px;
}

</style>
