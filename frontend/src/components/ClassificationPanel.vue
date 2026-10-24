<script setup lang="ts">
import { ref } from 'vue'
import type { HierarchyNode } from '@/utils/staffClassification'
defineProps<{ nodes: HierarchyNode[]; selectedKey: string; title: string; hint?: string }>()
const emit = defineEmits<{ select: [node: HierarchyNode] }>()
const search = ref('')
const tree = ref<{ filter: (value: string) => void }>()
function filter(value: string, data: HierarchyNode, node: unknown): boolean {
  if (!value || data.label.includes(value)) return true
  // Keep the grades visible when searching for their department.
  type Ancestor = { data?: { label?: string }; parent?: Ancestor | null }
  let parent = (node as Ancestor).parent
  while (parent) {
    if (parent.data?.label?.includes(value)) return true
    parent = parent.parent
  }
  return false
}
</script>

<template>
  <aside class="classification-panel card">
    <h3>{{ title }}</h3>
    <el-input v-model="search" placeholder="搜索分类" clearable @input="tree?.filter(search)" />
    <el-tree ref="tree" :data="nodes" node-key="key" :current-node-key="selectedKey"
      :default-expanded-keys="['all', ...(nodes[0]?.children?.length === 1 ? [nodes[0].children[0].key] : [])]" :expand-on-click-node="true" highlight-current
      :filter-node-method="filter" @node-click="(node: HierarchyNode) => emit('select', node)" />
    <p>{{ hint ?? '部门下按职级查找，原始岗位保持不变。分类不自动授予权限。' }}</p>
  </aside>
</template>

<style scoped>
.classification-panel{width:270px;flex-shrink:0;align-self:flex-start;position:sticky;top:12px;padding:16px;max-height:calc(100vh - 150px);overflow:auto}
h3{margin:0 0 12px;font-size:15px}.el-tree{margin-top:12px;background:transparent}p{color:var(--rbac-text-secondary);font-size:12px;margin:16px 0 0}
:deep(.el-tree-node__label){overflow:hidden;text-overflow:ellipsis;font-size:13px}
@media(max-width:900px){.classification-panel{width:100%;position:static;max-height:320px}}
</style>
