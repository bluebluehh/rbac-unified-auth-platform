<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="error-page">
    <el-result icon="info" title="页面不存在" sub-title="请检查访问地址，或返回应用门户重新进入">
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
