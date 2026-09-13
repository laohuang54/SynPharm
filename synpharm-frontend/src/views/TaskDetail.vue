<template>
  <div class="task-detail">
    <Sidebar />
    <main class="task-detail__content">
      <router-link to="/tasks" class="task-detail__back">← 返回任务列表</router-link>

      <div v-if="loading" class="task-detail__state">加载中…</div>
      <div v-else-if="error" class="task-detail__state task-detail__state--error">{{ error }}</div>
      <div v-else-if="!task" class="task-detail__state">未找到该任务</div>

      <el-card v-else class="task-detail__card" shadow="never">
        <template #header>
          <div class="task-detail__header">
            <h1 class="task-detail__title">任务详情</h1>
            <el-tag :type="getStatusTagType(task.status)" effect="light">
              {{ getStatusText(task.status) }}
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="1" border>
          <el-descriptions-item label="任务 ID">{{ task.id }}</el-descriptions-item>
          <el-descriptions-item v-if="task.taskNo" label="任务编号">{{ task.taskNo }}</el-descriptions-item>
          <el-descriptions-item label="算法类型">{{ getTypeText(task.predictType) }}</el-descriptions-item>
          <el-descriptions-item v-if="task.progress != null" label="进度">{{ task.progress }}%</el-descriptions-item>
          <el-descriptions-item v-if="task.createdAt" label="创建时间">{{ formatDate(task.createdAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="task.startedAt" label="开始时间">{{ formatDate(task.startedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="task.completedAt" label="完成时间">{{ formatDate(task.completedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="task.inputType" label="输入类型">{{ task.inputType }}</el-descriptions-item>
          <el-descriptions-item v-if="task.inputValue" label="输入参数">{{ task.inputValue }}</el-descriptions-item>
          <el-descriptions-item v-if="task.fileUrl" label="文件地址">{{ task.fileUrl }}</el-descriptions-item>
          <el-descriptions-item v-if="task.errorMessage" label="错误信息">
            <span class="task-detail__error">{{ task.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getTask } from '@/api/task'
import Sidebar from '@/components/Sidebar.vue'
import type { TaskDetail } from '@/types'

const route = useRoute()

const task = ref<TaskDetail | null>(null)
const loading = ref(false)
const error = ref('')

async function loadTask(): Promise<void> {
  const id = route.params.id as string
  if (!id) {
    task.value = null
    error.value = ''
    loading.value = false
    return
  }

  loading.value = true
  error.value = ''

  try {
    task.value = await getTask(id)
  } catch (err) {
    task.value = null
    error.value = err instanceof Error ? err.message : '加载任务详情失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.id,
  () => {
    loadTask()
  },
  { immediate: true }
)

const getStatusText = (status?: string): string => {
  const texts: Record<string, string> = {
    completed: '已完成',
    running: '运行中',
    pending: '待处理',
    failed: '失败',
    cancelled: '已取消'
  }
  return (status && texts[status]) || status || ''
}

const getStatusTagType = (status?: string): 'success' | 'warning' | 'danger' | 'info' => {
  switch (status) {
    case 'completed':
      return 'success'
    case 'running':
      return 'warning'
    case 'failed':
      return 'danger'
    default:
      return 'info'
  }
}

const getTypeText = (type?: string): string => {
  const texts: Record<string, string> = {
    dti: '药物-靶点',
    ppi: '蛋白-蛋白',
    ddi: '药物-药物'
  }
  return (type && texts[type]) || type || '-'
}

const formatDate = (dateString?: string): string => {
  if (!dateString) return '-'
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) return dateString
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}
</script>

<style lang="scss" scoped>
.task-detail {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
  padding-top: $header-height;
}

.task-detail__content {
  flex: 1;
  padding: $spacing-xl;
  max-width: 860px;
}

.task-detail__back {
  display: inline-block;
  color: $text-secondary;
  text-decoration: none;
  font-size: $font-size-sm;
  margin-bottom: $spacing-lg;

  &:hover {
    color: $primary-color;
  }
}

.task-detail__state {
  padding: $spacing-3xl;
  text-align: center;
  color: $text-muted;
  font-size: $font-size-base;

  &--error {
    color: $error-color;
  }
}

.task-detail__card {
  border-radius: $border-radius-lg;
}

.task-detail__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.task-detail__title {
  font-size: $font-size-xl;
  font-weight: 600;
  color: $text-primary;
  margin: 0;
}

.task-detail__error {
  color: $error-color;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
