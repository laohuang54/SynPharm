<template>
  <div class="tk">
    <Sidebar />
    <main class="tk__main">
      <header class="tk__header">
        <div>
          <h1 class="tk__title">任务管理</h1>
          <p class="tk__subtitle">查看和管理所有预测任务</p>
        </div>
        <button class="tk__btn tk__btn--primary" @click="goCreate">创建任务</button>
      </header>

      <div class="tk__tabs">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          @click="activeTab = tab.value"
          class="tk__tab"
          :class="{ 'tk__tab--active': activeTab === tab.value }"
        >
          {{ tab.label }}
          <span v-if="tab.count > 0" class="tk__tab-count">{{ tab.count }}</span>
        </button>
      </div>

      <section class="tk__list">
        <div v-if="loading" class="tk__state">加载中...</div>
        <div v-else-if="loadError" class="tk__state tk__state--error">{{ loadError }}</div>
        <template v-else>
          <div v-for="task in filteredTasks" :key="task.id" class="tk__card">
            <div class="tk__card-head">
              <div class="tk__card-info">
                <span class="tk__card-name">{{ task.taskNo || task.name || task.id }}</span>
                <span class="tk__card-type">{{ getTypeText(task.predictType || task.type || '') }}</span>
              </div>
              <span class="tk__status" :class="`tk__status--${task.status}`">{{ getStatusText(task.status) }}</span>
            </div>

            <div v-if="task.status === 'running'" class="tk__progress">
              <div class="tk__progress-track">
                <div class="tk__progress-fill" :style="{ width: task.progress + '%' }"></div>
              </div>
              <span class="tk__progress-text">{{ task.progress }}%</span>
            </div>

            <div class="tk__card-foot">
              <span class="tk__date">{{ formatDate(task.createdAt) }}</span>
              <div class="tk__actions">
                <button v-if="task.status === 'running'" class="tk__action" @click="handlePause(task)">暂停</button>
                <button class="tk__action" @click="handleView(task)">查看任务</button>
                <button v-if="task.status === 'running' || task.status === 'pending'" class="tk__action tk__action--danger" @click="handleCancel(task)">取消任务</button>
              </div>
            </div>
          </div>

          <div v-if="filteredTasks.length === 0" class="tk__empty">
            <span class="tk__empty-icon">📋</span>
            <span class="tk__empty-text">暂无{{ getTabLabel(activeTab) }}任务</span>
          </div>
        </template>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTasks, cancelTask } from '@/api/task'
import Sidebar from '@/components/Sidebar.vue'
import type { Task } from '@/types'

const router = useRouter()

const activeTab = ref('all')
const tasks = ref<Task[]>([])
const loading = ref(false)
const loadError = ref('')

const loadTasks = async () => {
  loading.value = true
  loadError.value = ''
  try {
    tasks.value = await getTasks()
  } catch (error: unknown) {
    loadError.value = error instanceof Error ? error.message : '加载任务失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadTasks)

const goCreate = () => {
  router.push('/predict')
}

const tabs = computed(() => [
  { value: 'all', label: '全部', count: tasks.value.length },
  { value: 'running', label: '运行中', count: tasks.value.filter((t: Task) => t.status === 'running').length },
  { value: 'pending', label: '待处理', count: tasks.value.filter((t: Task) => t.status === 'pending').length },
  { value: 'completed', label: '已完成', count: tasks.value.filter((t: Task) => t.status === 'completed').length },
  { value: 'failed', label: '失败', count: tasks.value.filter((t: Task) => t.status === 'failed').length }
])

const filteredTasks = computed(() => {
  if (activeTab.value === 'all') {
    return tasks.value
  }
  return tasks.value.filter((t: Task) => t.status === activeTab.value)
})

const getStatusText = (status: string): string => {
  const texts: Record<string, string> = {
    completed: '已完成',
    running: '运行中',
    pending: '待处理',
    failed: '失败',
    cancelled: '已取消'
  }
  return texts[status] || status
}

const getTypeText = (type: string): string => {
  const texts: Record<string, string> = {
    dti: '药物-靶点',
    ppi: '蛋白-蛋白',
    ddi: '药物-药物'
  }
  return texts[type] || type
}

const getTabLabel = (tab: string): string => {
  const labels: Record<string, string> = {
    all: '',
    running: '运行中',
    pending: '待处理',
    completed: '已完成',
    failed: '失败'
  }
  return labels[tab] || ''
}

const formatDate = (dateString: string): string => {
  const date = new Date(dateString)
  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const handlePause = (task: Task) => {
  console.log('暂停任务:', task.id)
}

const handleView = (task: Task) => {
  router.push(`/tasks/${task.id}`)
}

const handleCancel = async (task: Task) => {
  try {
    await ElMessageBox.confirm('确定取消该预测任务吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await cancelTask(task.id)
    ElMessage.success('任务取消成功')
    await loadTasks()
  } catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : '取消任务失败')
  }
}
</script>

<style lang="scss" scoped>
.tasks {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
  padding-top: $header-height;
}

.tasks__content {
  flex: 1;
  padding: $spacing-lg;
}

.tasks__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-xl;
}

.tasks__title {
  font-size: 24px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-xs;
}

.tasks__subtitle {
  font-size: $font-size-sm;
  color: $text-muted;
}

.tasks__btn {
  padding: $spacing-sm $spacing-lg;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all $transition-fast;
  
  &--primary {
    background: $primary-color;
    color: #ffffff;
    
    &:hover {
      background: $primary-dark;
    }
  }
}

.tasks__tabs {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-xl;
}

.tasks__tab {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  background: $bg-primary;
  border: 1px solid $border-light;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  color: $text-secondary;
  cursor: pointer;
  transition: all $transition-fast;
  
  &:hover {
    border-color: $border-color;
  }
  
  &--active {
    background: $primary-color;
    color: #ffffff;
    border-color: $primary-color;
  }
}

.tasks__tab-count {
  font-size: $font-size-xs;
  padding: 1px 6px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 100px;
}

.tasks__list {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.tasks__card {
  background: $bg-primary;
  padding: $spacing-lg;
  border-radius: $border-radius-lg;
  border: 1px solid $border-light;
}

.tasks__card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: $spacing-md;
}

.tasks__card-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.tasks__card-name {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
}

.tasks__card-type {
  font-size: $font-size-xs;
  color: $text-muted;
}

.tasks__status {
  font-size: $font-size-xs;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 100px;
  
  &--completed {
    background: rgba($success-color, 0.1);
    color: $success-color;
  }
  
  &--running {
    background: rgba($info-color, 0.1);
    color: $info-color;
  }
  
  &--pending {
    background: rgba($warning-color, 0.1);
    color: $warning-color;
  }
  
  &--failed {
    background: rgba($error-color, 0.1);
    color: $error-color;
  }
}

.tasks__progress {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-md;
}

.tasks__progress-track {
  flex: 1;
  height: 6px;
  background: $bg-secondary;
  border-radius: 3px;
  overflow: hidden;
}

.tasks__progress-fill {
  height: 100%;
  background: $primary-color;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.tasks__progress-text {
  font-size: $font-size-xs;
  color: $text-muted;
  width: 40px;
  text-align: right;
}

.tasks__card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: $spacing-md;
  border-top: 1px solid $border-light;
}

.tasks__card-date {
  font-size: $font-size-xs;
  color: $text-muted;
}

.tasks__card-actions {
  display: flex;
  gap: $spacing-sm;
}

.tasks__action-btn {
  padding: $spacing-xs $spacing-md;
  background: transparent;
  border: 1px solid $border-color;
  border-radius: $border-radius-sm;
  font-size: $font-size-xs;
  color: $text-secondary;
  cursor: pointer;
  transition: all $transition-fast;
  
  &:hover {
    background: $bg-secondary;
  }
}

.tasks__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl;
}

.tasks__empty-icon {
  font-size: 48px;
  margin-bottom: $spacing-md;
}

.tasks__empty-text {
  font-size: $font-size-base;
  color: $text-muted;
}
</style>

<style lang="scss" scoped>
/* ===================== 任务管理（新风格） ===================== */
.tk {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
  padding-top: $header-height;
}

.tk__main {
  flex: 1;
  padding: $spacing-xl;
  max-width: 900px;
}

.tk__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: $spacing-lg;
}

.tk__title {
  font-size: $font-size-2xl;
  font-weight: 700;
  color: $text-primary;
}

.tk__subtitle {
  margin-top: $spacing-xs;
  font-size: $font-size-sm;
  color: $text-muted;
}

.tk__btn {
  padding: $spacing-sm $spacing-lg;
  border: none;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  cursor: pointer;
  transition: $transition-fast;
  &--primary {
    background: $primary-color;
    color: #fff;
    &:hover { background: $primary-light; }
  }
}

.tk__tabs {
  display: inline-flex;
  background: $bg-tertiary;
  border-radius: $border-radius-md;
  padding: 4px;
  gap: 4px;
  margin-bottom: $spacing-lg;
}

.tk__tab {
  padding: $spacing-sm $spacing-lg;
  border: none;
  background: transparent;
  border-radius: $border-radius-sm;
  font-size: $font-size-sm;
  color: $text-secondary;
  cursor: pointer;
  transition: $transition-fast;
  &:hover { color: $text-primary; }
  &--active {
    background: $bg-primary;
    color: $primary-color;
    font-weight: 600;
    box-shadow: $shadow-sm;
  }
}

.tk__tab-count {
  margin-left: 4px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.15);
  color: $accent-color;
  font-size: $font-size-xs;
}

.tk__list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.tk__state {
  padding: $spacing-2xl;
  text-align: center;
  color: $text-muted;
  &--error { color: $error-color; }
}

.tk__card {
  background: $bg-primary;
  border: 1px solid $border-color;
  border-radius: $border-radius-lg;
  padding: $spacing-md $spacing-lg;
  box-shadow: $shadow-sm;
}

.tk__card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tk__card-info {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.tk__card-name {
  font-size: $font-size-base;
  font-weight: 600;
  color: $text-primary;
}

.tk__card-type {
  padding: 2px 8px;
  background: $bg-tertiary;
  border-radius: 999px;
  font-size: $font-size-xs;
  color: $text-secondary;
}

.tk__status {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: $font-size-xs;
  font-weight: 500;
  &--completed { background: rgba(16, 185, 129, 0.12); color: $success-color; }
  &--running { background: rgba(59, 130, 246, 0.12); color: $info-color; }
  &--pending { background: rgba(148, 163, 184, 0.15); color: $text-muted; }
  &--failed { background: rgba(239, 68, 68, 0.12); color: $error-color; }
  &--cancelled { background: rgba(148, 163, 184, 0.15); color: $text-muted; }
}

.tk__progress {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-top: $spacing-md;
}

.tk__progress-track {
  flex: 1;
  height: 6px;
  background: $border-color;
  border-radius: 999px;
  overflow: hidden;
}

.tk__progress-fill {
  height: 100%;
  background: linear-gradient(90deg, $accent-color, $accent-light);
  border-radius: 999px;
  transition: width 0.3s;
}

.tk__progress-text {
  font-size: $font-size-xs;
  color: $text-muted;
}

.tk__card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: $spacing-md;
  padding-top: $spacing-sm;
  border-top: 1px solid $border-light;
}

.tk__date {
  font-size: $font-size-xs;
  color: $text-muted;
}

.tk__actions {
  display: flex;
  gap: $spacing-sm;
}

.tk__action {
  border: none;
  background: transparent;
  color: $accent-color;
  font-size: $font-size-xs;
  cursor: pointer;
  padding: $spacing-xs $spacing-sm;
  border-radius: $border-radius-sm;
  transition: $transition-fast;
  &:hover { background: rgba(59, 130, 246, 0.08); }
  &--danger {
    color: $error-color;
    &:hover { background: rgba(239, 68, 68, 0.08); }
  }
}

.tk__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-3xl;
  color: $text-muted;
}

.tk__empty-icon {
  font-size: 40px;
  margin-bottom: $spacing-md;
}

.tk__empty-text {
  font-size: $font-size-base;
}
</style>