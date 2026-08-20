<template>
  <div class="tg">
    <Sidebar />
    <main class="tg__main">
      <header class="tg__header">
        <div>
          <h1 class="tg__title">靶点库</h1>
          <p class="tg__subtitle">按靶点类型与疾病领域精细分类的候选靶点库</p>
        </div>
        <span class="tg__count">{{ filteredTargets.length }} / {{ mockTargets.length }} 个靶点</span>
      </header>

      <!-- 筛选 -->
      <section class="tg__filter">
        <input
          v-model="searchQuery"
          type="text"
          class="tg__search-input"
          placeholder="搜索名称、ID、基因名、中文名..."
        />
        <select v-model="filterType" class="tg__select">
          <option value="all">全部类型</option>
          <option v-for="t in targetTypes" :key="t" :value="t">{{ t }}</option>
        </select>
        <select v-model="filterArea" class="tg__select">
          <option value="all">全部领域</option>
          <option v-for="a in diseaseAreas" :key="a" :value="a">{{ a }}</option>
        </select>
      </section>

      <section class="tg__body">
        <!-- 靶点列表 -->
        <div class="tg__list">
          <div
            v-for="target in filteredTargets"
            :key="target.id"
            @click="selectTarget(target)"
            class="tg__item"
            :class="{ 'tg__item--selected': selectedTarget?.id === target.id }"
          >
            <div class="tg__item-head">
              <span class="tg__item-name">{{ target.name }}</span>
              <span class="tg__item-status">{{ getStatusText(target.status) }}</span>
            </div>
            <span v-if="target.chineseName" class="tg__item-cn">{{ target.chineseName }}</span>
            <div class="tg__item-tags">
              <span v-if="target.targetType" class="tg__tag tg__tag--type">{{ target.targetType }}</span>
              <span v-if="target.diseaseArea" class="tg__tag tg__tag--area">{{ target.diseaseArea }}</span>
            </div>
            <span class="tg__item-id">{{ target.uniprotId }}</span>
          </div>

          <div v-if="filteredTargets.length === 0" class="tg__empty">
            <span class="tg__empty-icon">🧪</span>
            <span class="tg__empty-text">未找到匹配的靶点</span>
          </div>
        </div>

        <!-- 靶点详情 -->
        <div v-if="selectedTarget" class="tg__detail">
          <div class="tg__detail-head">
            <div>
              <h3 class="tg__detail-name">{{ selectedTarget.name }}</h3>
              <span v-if="selectedTarget.chineseName" class="tg__detail-cn">{{ selectedTarget.chineseName }}</span>
            </div>
            <button class="tg__use-btn" @click.stop="useTarget(selectedTarget)">使用此靶点</button>
          </div>

          <p v-if="selectedTarget.description" class="tg__desc">{{ selectedTarget.description }}</p>

          <div class="tg__block">
            <h4 class="tg__block-title">基本信息</h4>
            <div class="tg__kv"><span>UniProt ID</span><b>{{ selectedTarget.uniprotId }}</b></div>
            <div class="tg__kv"><span>基因名称</span><b>{{ selectedTarget.geneName || '-' }}</b></div>
            <div class="tg__kv"><span>物种</span><b>{{ getOrganismText(selectedTarget.organism) }}</b></div>
            <div class="tg__kv"><span>结构 (PDB)</span><b>{{ selectedTarget.pdbIds?.join(', ') || selectedTarget.pdbId || '-' }}</b></div>
            <div class="tg__kv"><span>支持状态</span><b>{{ getStatusText(selectedTarget.status) }}</b></div>
          </div>

          <div class="tg__block">
            <h4 class="tg__block-title">分子特征</h4>
            <div class="tg__kv"><span>靶点类型</span><b>{{ selectedTarget.targetType || '-' }}</b></div>
            <div class="tg__kv"><span>蛋白家族</span><b>{{ selectedTarget.family || '-' }}</b></div>
            <div class="tg__kv"><span>主要通路</span><b>{{ selectedTarget.pathway || '-' }}</b></div>
            <div class="tg__kv"><span>疾病领域</span><b>{{ selectedTarget.diseaseArea || '-' }}</b></div>
          </div>

          <div class="tg__block">
            <h4 class="tg__block-title">临床相关</h4>
            <div v-if="selectedTarget.relatedDiseases" class="tg__kv"><span>相关疾病</span><b>{{ selectedTarget.relatedDiseases }}</b></div>
            <div v-if="selectedTarget.knownDrugs" class="tg__kv"><span>相关药物</span><b>{{ selectedTarget.knownDrugs }}</b></div>
          </div>
        </div>

        <!-- 未选中提示 -->
        <div v-else class="tg__placeholder">
          <span class="tg__placeholder-icon">👈</span>
          <p>从左侧选择一个靶点查看详细信息</p>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { mockTargets } from '@/data/mockResults'
import Sidebar from '@/components/Sidebar.vue'
import type { Target } from '@/types'

const router = useRouter()

const searchQuery = ref('')
const filterType = ref('all')
const filterArea = ref('all')
const selectedTarget = ref<Target | null>(null)

const targetTypes = computed(() => {
  return Array.from(new Set(mockTargets.map(t => t.targetType).filter(Boolean))) as string[]
})

const diseaseAreas = computed(() => {
  return Array.from(new Set(mockTargets.map(t => t.diseaseArea).filter(Boolean))) as string[]
})

const filteredTargets = computed(() => {
  let targets = [...mockTargets]

  if (filterType.value !== 'all') {
    targets = targets.filter(t => t.targetType === filterType.value)
  }

  if (filterArea.value !== 'all') {
    targets = targets.filter(t => t.diseaseArea === filterArea.value)
  }

  if (searchQuery.value) {
    const search = searchQuery.value.toLowerCase()
    targets = targets.filter(t =>
      t.name.toLowerCase().includes(search) ||
      t.uniprotId.toLowerCase().includes(search) ||
      (t.geneName && t.geneName.toLowerCase().includes(search)) ||
      (t.chineseName && t.chineseName.toLowerCase().includes(search)) ||
      (t.targetType && t.targetType.toLowerCase().includes(search))
    )
  }

  return targets
})

const getOrganismText = (organism?: string): string => {
  if (!organism) return '-'
  const texts: Record<string, string> = {
    human: '人类',
    mouse: '小鼠',
    rat: '大鼠'
  }
  return texts[organism] || organism
}

const getStatusText = (status: string): string => {
  const texts: Record<string, string> = {
    supported: '已支持',
    beta: '测试中',
    planned: '规划中'
  }
  return texts[status] || status
}

const selectTarget = (target: Target) => {
  selectedTarget.value = target
}

const useTarget = (target: Target) => {
  router.push({
    path: '/predict',
    query: {
      targetId: target.uniprotId,
      targetName: target.name
    }
  })
}
</script>

<style lang="scss" scoped>
.targets {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
}

.targets__content {
  flex: 1;
  margin-left: $sidebar-width;
  padding: $spacing-lg;
}

.targets__header {
  margin-bottom: $spacing-xl;
}

.targets__title {
  font-size: 24px;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-xs;
}

.targets__subtitle {
  font-size: $font-size-sm;
  color: $text-muted;
}

.targets__search {
  display: flex;
  gap: $spacing-md;
  margin-bottom: $spacing-xl;
}

.targets__search-input {
  flex: 1;
  padding: $spacing-sm $spacing-md;
  border: 1px solid $border-color;
  border-radius: $border-radius-md;
  font-size: $font-size-base;
  background: $bg-primary;
  
  &:focus {
    outline: none;
    border-color: $primary-color;
  }
}

.targets__organism-select {
  padding: $spacing-sm $spacing-md;
  border: 1px solid $border-color;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  background: $bg-primary;
  cursor: pointer;
  
  &:focus {
    outline: none;
    border-color: $primary-color;
  }
}

.targets__main {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-xl;
}

.targets__list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  max-height: calc(100vh - 200px);
  overflow-y: auto;
}

.targets__item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-md;
  background: $bg-primary;
  border-radius: $border-radius-md;
  border: 1px solid $border-light;
  cursor: pointer;
  transition: all $transition-fast;
  
  &:hover {
    border-color: $border-color;
  }
  
  &--selected {
    border-color: $primary-color;
    background: rgba($primary-color, 0.02);
  }
}

.targets__item-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.targets__item-name {
  font-size: $font-size-sm;
  font-weight: 500;
  color: $text-primary;
}

.targets__item-id {
  font-size: $font-size-xs;
  color: $text-muted;
  font-family: monospace;
}

.targets__item-organism {
  font-size: $font-size-xs;
  color: $text-muted;
  padding: 2px 8px;
  background: $bg-secondary;
  border-radius: $border-radius-sm;
}

.targets__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl;
}

.targets__empty-icon {
  font-size: 48px;
  margin-bottom: $spacing-md;
}

.targets__empty-text {
  font-size: $font-size-base;
  color: $text-muted;
}

.targets__detail {
  background: $bg-primary;
  border-radius: $border-radius-lg;
  padding: $spacing-lg;
  border: 1px solid $border-light;
  height: fit-content;
}

.targets__detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-lg;
}

.targets__detail-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
}

.targets__use-btn {
  padding: $spacing-sm $spacing-lg;
  background: $primary-color;
  color: #ffffff;
  border: none;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-fast;
  
  &:hover {
    background: $primary-dark;
  }
}

.targets__detail-content {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.targets__detail-row {
  display: flex;
  gap: $spacing-md;
}

.targets__detail-label {
  font-size: $font-size-xs;
  color: $text-muted;
  width: 100px;
  flex-shrink: 0;
}

.targets__detail-value {
  font-size: $font-size-sm;
  color: $text-primary;
  flex: 1;
}
</style>

<style lang="scss" scoped>
/* ===================== 靶点库（专业版） ===================== */
.tg {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
  padding-top: $header-height;
}

.tg__main {
  flex: 1;
  padding: $spacing-lg $spacing-xl;
  max-width: 1160px;
}

.tg__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: $spacing-lg;
}

.tg__title {
  font-size: $font-size-2xl;
  font-weight: 700;
  color: $text-primary;
}

.tg__subtitle {
  margin-top: $spacing-xs;
  font-size: $font-size-sm;
  color: $text-muted;
}

.tg__count {
  font-size: $font-size-sm;
  color: $text-secondary;
  background: $bg-tertiary;
  padding: $spacing-xs $spacing-md;
  border-radius: 999px;
}

/* ---------- 筛选 ---------- */
.tg__filter {
  display: flex;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
}

.tg__search-input {
  flex: 1;
  padding: $spacing-sm $spacing-md;
  border: 1px solid $border-color;
  border-radius: $border-radius-md;
  font-size: $font-size-base;
  background: $bg-primary;
  &:focus { outline: none; border-color: $accent-color; box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12); }
}

.tg__select {
  padding: $spacing-sm $spacing-md;
  border: 1px solid $border-color;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  background: $bg-primary;
  color: $text-primary;
  cursor: pointer;
  max-width: 240px;
  &:focus { outline: none; border-color: $accent-color; }
}

/* ---------- 布局 ---------- */
.tg__body {
  display: grid;
  grid-template-columns: 1fr 1.3fr;
  gap: $spacing-xl;
  align-items: start;
}

.tg__list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  max-height: calc(100vh - 120px);
  overflow-y: auto;
  padding-right: $spacing-xs;
}

.tg__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: $spacing-md;
  background: $bg-primary;
  border-radius: $border-radius-md;
  border: 1px solid $border-light;
  cursor: pointer;
  transition: $transition-fast;
  &:hover { border-color: $border-color; box-shadow: $shadow-sm; }
  &--selected { border-color: $accent-color; background: rgba(59, 130, 246, 0.04); }
}

.tg__item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $spacing-sm;
}

.tg__item-name {
  font-size: $font-size-base;
  font-weight: 700;
  color: $text-primary;
}

.tg__item-status {
  font-size: $font-size-xs;
  color: $text-muted;
  white-space: nowrap;
}

.tg__item-cn {
  font-size: $font-size-xs;
  color: $text-muted;
}

.tg__item-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tg__tag {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: $font-size-xs;
  font-weight: 500;
  white-space: nowrap;
  &--type { background: rgba(59, 130, 246, 0.1); color: $accent-color; }
  &--area { background: rgba(16, 185, 129, 0.1); color: $success-color; }
}

.tg__item-id {
  font-size: $font-size-xs;
  color: $text-light;
  font-family: monospace;
}

.tg__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-2xl;
  color: $text-muted;
}

.tg__empty-icon { font-size: 40px; margin-bottom: $spacing-sm; }
.tg__empty-text { font-size: $font-size-base; }

/* ---------- 详情 ---------- */
.tg__detail {
  background: $bg-primary;
  border: 1px solid $border-color;
  border-radius: $border-radius-lg;
  padding: $spacing-lg;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.tg__detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: $spacing-md;
  padding-bottom: $spacing-md;
  border-bottom: 1px solid $border-light;
}

.tg__detail-name {
  font-size: $font-size-xl;
  font-weight: 700;
  color: $text-primary;
}

.tg__detail-cn {
  display: block;
  margin-top: 4px;
  font-size: $font-size-sm;
  color: $accent-color;
}

.tg__use-btn {
  padding: $spacing-sm $spacing-lg;
  background: $primary-color;
  color: #fff;
  border: none;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  font-weight: 500;
  cursor: pointer;
  transition: $transition-fast;
  white-space: nowrap;
  &:hover { background: $primary-light; }
}

.tg__desc {
  padding: $spacing-md;
  background: $bg-secondary;
  border-left: 3px solid $accent-color;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  line-height: 1.7;
  color: $text-secondary;
}

.tg__block {
  padding: $spacing-md;
  background: $bg-secondary;
  border-radius: $border-radius-md;
}

.tg__block-title {
  font-size: $font-size-sm;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

.tg__kv {
  display: flex;
  gap: $spacing-md;
  padding: $spacing-xs 0;
  font-size: $font-size-sm;
  span { width: 84px; color: $text-muted; flex-shrink: 0; }
  b { color: $text-primary; font-weight: 500; line-height: 1.6; }
}

.tg__placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-3xl;
  background: $bg-primary;
  border: 1px dashed $border-color;
  border-radius: $border-radius-lg;
  color: $text-muted;
  .tg__placeholder-icon { font-size: 40px; margin-bottom: $spacing-md; }
  p { font-size: $font-size-base; }
}
</style>