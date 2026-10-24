<script setup lang="ts">
/**
 * 应用管理（A27–A31，文档 §10.7）：
 * - A27 分页/搜索；appKey 创建后不可变（A30 仅改名称）。
 * - 技术管理应用 platform 受保护（reservedAppKeys），禁止启停/编辑（后端双重校验）。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { listApplications, getApplication, createApplication, patchApplication, patchApplicationStatus } from '@/api/application'
import { ApiError } from '@/api/errors'
import { P } from '@/constants/permissions'
import { useDictionaryStore } from '@/stores/dictionary'
import { applyFieldErrors } from '@/utils/formErrors'
import { showApiError } from '@/utils/apiMessages'
import type { ApplicationVO, Page } from '@/types'

const dictionary = useDictionaryStore()

const loading = ref(false)
const loadFailed = ref(false)
const apps = ref<Page<ApplicationVO>>({ records: [], total: 0, page: 1, size: 20 })
const query = reactive({ page: 1, size: 20, keyword: '', status: '' as 0 | 1 | '' })
let loadSeq = 0

async function load(): Promise<void> {
  const seq = ++loadSeq
  loading.value = true
  loadFailed.value = false
  try {
    const result = await listApplications({
      page: query.page,
      size: query.size,
      keyword: query.keyword.trim() || undefined,
      status: query.status === '' ? undefined : query.status,
    })
    if (seq !== loadSeq) return
    apps.value = result
  } catch {
    if (seq === loadSeq) loadFailed.value = true
  } finally {
    if (seq === loadSeq) loading.value = false
  }
}

function onSearch(): void {
  query.page = 1
  void load()
}

function onReset(): void {
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

function isReserved(app: ApplicationVO): boolean {
  return app.appKey === 'platform' || (dictionary.dict?.reservedAppKeys ?? []).includes(app.appKey)
}

/* ---------- 新增 / 编辑 ---------- */

const dialogVisible = ref(false)
const submitting = ref(false)
const editing = ref<ApplicationVO | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  appName: '',
  appKey: '',
  status: 1 as 0 | 1,
})

const rules: FormRules = {
  appName: [
    { required: true, message: '请输入应用名称', trigger: 'blur' },
    { max: 100, message: '应用名称不超过 100 个字符', trigger: 'blur' },
  ],
  appKey: [
    { required: true, message: '请输入应用编码', trigger: 'blur' },
    { max: 32, message: '编码不超过 32 个字符', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]*$/, message: '编码须以小写字母开头，仅含小写字母/数字/下划线', trigger: 'blur' },
  ],
}

function openCreate(): void {
  editing.value = null
  form.appName = ''
  form.appKey = ''
  form.status = 1
  dialogVisible.value = true
}

async function openEdit(app: ApplicationVO): Promise<void> {
  try {
    const current = await getApplication(app.id)
    editing.value = current
    form.appName = current.appName
    form.appKey = current.appKey
    form.status = current.status
    dialogVisible.value = true
  } catch (e) {
    if (e instanceof ApiError && e.code === 'NOT_FOUND') {
      ElMessage.warning('应用不存在或已被删除，列表已刷新')
      void load()
      return
    }
    showApiError(e, '无法获取应用信息')
  }
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (editing.value) {
      await patchApplication(editing.value.id, { appName: form.appName }, editing.value.version)
      ElMessage.success('应用已更新')
    } else {
      await createApplication({ appName: form.appName, appKey: form.appKey, status: form.status })
      ElMessage.success('应用已创建')
    }
    dialogVisible.value = false
    void load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      dialogVisible.value = false
      ElMessage.warning('应用信息已有其他更新，列表已刷新，请重新操作')
      void load()
      return
    }
    if (e instanceof ApiError) applyFieldErrors(formRef.value, e)
    showApiError(e, '保存失败')
  } finally {
    submitting.value = false
  }
}

/* ---------- 启停 ---------- */

async function onStatus(app: ApplicationVO): Promise<void> {
  const action = app.status === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定${action}应用「${app.appName}」吗？${app.status === 1 ? '停用后其全部下属权限将失效。' : ''}`,
      `${action}应用`,
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await patchApplicationStatus(app.id, { status: app.status === 1 ? 0 : 1 }, app.version)
    ElMessage.success(`${action}成功`)
    void load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      ElMessage.warning('应用信息已有其他更新，列表已刷新，请重新操作')
      void load()
      return
    }
    showApiError(e, '操作失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">应用管理</h2>
        <p class="page-subtitle">维护员工可使用的应用系统及其启用状态</p>
      </div>
      <el-button v-permission="P.app.create" type="primary" @click="openCreate">新增应用</el-button>
    </div>

    <div class="card toolbar">
      <el-input v-model="query.keyword" placeholder="按应用名称或应用编码搜索" clearable style="width: 240px" @keyup.enter="onSearch" @clear="onSearch" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 140px" @change="onSearch">
        <el-option v-for="s in dictionary.dict?.binaryStatuses ?? []" :key="String(s.value)" :label="s.label" :value="s.value" />
      </el-select>
      <el-button type="primary" @click="onSearch">查询</el-button>
      <el-button @click="onReset">重置</el-button>
    </div>

    <div class="card">
      <el-alert
        v-if="loadFailed && apps.records.length > 0"
        title="列表刷新失败，当前显示的是上一次加载的数据"
        type="warning"
        show-icon
        :closable="false"
        class="load-alert"
      />
      <el-table v-loading="loading" :data="apps.records" class="table">
        <el-table-column prop="appName" label="应用名称" min-width="180" />
        <el-table-column prop="appKey" label="应用编码" min-width="160">
          <template #default="{ row }">
            <span>{{ (row as ApplicationVO).appKey }}</span>
            <el-tag v-if="isReserved(row as ApplicationVO)" type="danger" size="small" class="reserved-tag">系统应用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="(row as ApplicationVO).status === 1 ? 'success' : 'info'" size="small">
              {{ dictionary.binaryStatusLabel((row as ApplicationVO).status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="P.app.edit" link type="primary" :disabled="isReserved(row as ApplicationVO)" @click="openEdit(row as ApplicationVO)">编辑</el-button>
            <el-button v-permission="P.app.status" link :type="(row as ApplicationVO).status === 1 ? 'danger' : 'primary'" :disabled="isReserved(row as ApplicationVO)" @click="onStatus(row as ApplicationVO)">
              {{ (row as ApplicationVO).status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty v-if="loadFailed" description="加载失败">
            <el-button type="primary" @click="load">重试</el-button>
          </el-empty>
          <el-empty v-else description="暂无应用" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="apps.total"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[20, 50, 100]"
        class="pagination"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑应用' : '新增应用'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="form.appName" />
        </el-form-item>
        <el-form-item label="应用编码" prop="appKey">
          <el-input v-model="form.appKey" :disabled="!!editing" placeholder="小写字母开头，可包含数字和下划线" />
        </el-form-item>
        <el-form-item v-if="!editing" label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
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

.reserved-tag {
  margin-left: 8px;
}
</style>
