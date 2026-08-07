<template>
  <div class="result-detail">
    <Sidebar />
    <main class="result-detail__content">
      <router-link to="/results" class="result-detail__back">← 返回结果列表</router-link>

      <template v-if="result">
        <header class="result-detail__header">
          <h1 class="result-detail__title">{{ result.targetName }}</h1>
          <p class="result-detail__subtitle">{{ result.targetId }}</p>
        </header>

        <section class="result-detail__metrics">
          <div class="result-detail__metric">
            <span class="result-detail__metric-label">结合亲和力</span>
            <span class="result-detail__metric-value">{{ result.bindingAffinity.toFixed(2) }} kcal/mol</span>
          </div>
          <div class="result-detail__metric">
            <span class="result-detail__metric-label">置信度</span>
            <span class="result-detail__metric-value">{{ Math.round(result.confidenceScore * 100) }}%</span>
          </div>
        </section>

        <section v-if="result.interactions.length" class="result-detail__interactions">
          <h2 class="result-detail__section-title">相互作用</h2>
          <div
            v-for="(inter, idx) in result.interactions"
            :key="idx"
            class="result-detail__interaction"
          >
            <span>{{ inter.type }}</span>
            <span>{{ inter.residueName }} {{ inter.residueNumber }}</span>
            <span>{{ inter.distance }} Å</span>
          </div>
        </section>

        <div class="result-detail__actions">
          <button class="result-detail__btn" @click="goToVisualization">查看3D结构</button>
        </div>
      </template>

      <div v-else class="result-detail__empty">
        <span>未找到预测结果</span>
        <router-link to="/results">返回结果列表</router-link>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import Sidebar from '@/components/Sidebar.vue'
import type { PredictionResult } from '@/types'

const router = useRouter()
const route = useRoute()

const resultId = computed(() => {
  return (route.params.id as string) || (route.query.id as string) || ''
})

const result = computed<PredictionResult | null>(() => {
  if (!resultId.value && !route.query.targetId && !route.query.targetName) {
    return null
  }
  try {
    return {
      id: resultId.value || (route.query.id as string) || '',
      targetId: (route.query.targetId as string) || '',
      targetName: (route.query.targetName as string) || '',
      ligandSmiles: '',
      bindingAffinity: parseFloat(route.query.bindingAffinity as string) || 0,
      confidenceScore: parseFloat(route.query.confidenceScore as string) || 0,
      confidenceLevel: (route.query.confidenceLevel as PredictionResult['confidenceLevel']) || 'medium',
      interactions: [],
      createdAt: new Date().toISOString(),
      datasetInfo: { name: '', size: 0, description: '', source: 'internal' as const },
    }
  } catch {
    return null
  }
})

const goToVisualization = () => {
  if (!result.value) return
  router.push({
    path: '/visualization',
    query: {
      id: String(result.value.id),
      targetName: result.value.targetName,
      targetId: result.value.targetId,
    },
  })
}
</script>

<style lang="scss" scoped>
.result-detail {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
}

.result-detail__content {
  flex: 1;
  margin-left: $sidebar-width;
  padding: $spacing-lg $spacing-xl;
}

.result-detail__back {
  display: inline-block;
  color: $text-secondary;
  text-decoration: none;
  font-size: $font-size-sm;
  margin-bottom: $spacing-lg;

  &:hover {
    color: $primary-color;
  }
}

.result-detail__header {
  margin-bottom: $spacing-xl;
}

.result-detail__title {
  font-size: 24px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-xs;
}

.result-detail__subtitle {
  font-size: $font-size-sm;
  color: $text-muted;
}

.result-detail__metrics {
  display: flex;
  gap: $spacing-lg;
  margin-bottom: $spacing-xl;
}

.result-detail__metric {
  background: $bg-primary;
  border: 1px solid $border-light;
  border-radius: $border-radius-lg;
  padding: $spacing-lg;
  flex: 1;
}

.result-detail__metric-label {
  display: block;
  font-size: $font-size-xs;
  color: $text-muted;
  margin-bottom: $spacing-xs;
}

.result-detail__metric-value {
  font-size: $font-size-xl;
  font-weight: 600;
  color: $text-primary;
}

.result-detail__interactions {
  margin-bottom: $spacing-xl;
}

.result-detail__section-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

.result-detail__interaction {
  display: flex;
  gap: $spacing-lg;
  padding: $spacing-sm $spacing-md;
  background: $bg-primary;
  border: 1px solid $border-light;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  color: $text-secondary;

  & + & {
    margin-top: $spacing-xs;
  }
}

.result-detail__actions {
  padding-top: $spacing-lg;
  border-top: 1px solid $border-light;
}

.result-detail__btn {
  padding: $spacing-sm $spacing-lg;
  background: $primary-color;
  color: #ffffff;
  border: none;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  font-weight: 500;
  cursor: pointer;

  &:hover {
    background: $primary-dark;
  }
}

.result-detail__empty {
  text-align: center;
  padding: $spacing-3xl;
  color: $text-muted;
  font-size: $font-size-base;

  a {
    display: inline-block;
    margin-top: $spacing-md;
    color: $primary-color;
    text-decoration: none;
    font-weight: 500;
  }
}
</style>
