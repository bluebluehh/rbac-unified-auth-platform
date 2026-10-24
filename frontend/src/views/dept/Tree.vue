<script setup lang="ts">
/**
 * 部门维护（A15–A19，文档 §10.7）：
 * - A15 组织树（管理视图含停用项）。
 * - 新增（A17）确定父节点后不可搬迁；nodeKey/nodeType 创建后不可变（A18 仅改名称/负责人）。
 * - 负责人使用 A07 分页检索（远程搜索），不全量加载；启停（A19）提示子树影响并回显影响人数。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getDeptTree, getDept, createDept, patchDept, patchDeptStatus } from '@/api/dept'
import { getUser, listUsers } from '@/api/user'
import { ApiError } from '@/api/errors'
import { P } from '@/constants/permissions'
import { useDictionaryStore } from '@/stores/dictionary'
import { applyFieldErrors } from '@/utils/formErrors'
import { showApiError } from '@/utils/apiMessages'
import { flattenDeptTree } from '@/utils/dept'
import type { DeptTreeVO, DeptVO, ID, NodeType, UserVO } from '@/types'

const dictionary = useDictionaryStore()

const loading = ref(false)
const loadFailed = ref(false)
const tree = ref<DeptTreeVO[]>([])

async function load(): Promise<void> {
  loading.value = true
  loadFailed.value = false
  try {
    tree.value = await getDeptTree({ includeDisabled: true })
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function rootId(): ID {
  const root = tree.value.find((n) => n.nodeType === 'ROOT')
  return root ? root.id : ''
}

function departmentPath(id: ID): string {
  if (!id) return '未选择上级组织'
  return flattenDeptTree(tree.value).find((item) => item.id === id)?.label ?? '上级组织'
}

/* ---------- 负责人远程检索 ---------- */

const managerOptions = ref<UserVO[]>([])
const managerLoading = ref(false)
let managerSeq = 0

async function searchManagers(keyword: string): Promise<void> {
  const seq = ++managerSeq
  managerLoading.value = true
  try {
    const page = await listUsers({ page: 1, size: 20, keyword: keyword || undefined })
    if (seq !== managerSeq) return
    managerOptions.value = page.records
  } catch {
    if (seq === managerSeq) managerOptions.value = []
  } finally {
    if (seq === managerSeq) managerLoading.value = false
  }
}

async function preloadManager(id: ID | null): Promise<void> {
  if (!id) return
  if (!managerOptions.value.some((u) => u.id === id)) {
    try {
      const u = await getUser(id)
      managerOptions.value = [u, ...managerOptions.value]
    } catch {
      /* 负责人不可读时保留空显示 */
    }
  }
}

/* ---------- 新增 ---------- */

const createVisible = ref(false)
const createSubmitting = ref(false)
const createRef = ref<FormInstance>()
const createForm = reactive({
  deptName: '',
  nodeKey: '',
  parentId: '' as ID,
  nodeType: 'DEPARTMENT' as Exclude<NodeType, 'ROOT'>,
  managerUserId: '' as ID | '',
})

const createRules: FormRules = {
  deptName: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { max: 160, message: '名称不超过 160 个字符', trigger: 'blur' },
  ],
  nodeKey: [
    { required: true, message: '请输入组织编码', trigger: 'blur' },
    { max: 160, message: '组织编码不超过 160 个字符', trigger: 'blur' },
  ],
  parentId: [{ required: true, message: '请选择上级组织', trigger: 'change' }],
  nodeType: [{ required: true, message: '请选择组织类型', trigger: 'change' }],
}

function openCreate(parentId: ID): void {
  const parent = flattenDeptTree(tree.value).find((node) => node.id === parentId)
  if (parent && parent.status === 0) {
    ElMessage.warning('停用组织下不能新增下级组织，请先启用该组织')
    return
  }
  createForm.deptName = ''
  createForm.nodeKey = ''
  createForm.parentId = parentId
  createForm.nodeType = 'DEPARTMENT'
  createForm.managerUserId = ''
  managerOptions.value = []
  createVisible.value = true
}

async function submitCreate(): Promise<void> {
  const valid = await createRef.value?.validate().catch(() => false)
  if (!valid) return
  createSubmitting.value = true
  try {
    await createDept({
      deptName: createForm.deptName,
      nodeKey: createForm.nodeKey,
      parentId: createForm.parentId,
      nodeType: createForm.nodeType,
      managerUserId: createForm.managerUserId || null,
    })
    ElMessage.success('组织已创建')
    createVisible.value = false
    void load()
  } catch (e) {
    if (e instanceof ApiError) applyFieldErrors(createRef.value, e)
    showApiError(e, '创建失败')
  } finally {
    createSubmitting.value = false
  }
}

/* ---------- 编辑 ---------- */

const editVisible = ref(false)
const editSubmitting = ref(false)
const editRef = ref<FormInstance>()
const editTarget = ref<DeptVO | null>(null)
const editForm = reactive({
  deptName: '',
  managerUserId: '' as ID | '',
})

const editRules: FormRules = {
  deptName: [
    { required: true, message: '请输入部门名称', trigger: 'blur' },
    { max: 160, message: '名称不超过 160 个字符', trigger: 'blur' },
  ],
}

async function openEdit(node: DeptVO): Promise<void> {
  try {
    const current = await getDept(node.id)
    editTarget.value = current
    editForm.deptName = current.deptName
    editForm.managerUserId = current.managerUserId ?? ''
    managerOptions.value = []
    void preloadManager(current.managerUserId)
    editVisible.value = true
  } catch (e) {
    if (e instanceof ApiError && e.code === 'NOT_FOUND') {
      ElMessage.warning('组织不存在或已被删除，组织树已刷新')
      void load()
      return
    }
    showApiError(e, '无法获取组织信息')
  }
}

async function submitEdit(): Promise<void> {
  const valid = await editRef.value?.validate().catch(() => false)
  if (!valid || !editTarget.value) return
  editSubmitting.value = true
  try {
    await patchDept(
      editTarget.value.id,
      {
        deptName: editForm.deptName,
        managerUserId: editForm.managerUserId || null,
      },
      editTarget.value.version,
    )
    ElMessage.success('组织信息已更新')
    editVisible.value = false
    void load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      editVisible.value = false
      ElMessage.warning('组织信息已有其他更新，组织树已刷新，请重新操作')
      void load()
      return
    }
    if (e instanceof ApiError) applyFieldErrors(editRef.value, e)
    showApiError(e, '更新失败')
  } finally {
    editSubmitting.value = false
  }
}

/* ---------- 启停 ---------- */

async function onStatus(node: DeptVO): Promise<void> {
  const action = node.status === 1 ? '停用' : '启用'
  try {
    await ElMessageBox.confirm(
      node.status === 1
        ? `停用「${node.deptName}」后，该组织及其全部下级组织将停止使用，相关员工需要重新登录，确定继续吗？`
        : `确定启用「${node.deptName}」吗？`,
      `${action}组织`,
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    const result = await patchDeptStatus(node.id, { status: node.status === 1 ? 0 : 1 }, node.version)
    ElMessage.success(`${action}成功${result.affectedUserCount > 0 ? `，涉及员工 ${result.affectedUserCount} 人` : ''}`)
    void load()
  } catch (e) {
    if (e instanceof ApiError && e.code === 'VERSION_CONFLICT') {
      ElMessage.warning('组织信息已有其他更新，组织树已刷新，请重新操作')
      void load()
      return
    }
    showApiError(e, '操作失败')
  }
}

function typeTagType(nodeType: string): 'primary' | 'success' | 'warning' | 'info' {
  if (nodeType === 'ROOT') return 'primary'
  if (nodeType === 'COMPANY') return 'success'
  if (nodeType === 'DEPARTMENT') return 'warning'
  return 'info'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">部门维护</h2>
        <p class="page-subtitle">查看公司、部门和项目部的组织结构，并维护组织信息和负责人</p>
      </div>
      <el-button v-permission="P.dept.create" type="primary" :disabled="!rootId()" @click="openCreate(rootId())">
        新增公司或部门
      </el-button>
    </div>

    <div class="card tree-card">
      <div v-loading="loading" class="tree-wrap">
        <el-result v-if="loadFailed" icon="error" title="加载失败" sub-title="暂时无法获取组织信息">
          <template #extra>
            <el-button type="primary" @click="load">重试</el-button>
          </template>
        </el-result>
        <el-empty v-else-if="tree.length === 0" description="暂无组织信息" />
        <el-tree
          v-else
          :data="tree"
          node-key="id"
          :props="{ label: 'deptName', children: 'children' }"
          :default-expanded-keys="[rootId()]"
          highlight-current
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span class="node-name">{{ (data as DeptVO).deptName }}</span>
              <el-tag
                v-if="(data as DeptVO).nodeType !== 'ROOT'"
                :type="typeTagType((data as DeptVO).nodeType)"
                size="small"
                class="node-tag"
              >
                {{ dictionary.nodeTypeLabel((data as DeptVO).nodeType) }}
              </el-tag>
              <el-tag :type="(data as DeptVO).status === 1 ? 'success' : 'info'" size="small" class="node-tag">
                {{ dictionary.binaryStatusLabel((data as DeptVO).status) }}
              </el-tag>
              <span class="node-actions">
                <el-button
                  v-permission="P.dept.create"
                  link
                  type="primary"
                  size="small"
                  :disabled="(data as DeptVO).status === 0"
                  @click.stop="openCreate((data as DeptVO).id)"
                >
                  新增下级组织
                </el-button>
                <el-button v-permission="P.dept.edit" link type="primary" size="small" @click.stop="openEdit(data as DeptVO)">编辑</el-button>
                <el-button
                  v-permission="P.dept.status"
                  link
                  size="small"
                  :type="(data as DeptVO).status === 1 ? 'danger' : 'primary'"
                  :disabled="(data as DeptVO).nodeType === 'ROOT'"
                  @click.stop="onStatus(data as DeptVO)"
                >
                  {{ (data as DeptVO).status === 1 ? '停用' : '启用' }}
                </el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>
    </div>

    <el-dialog v-model="createVisible" title="新增组织" width="520px" destroy-on-close>
      <el-form ref="createRef" :model="createForm" :rules="createRules" label-width="90px">
        <el-form-item label="上级组织" prop="parentId">
          <el-input :model-value="departmentPath(createForm.parentId)" disabled />
        </el-form-item>
        <el-form-item label="组织名称" prop="deptName">
          <el-input v-model="createForm.deptName" />
        </el-form-item>
        <el-form-item label="组织编码" prop="nodeKey">
          <el-input v-model="createForm.nodeKey" placeholder="系统内唯一，创建后不能修改" />
        </el-form-item>
        <el-form-item label="组织类型" prop="nodeType">
          <el-select v-model="createForm.nodeType" style="width: 100%">
            <el-option v-for="t in dictionary.dict?.nodeTypes ?? []" :key="t.value" :label="t.label" :value="t.value" :disabled="t.value === 'ROOT'" />
          </el-select>
        </el-form-item>
        <el-form-item label="组织负责人" prop="managerUserId">
          <el-select
            v-model="createForm.managerUserId"
            filterable
            remote
            clearable
            reserve-keyword
            placeholder="按工号或姓名搜索"
            :remote-method="searchManagers"
            :loading="managerLoading"
            style="width: 100%"
          >
            <el-option v-for="u in managerOptions" :key="u.id" :label="`${u.username} · ${u.realName}`" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑组织" width="520px" destroy-on-close>
      <el-descriptions v-if="editTarget" :column="1" border size="small" class="readonly">
        <el-descriptions-item label="组织编码">{{ editTarget.nodeKey }}</el-descriptions-item>
        <el-descriptions-item label="组织类型">{{ dictionary.nodeTypeLabel(editTarget.nodeType) }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="editRef" :model="editForm" :rules="editRules" label-width="90px" class="edit-form">
        <el-form-item label="组织名称" prop="deptName">
          <el-input v-model="editForm.deptName" />
        </el-form-item>
        <el-form-item label="组织负责人" prop="managerUserId">
          <el-select
            v-model="editForm.managerUserId"
            filterable
            remote
            clearable
            reserve-keyword
            placeholder="按工号或姓名搜索"
            :remote-method="searchManagers"
            :loading="managerLoading"
            style="width: 100%"
          >
            <el-option v-for="u in managerOptions" :key="u.id" :label="`${u.username} · ${u.realName}`" :value="u.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.tree-card {
  min-height: 320px;
}

.tree-wrap {
  min-height: 320px;
}

.tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.node-name {
  font-size: 14px;
  color: var(--rbac-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-actions {
  margin-left: auto;
  white-space: nowrap;
}

.readonly {
  margin-bottom: 14px;
}

.edit-form {
  margin-top: 14px;
}
</style>
