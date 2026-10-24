<script setup lang="ts">
/**
 * 权限点管理（A32–A35，文档 §10.7）：
 * - A32 按 applicationId/keyword/status 分页查询。
 * - A34 新增不提交 permKey/permName（后端生成四段码）；actionCode 来自 A40 字典。
 * - A35 启停；固定技术权限（platform:*）只读，后端双重校验。
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listPermissions, getPermission, createPermission, patchPermissionStatus } from '@/api/permission'
import { listApplications } from '@/api/application'
import { ApiError } from '@/api/errors'
import { P } from '@/constants/permissions'
import { useDictionaryStore } from '@/stores/dictionary'
import { applyFieldErrors } from '@/utils/formErrors'
import { showApiError } from '@/utils/apiMessages'
import { formatDateTime } from '@/utils/format'
import type { ApplicationVO, ID, Page, PermissionVO } from '@/types'

const dictionary = useDictionaryStore()

const loading = ref(false)
const loadFailed = ref(false)
const permissions = ref<Page<PermissionVO>>({ records: [], total: 0, page: 1, size: 20 })
const query = reactive({ page: 1, size: 20, applicationId: '' as ID | '', moduleKey: '', keyword: '', status: '' as 0 | 1 | '' })
const applications = ref<ApplicationVO[]>([])
const enabledApplications = computed(() => applications.value.filter((app) => app.status === 1))
/**const moduleOptions = ref<Array<{ key: string; name: string }>>([])**/
let loadSeq = 0

async function load(): Promise<void> {
  const seq = ++loadSeq
  loading.value = true
  loadFailed.value = false
  try {
    const result = await listPermissions({
      page: query.page,
      size: query.size,
      applicationId: query.applicationId || undefined,
      moduleKey: query.moduleKey.trim() || undefined,
      keyword: query.keyword.trim() || undefined,
      status: query.status === '' ? undefined : query.status,
    })
    if (seq !== loadSeq) return
    permissions.value = result
  } catch {
    if (seq === loadSeq) loadFailed.value = true
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

async function loadApplications(): Promise<void> {
  try {
    const all: ApplicationVO[] = []
    let page = 1
    for (;;) {
      const result = await listApplications({ page, size: 100 })
      all.push(...result.records)
      if (all.length >= result.total || result.records.length === 0) break
      page += 1
    }
    applications.value = all
  } catch {
    /* 应用候选项加载失败不阻断列表 */
  }
}

/**async function loadModuleOptions(): Promise<void> {
  try {
    const seen = new Map<string, string>()
    const all: PermissionVO[] = []
    let page = 1
    for (;;) {
      const result = await listPermissions({ page, size: 100 })
      all.push(...result.records)
      if (all.length >= result.total || result.records.length === 0) break
      page += 1
    }
    for (const p of all) {
      if (!seen.has(p.moduleKey)) seen.set(p.moduleKey, p.moduleName)
    }
    moduleOptions.value = [...seen.entries()]
      .map(([key, name]) => ({ key, name }))
      .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
  } catch {

  }
}**/

function onSearch(): void {
  query.page = 1
  void load()
}

function onReset(): void {
  query.applicationId = ''
  query.moduleKey = ''
  query.keyword = ''
  query.status = ''
  query.page = 1
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

function isTechnical(p: PermissionVO): boolean {
  return p.permKey.startsWith('platform:')
}

/** 功能板块分组键：同一应用下的同一模块合并为一个板块。 */
function moduleGroupKey(p: PermissionVO): string {
  return `${p.applicationId}:${p.moduleKey}`
}

/** 合并「功能板块」列中相邻同模块单元格，形成板块划分。 */
function spanModule({ rowIndex, columnIndex }: { rowIndex: number; columnIndex: number }): { rowspan: number; colspan: number } {
  if (columnIndex !== 0) return { rowspan: 1, colspan: 1 }
  const records = permissions.value.records
  const current = records[rowIndex]
  if (rowIndex > 0 && moduleGroupKey(records[rowIndex - 1]) === moduleGroupKey(current)) {
    return { rowspan: 0, colspan: 0 }
  }
  let count = 1
  while (rowIndex + count < records.length && moduleGroupKey(records[rowIndex + count]) === moduleGroupKey(current)) {
    count += 1
  }
  return { rowspan: count, colspan: 1 }
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<PermissionVO | null>(null)

async function openDetail(row: PermissionVO): Promise<void> {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await getPermission(row.id)
  } catch (e) {
    if (e instanceof ApiError && e.code === 'NOT_FOUND') {
      detailVisible.value = false
      ElMessage.warning('功能权限不存在或已被删除，列表已刷新')
      void load()
    } else {
      showApiError(e, '无法获取功能权限详情')
    }
  } finally {
    detailLoading.value = false
  }
}

/* ---------- 新增 ---------- */

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  applicationId: '' as ID,
  moduleKey: '',
  moduleName: '',
  resourceKey: '',
  resourceName: '',
  resourceDescription: '',
  actionCode: '',
})

const rules: FormRules = {
  applicationId: [{ required: true, message: '请选择所属应用', trigger: 'change' }],
  moduleKey: [
    { required: true, message: '请输入模块编码', trigger: 'blur' },
    { max: 32, message: '模块编码不超过 32 个字符', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: '须以小写字母开头，仅含小写字母/数字/下划线', trigger: 'blur' },
  ],
  moduleName: [
    { required: true, message: '请输入模块名', trigger: 'blur' },
    { max: 100, message: '模块名不超过 100 个字符', trigger: 'blur' },
  ],
  resourceKey: [
    { required: true, message: '请输入功能编码', trigger: 'blur' },
    { max: 48, message: '功能编码不超过 48 个字符', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: '须以小写字母开头，仅含小写字母/数字/下划线', trigger: 'blur' },
  ],
  resourceName: [
    { required: true, message: '请输入功能名', trigger: 'blur' },
    { max: 100, message: '功能名不超过 100 个字符', trigger: 'blur' },
  ],
  actionCode: [{ required: true, message: '请选择动作', trigger: 'change' }],
}

function openCreate(): void {
  form.applicationId = ''
  form.moduleKey = ''
  form.moduleName = ''
  form.resourceKey = ''
  form.resourceName = ''
  form.resourceDescription = ''
  form.actionCode = ''
  dialogVisible.value = true
}

async function submitCreate(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createPermission({
      applicationId: form.applicationId,
      moduleKey: form.moduleKey,
      moduleName: form.moduleName,
      resourceKey: form.resourceKey,
      resourceName: form.resourceName,
      resourceDescription: form.resourceDescription || null,
      actionCode: form.actionCode as PermissionVO['actionCode'],
    })
    ElMessage.success('权限信息已创建，系统已自动生成权限编码')
    dialogVisible.value = false
    void load()
  } catch (e) {
    if (e instanceof ApiError) applyFieldErrors(formRef.value, e)
    showApiError(e, '创建失败')
  } finally {
    submitting.value = false
  }
}

/* ---------- 启停 ---------- */

async function onStatus(p: PermissionVO): Promise<void> {
  const action = p.status === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定${action}功能权限「${p.permName}」吗？`,
      `${action}功能权限`,
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await patchPermissionStatus(p.id, { status: p.status === 1 ? 0 : 1 }, p.version)
    ElMessage.success(`${action}成功`)
    void load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      ElMessage.warning('功能权限数据已有其他更新，列表已刷新，请重新操作')
      void load()
      return
    }
    showApiError(e, '操作失败')
  }
}

onMounted(() => {
  void load()
  void loadApplications()
  /*void loadModuleOptions()*/
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">权限管理</h2>
        <p class="page-subtitle">维护员工在各应用和功能模块中的操作权限</p>
      </div>
      <el-button v-permission="P.permission.create" type="primary" @click="openCreate">新增功能权限</el-button>
    </div>

    <div class="card toolbar">
      <el-select v-model="query.applicationId" placeholder="所属应用" clearable filterable style="width: 200px" @change="onSearch">
        <el-option v-for="a in applications" :key="a.id" :label="a.appName" :value="a.id" />
      </el-select>
      <!--  <el-select v-model="query.moduleKey" placeholder="功能板块" clearable filterable style="width: 180px" @change="onSearch">
          <el-option v-for="m in moduleOptions" :key="m.key" :label="m.name" :value="m.key" />
        </el-select>-->
        <el-input v-model="query.keyword" placeholder="按权限名称或权限编码搜索" clearable style="width: 250px" @keyup.enter="onSearch" @clear="onSearch" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="onSearch">
          <el-option v-for="s in dictionary.dict?.binaryStatuses ?? []" :key="String(s.value)" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="onSearch">查询</el-button>
        <el-button @click="onReset">重置</el-button>
      </div>

      <div class="card">
        <el-alert
          v-if="loadFailed && permissions.records.length > 0"
          title="列表刷新失败，当前显示的是上一次加载的数据"
          type="warning"
          show-icon
          :closable="false"
          class="load-alert"
        />
        <el-table v-loading="loading" :data="permissions.records" class="table" :span-method="spanModule">
          <el-table-column label="功能板块" min-width="140" show-overflow-tooltip class-name="module-cell">
            <template #default="{ row }">
              <span class="module-name">{{ (row as PermissionVO).moduleName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="permName" label="权限名称" min-width="160" />
          <el-table-column prop="permKey" label="权限编码" min-width="240" show-overflow-tooltip />
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              {{ dictionary.actionLabel((row as PermissionVO).actionCode) }}
            </template>
          </el-table-column>
          <el-table-column label="权限类型" width="100">
            <template #default="{ row }">
              <el-tag v-if="isTechnical(row as PermissionVO)" type="warning" size="small">系统</el-tag>
              <span v-else class="text-secondary">业务</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="(row as PermissionVO).status === 1 ? 'success' : 'info'" size="small">
                {{ dictionary.binaryStatusLabel((row as PermissionVO).status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row as PermissionVO)">详情</el-button>
              <el-button v-permission="P.permission.status" link :type="(row as PermissionVO).status === 1 ? 'danger' : 'primary'" :disabled="isTechnical(row as PermissionVO)" @click="onStatus(row as PermissionVO)">
                {{ (row as PermissionVO).status === 1 ? '停用' : '启用' }}
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty v-if="loadFailed" description="加载失败">
              <el-button type="primary" @click="load">重试</el-button>
            </el-empty>
            <el-empty v-else description="暂无功能权限" />
          </template>
        </el-table>

        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="permissions.total"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[20, 50, 100]"
          class="pagination"
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>

      <el-dialog v-model="detailVisible" title="功能权限详情" width="600px" destroy-on-close>
        <div v-loading="detailLoading" class="permission-detail">
          <el-descriptions v-if="detail" :column="2" border>
            <el-descriptions-item label="权限名称">{{ detail.permName }}</el-descriptions-item>
            <el-descriptions-item label="操作权限">{{ dictionary.actionLabel(detail.actionCode) }}</el-descriptions-item>
            <el-descriptions-item label="所属应用">{{ applications.find((app) => app.id === detail?.applicationId)?.appName ?? '—' }}</el-descriptions-item>
            <el-descriptions-item label="权限类型">{{ isTechnical(detail) ? '系统' : '业务' }}</el-descriptions-item>
            <el-descriptions-item label="模块名称">{{ detail.moduleName }}</el-descriptions-item>
            <el-descriptions-item label="功能名称">{{ detail.resourceName }}</el-descriptions-item>
            <el-descriptions-item label="功能说明" :span="2">{{ detail.resourceDescription || '—' }}</el-descriptions-item>
            <el-descriptions-item label="权限编码" :span="2">{{ detail.permKey }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ dictionary.binaryStatusLabel(detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="最近更新">{{ formatDateTime(detail.updateTime) }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-dialog>

      <el-dialog v-model="dialogVisible" title="新增功能权限" width="560px" destroy-on-close>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
          <el-form-item label="所属应用" prop="applicationId">
            <el-select v-model="form.applicationId" style="width: 100%">
              <el-option
                v-for="a in enabledApplications"
                :key="a.id"
                :label="a.appName"
                :value="a.id"
                :disabled="a.appKey === 'platform'"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="模块编码" prop="moduleKey">
            <el-input v-model="form.moduleKey" placeholder="小写字母开头，可包含数字和下划线" />
          </el-form-item>
          <el-form-item label="模块名称" prop="moduleName">
            <el-input v-model="form.moduleName" placeholder="如 薪酬管理" />
          </el-form-item>
          <el-form-item label="功能编码" prop="resourceKey">
            <el-input v-model="form.resourceKey" placeholder="小写字母开头，可包含数字和下划线" />
          </el-form-item>
          <el-form-item label="功能名称" prop="resourceName">
            <el-input v-model="form.resourceName" placeholder="如 工资单" />
          </el-form-item>
          <el-form-item label="功能说明" prop="resourceDescription">
            <el-input v-model="form.resourceDescription" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item label="操作权限" prop="actionCode">
            <el-select v-model="form.actionCode" style="width: 100%">
              <el-option v-for="a in dictionary.dict?.actions ?? []" :key="a.code" :label="a.label" :value="a.code" />
            </el-select>
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitCreate">创建</el-button>
        </template>
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

  .module-cell {
    background-color: var(--el-fill-color-light);
  }

  .module-name {
    font-weight: 600;
    color: var(--el-color-primary);
  }

  .permission-detail {
    min-height: 140px;
  }
  </style>
