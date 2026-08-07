<template>
  <div class="db">
    <Sidebar />
    <main class="db__main">
      <header class="db__header">
        <div>
          <h1 class="db__title">仪表盘</h1>
          <p class="db__subtitle">欢迎回来，{{ authStore.userNickname }}</p>
        </div>
        <span class="db__date">{{ currentDate }}</span>
      </header>

      <section class="db__stats">
        <div class="db__stat">
          <span class="db__stat-icon">📊</span>
          <div class="db__stat-info">
            <b class="db__stat-value">{{ stats.totalTasks }}</b>
            <span class="db__stat-label">总任务数</span>
          </div>
        </div>
        <div class="db__stat">
          <span class="db__stat-icon">✅</span>
          <div class="db__stat-info">
            <b class="db__stat-value">{{ stats.completedTasks }}</b>
            <span class="db__stat-label">已完成</span>
          </div>
        </div>
        <div class="db__stat">
          <span class="db__stat-icon">⏳</span>
          <div class="db__stat-info">
            <b class="db__stat-value">{{ stats.runningTasks }}</b>
            <span class="db__stat-label">运行中</span>
          </div>
        </div>
        <div class="db__stat">
          <span class="db__stat-icon">🎯</span>
          <div class="db__stat-info">
            <b class="db__stat-value">{{ stats.averageConfidence }}%</b>
            <span class="db__stat-label">平均置信度</span>
          </div>
        </div>
      </section>

      <section class="db__sections">
        <div class="db__card">
          <div class="db__card-head">
            <h2 class="db__card-title">最近任务</h2>
            <router-link to="/tasks" class="db__link">查看全部</router-link>
          </div>
          <div class="db__tasks">
            <div v-for="task in recentTasks" :key="task.id" class="db__task">
              <div class="db__task-info">
                <span class="db__task-name">{{ task.name || task.id }}</span>
                <span class="db__task-type">{{ task.type }}</span>
              </div>
              <span class="db__status" :class="`db__status--${task.status}`">{{ getStatusText(task.status) }}</span>
            </div>
          </div>
        </div>

        <div class="db__card">
          <div class="db__card-head">
            <h2 class="db__card-title">最近结果</h2>
            <router-link to="/results" class="db__link">查看全部</router-link>
          </div>
          <div class="db__results">
            <ResultCard
              v-for="result in recentResults"
              :key="result.id"
              :result="result"
              @detail="handleResultDetail"
              @3d="handleResult3D"
            />
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { mockTasks, mockResults } from '@/data/mockResults'
import Sidebar from '@/components/Sidebar.vue'
import ResultCard from '@/components/ResultCard.vue'
import type { PredictionResult, Task } from '@/types'

const authStore = useAuthStore()
const router = useRouter()

const currentDate = computed(() => {
  return new Date().toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
})

const stats = computed(() => {
  const total = mockTasks.length
  const completed = mockTasks.filter((t: Task) => t.status === 'completed').length
  const running = mockTasks.filter((t: Task) => t.status === 'running').length
  const avgConfidence = mockResults.length > 0 
    ? Math.round(mockResults.reduce((sum: number, r: PredictionResult) => sum + r.confidenceScore, 0) / mockResults.length * 100)
    : 0
  
  return {
    totalTasks: total,
    completedTasks: completed,
    runningTasks: running,
    averageConfidence: avgConfidence
  }
})

const recentTasks = computed(() => {
  return [...mockTasks].sort((a, b) => 
    new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  ).slice(0, 5)
})

const recentResults = computed(() => {
  return [...mockResults].sort((a, b) => 
    new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  ).slice(0, 2)
})

const getStatusText = (status: string): string => {
  const texts: Record<string, string> = {
    completed: '已完成',
    running: '运行中',
    pending: '待处理',
    failed: '失败'
  }
  return texts[status] || status
}

const handleResultDetail = (result: PredictionResult) => {
  router.push({
    path: '/result/' + String(result.id),
    query: {
      id: String(result.id),
      targetId: result.targetId || '',
      targetName: result.targetName || '',
      bindingAffinity: String(result.bindingAffinity ?? ''),
      confidenceScore: String(result.confidenceScore ?? ''),
      confidenceLevel: result.confidenceLevel || '',
    },
  })
}

const handleResult3D = (result: PredictionResult) => {
  router.push({
    path: '/visualization',
    query: {
      id: String(result.id),
      targetName: result.targetName || '',
      targetId: result.targetId || '',
    },
  })
}
</script>

<style lang="scss" scoped>
.dashboard {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
  padding-top: $header-height;
}

.dashboard__content {
  flex: 1;
  padding: $spacing-lg $spacing-xl;
}

.dashboard__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-xl;
}

.dashboard__title {
  font-size: 28px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-xs;
  letter-spacing: -0.3px;
}

.dashboard__subtitle {
  font-size: $font-size-sm;
  color: $text-muted;
}

.dashboard__date {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.dashboard__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-lg;
  margin-bottom: $spacing-xl;
}

.dashboard__stat-card {
  background: $bg-primary;
  padding: $spacing-lg;
  border-radius: $border-radius-lg;
  display: flex;
  align-items: center;
  gap: $spacing-md;
  border: 1px solid $border-light;
  transition: all $transition-fast;
  
  &:hover {
    box-shadow: $shadow-sm;
  }
}

.dashboard__stat-icon {
  font-size: 36px;
}

.dashboard__stat-info {
  display: flex;
  flex-direction: column;
}

.dashboard__stat-value {
  font-size: 28px;
  font-weight: 700;
  color: $primary-color;
  letter-spacing: -0.5px;
}

.dashboard__stat-label {
  font-size: $font-size-xs;
  color: $text-muted;
  text-transform: uppercase;
  letter-spacing: 0.3px;
  margin-top: 2px;
}

.dashboard__main {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: $spacing-xl;
}

.dashboard__section {
  background: $bg-primary;
  border-radius: $border-radius-lg;
  padding: $spacing-xl;
  border: 1px solid $border-light;
}

.dashboard__section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
}

.dashboard__section-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
}

.dashboard__section-link {
  font-size: $font-size-xs;
  color: $accent-color;
  text-decoration: none;
  font-weight: 500;
  
  &:hover {
    text-decoration: underline;
  }
}

.dashboard__tasks {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.dashboard__task-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-md;
  background: $bg-secondary;
  border-radius: $border-radius-md;
  transition: all $transition-fast;
  
  &:hover {
    background: $bg-tertiary;
  }
}

.dashboard__task-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dashboard__task-name {
  font-size: $font-size-sm;
  font-weight: 500;
  color: $text-primary;
}

.dashboard__task-type {
  font-size: $font-size-xs;
  color: $text-muted;
}

.dashboard__task-status {
  display: flex;
  align-items: center;
}

.dashboard__status-badge {
  font-size: $font-size-xs;
  font-weight: 500;
  padding: 4px 12px;
  border-radius: 100px;
  
  &--completed {
    background: rgba($success-color, 0.1);
    color: $success-color;
  }
  
  &--running {
    background: rgba($accent-color, 0.1);
    color: $accent-color;
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

.dashboard__results {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;
}
</style>

<style lang="scss" scoped>
/* ===================== 仪表盘（新风格） ===================== */
.db {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
  padding-top: $header-height;
}

.db__main {
  flex: 1;
  padding: $spacing-xl;
  max-width: 1100px;
}

.db__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: $spacing-lg;
}

.db__title {
  font-size: $font-size-2xl;
  font-weight: 700;
  color: $text-primary;
}

.db__subtitle {
  margin-top: $spacing-xs;
  font-size: $font-size-sm;
  color: $text-muted;
}

.db__date {
  font-size: $font-size-sm;
  color: $text-secondary;
  background: $bg-tertiary;
  padding: $spacing-xs $spacing-md;
  border-radius: 999px;
}

.db__stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
}

.db__stat {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  background: $bg-primary;
  border: 1px solid $border-color;
  border-radius: $border-radius-lg;
  box-shadow: $shadow-sm;
}

.db__stat-icon {
  font-size: $font-size-2xl;
}

.db__stat-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.db__stat-value {
  font-size: $font-size-2xl;
  font-weight: 700;
  color: $text-primary;
}

.db__stat-label {
  font-size: $font-size-xs;
  color: $text-muted;
}

.db__sections {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: $spacing-lg;
  align-items: start;
}

.db__card {
  background: $bg-primary;
  border: 1px solid $border-color;
  border-radius: $border-radius-lg;
  padding: $spacing-lg;
  box-shadow: $shadow-sm;
}

.db__card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-md;
}

.db__card-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
}

.db__link {
  font-size: $font-size-sm;
  color: $accent-color;
  text-decoration: none;
  &:hover { text-decoration: underline; }
}

.db__tasks {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.db__task {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-sm $spacing-md;
  background: $bg-secondary;
  border-radius: $border-radius-md;
}

.db__task-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.db__task-name {
  font-size: $font-size-sm;
  font-weight: 500;
  color: $text-primary;
}

.db__task-type {
  font-size: $font-size-xs;
  color: $text-muted;
}

.db__status {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: $font-size-xs;
  font-weight: 500;
  &--completed { background: rgba(16, 185, 129, 0.12); color: $success-color; }
  &--running { background: rgba(59, 130, 246, 0.12); color: $info-color; }
  &--pending { background: rgba(148, 163, 184, 0.15); color: $text-muted; }
  &--failed { background: rgba(239, 68, 68, 0.12); color: $error-color; }
}

.db__results {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}
</style>