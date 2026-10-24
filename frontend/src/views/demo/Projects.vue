<script setup lang="ts">
/**
 * 业务演示页（A38/A39）：固定合成数据。
 * A39 提交按钮需固定权限 engineering:demo:project:submit。
 */
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listDemoProjects, submitDemoProject } from '@/api/demo'
import { P } from '@/constants/permissions'
import { usePermissionStore } from '@/stores/permission'
import type { DemoProjectVO } from '@/types'

const loading = ref(false)
const loadFailed = ref(false)
const projects = ref<DemoProjectVO[]>([])
const submitting = ref(false)
const permission = usePermissionStore()
const canView = computed(() => permission.has(P.demo.view))

async function load(): Promise<void> {
  loading.value = true
  loadFailed.value = false
  try {
    projects.value = await listDemoProjects()
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

async function onSubmit(): Promise<void> {
  submitting.value = true
  try {
    await submitDemoProject()
    ElMessage.success('演示操作提交成功，不会影响真实业务数据')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (canView.value) void load()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">工程业务演示</h2>
        <p class="page-subtitle">用于演示当前账号可查看和提交的工程项目数据，所有内容均为演示数据</p>
      </div>
      <el-button v-permission="P.demo.submit" type="primary" :loading="submitting" data-test="demo-submit" @click="onSubmit">
        提交演示操作
      </el-button>
    </div>

    <div v-loading="loading" class="table-min-height">
      <el-empty v-if="!canView" description="当前账号只能执行提交操作，暂不能查看项目列表" />

      <el-result v-else-if="loadFailed" icon="error" title="加载失败" sub-title="无法获取演示项目，请稍后重试">
        <template #extra>
          <el-button type="primary" @click="load">重试</el-button>
        </template>
      </el-result>

      <el-empty v-else-if="projects.length === 0" description="暂无演示项目" />

      <div v-else class="card project-list">
        <div v-for="p in projects" :key="p.id" class="project-item">
          <span class="dot" />
          <span class="name">{{ p.name }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.project-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 20px;
}

.project-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--rbac-border-light);

  &:last-child {
    border-bottom: none;
  }
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--rbac-primary);
  opacity: 0.6;
}

.name {
  flex: 1;
  color: var(--rbac-text-primary);
}

</style>
