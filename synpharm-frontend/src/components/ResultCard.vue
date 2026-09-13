<template>
  <div class="result-card">
    <div class="result-card__header">
      <div class="result-card__target">
        <span class="result-card__target-name">{{ result.targetName }}</span>
        <span class="result-card__target-id">{{ result.targetId }}</span>
      </div>
      <span class="result-card__confidence" :class="`result-card__confidence--${result.confidenceLevel}`">
        {{ getConfidenceText(result.confidenceLevel) }}
      </span>
    </div>
    
    <div class="result-card__main">
      <div class="result-card__affinity">
        <span class="result-card__affinity-label">结合亲和力</span>
        <div class="result-card__affinity-value-wrap">
          <span class="result-card__affinity-value">{{ result.bindingAffinity?.toFixed?.(2) ?? '-' }}</span>
          <span class="result-card__affinity-unit">kcal/mol</span>
        </div>
      </div>
      
      <div class="result-card__confidence-section">
        <span class="result-card__confidence-label">置信度</span>
        <div class="result-card__confidence-bar">
          <div class="result-card__confidence-track">
            <div 
              class="result-card__confidence-fill" 
              :style="{
                width: ((result.confidenceScore ?? 0) * 100) + '%',
                background: getConfidenceColor(result.confidenceScore ?? 0)
              }"
            ></div>
          </div>
          <span class="result-card__confidence-value" :style="{ color: getConfidenceColor(result.confidenceScore ?? 0) }">
            {{ Math.round((result.confidenceScore ?? 0) * 100) }}%
          </span>
        </div>
      </div>
    </div>
    
    <div class="result-card__interactions">
      <span class="result-card__interactions-label">相互作用类型</span>
      <div class="result-card__interaction-tags">
        <span 
          v-for="(interaction, index) in (result.interactions || []).slice(0, 4)" 
          :key="index"
          class="result-card__interaction-tag"
          :style="{ background: getInteractionColor(interaction.type) }"
        >
          {{ getInteractionTypeName(interaction.type) }}
        </span>
        <span v-if="(result.interactions || []).length > 4" class="result-card__interaction-tag result-card__interaction-tag--more">
          +{{ (result.interactions || []).length - 4 }}
        </span>
      </div>
    </div>
    
    <div class="result-card__footer">
      <span class="result-card__date">{{ formatDate(result.createdAt) }}</span>
      <div class="result-card__actions">
        <button class="result-card__action-btn" @click="$emit('detail', result)">详情</button>
        <button class="result-card__action-btn result-card__action-btn--primary" @click="$emit('3d', result)">3D</button>
        <el-button type="danger" size="small" @click="$emit('delete', result)">删除</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PredictionResult } from '@/types'

defineProps<{
  result: PredictionResult
}>()

defineEmits<{
  (e: 'detail', result: PredictionResult): void
  (e: '3d', result: PredictionResult): void
  (e: 'delete', result: PredictionResult): void
}>()

const getConfidenceColor = (score: number): string => {
  if (score >= 0.8) return '#10b981'
  if (score >= 0.6) return '#f59e0b'
  return '#ef4444'
}

const getConfidenceText = (level: string): string => {
  const texts: Record<string, string> = {
    high: '高',
    medium: '中',
    low: '低'
  }
  return texts[level] || level
}

const getInteractionColor = (type: string): string => {
  const colors: Record<string, string> = {
    hydrogen_bond: 'rgba(16, 185, 129, 0.12)',
    hydrophobic: 'rgba(245, 158, 11, 0.12)',
    ionic: 'rgba(239, 68, 68, 0.12)',
    pi_pi: 'rgba(139, 92, 246, 0.12)',
    metal: 'rgba(59, 130, 246, 0.12)'
  }
  return colors[type] || 'rgba(148, 163, 184, 0.12)'
}

const getInteractionTypeName = (type: string): string => {
  const types: Record<string, string> = {
    hydrogen_bond: '氢键',
    hydrophobic: '疏水',
    ionic: '离子',
    pi_pi: 'π-π',
    metal: '金属'
  }
  return types[type] || type
}

const formatDate = (dateString: string): string => {
  const date = new Date(dateString)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)
  
  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  if (diffHours < 24) return `${diffHours}小时前`
  if (diffDays < 7) return `${diffDays}天前`
  
  return date.toLocaleDateString('zh-CN')
}
</script>

<style lang="scss" scoped>
.result-card {
  background: $bg-primary;
  border-radius: $border-radius-lg;
  padding: $spacing-lg;
  border: 1px solid $border-light;
  transition: all $transition-normal;
  
  &:hover {
    box-shadow: $shadow-md;
    border-color: $border-color;
  }
}

.result-card__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: $spacing-lg;
}

.result-card__target {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.result-card__target-name {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
}

.result-card__target-id {
  font-size: $font-size-xs;
  color: $text-muted;
  font-family: monospace;
}

.result-card__confidence {
  font-size: $font-size-xs;
  font-weight: 600;
  padding: 4px 12px;
  border-radius: 100px;
  
  &--high {
    background: rgba($success-color, 0.1);
    color: $success-color;
  }
  
  &--medium {
    background: rgba($warning-color, 0.1);
    color: $warning-color;
  }
  
  &--low {
    background: rgba($error-color, 0.1);
    color: $error-color;
  }
}

.result-card__main {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-lg;
  margin-bottom: $spacing-lg;
  padding: $spacing-lg;
  background: $bg-secondary;
  border-radius: $border-radius-md;
}

.result-card__affinity {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
}

.result-card__affinity-label {
  font-size: $font-size-xs;
  color: $text-muted;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.result-card__affinity-value-wrap {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.result-card__affinity-value {
  font-size: 28px;
  font-weight: 700;
  color: $primary-color;
  letter-spacing: -0.5px;
}

.result-card__affinity-unit {
  font-size: $font-size-xs;
  color: $text-muted;
}

.result-card__confidence-section {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.result-card__confidence-label {
  font-size: $font-size-xs;
  color: $text-muted;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.result-card__confidence-bar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.result-card__confidence-track {
  flex: 1;
  height: 6px;
  background: $bg-tertiary;
  border-radius: 3px;
  overflow: hidden;
}

.result-card__confidence-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.result-card__confidence-value {
  font-size: $font-size-sm;
  font-weight: 600;
  min-width: 40px;
  text-align: right;
}

.result-card__interactions {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  margin-bottom: $spacing-lg;
}

.result-card__interactions-label {
  font-size: $font-size-xs;
  color: $text-muted;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.result-card__interaction-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.result-card__interaction-tag {
  padding: 3px 10px;
  border-radius: $border-radius-sm;
  font-size: $font-size-xs;
  color: $text-secondary;
  
  &--more {
    background: $bg-secondary;
    color: $text-muted;
  }
}

.result-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: $spacing-md;
  border-top: 1px solid $border-light;
}

.result-card__date {
  font-size: $font-size-xs;
  color: $text-muted;
}

.result-card__actions {
  display: flex;
  gap: $spacing-sm;
}

.result-card__action-btn {
  padding: $spacing-xs $spacing-md;
  background: transparent;
  color: $text-secondary;
  border: 1px solid $border-color;
  border-radius: $border-radius-sm;
  font-size: $font-size-xs;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-fast;
  
  &:hover {
    background: $bg-secondary;
    border-color: $text-muted;
  }
  
  &--primary {
    background: $primary-color;
    color: #ffffff;
    border-color: $primary-color;
    
    &:hover {
      background: $primary-dark;
      border-color: $primary-dark;
    }
  }
}
</style>