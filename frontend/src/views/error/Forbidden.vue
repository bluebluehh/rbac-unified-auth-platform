<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="error-page">
    <el-result icon="warning" title="无法访问" sub-title="当前账号没有访问该页面的权限">
      <template #extra>
        <el-button type="primary" @click="router.replace('/portal')">返回门户</el-button>
      </template>
    </el-result>
  </div>
</template>

<style scoped lang="scss">
.error-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
