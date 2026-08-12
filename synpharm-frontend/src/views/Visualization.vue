
<template>
  <div class="visualization">

    <Sidebar />

    <main class="visualization__content">

      <!-- Header -->
      <header class="visualization__header">

        <div>

          <h1 class="visualization__title">
            3D可视化
          </h1>

          <p class="visualization__subtitle">
            交互式分子结构展示
          </p>

        </div>

      </header>

      <!-- Main -->
      <section class="visualization__main">

        <!-- =========================
             Viewer
        ========================== -->

        <div class="visualization__canvas">

          <!-- Resolving -->
          <div
            v-if="resolving"
            class="visualization__canvas-placeholder"
          >

            <span class="visualization__canvas-placeholder-icon">
              🔍
            </span>

            <span class="visualization__canvas-placeholder-text">
              正在查找蛋白质结构...
            </span>

            <span class="visualization__canvas-placeholder-hint">
              {{ uniprotId }}
            </span>

          </div>

          <!-- Mol* -->
          <MolstarViewer
            v-else-if="resolvedPdbId"

            ref="molstarRef"

            :pdb-id="resolvedPdbId"

            :auto-rotate="autoRotate"

            :show-grid="showGrid"

            @structure-loaded="onStructureLoaded"
          />

          <!-- Error -->
          <div
            v-else-if="resolveError"
            class="visualization__canvas-placeholder"
          >

            <span class="visualization__canvas-placeholder-icon">
              ⚠️
            </span>

            <span class="visualization__canvas-placeholder-text">
              结构数据库查询失败
            </span>

            <span class="visualization__canvas-placeholder-hint">
              已尝试使用本地结构映射
            </span>

          </div>

          <!-- No structure -->
          <div
            v-else
            class="visualization__canvas-placeholder"
          >

            <span class="visualization__canvas-placeholder-icon">
              🧬
            </span>

            <span class="visualization__canvas-placeholder-text">
              暂无对应 PDB 结构
            </span>

            <span class="visualization__canvas-placeholder-hint">
              该靶点暂无可用的蛋白质结构数据
            </span>

          </div>

        </div>

        <!-- =========================
             Controls
        ========================== -->

        <div class="visualization__controls">

          <!-- Display -->
          <div class="visualization__control-section">

            <h3 class="visualization__control-title">
              显示模式
            </h3>

            <div class="visualization__mode-buttons">

              <button
                v-for="mode in displayModes"
                :key="mode.value"

                class="visualization__mode-btn"

                :class="{
                  'visualization__mode-btn--active':
                    displayMode === mode.value
                }"

                @click="changeDisplayMode(mode.value)"
              >

                {{ mode.label }}

              </button>

            </div>

          </div>

          <!-- Color -->
          <div class="visualization__control-section">

            <h3 class="visualization__control-title">
              颜色方案
            </h3>

            <div class="visualization__color-options">

              <button
                v-for="color in colorSchemes"
                :key="color.value"

                class="visualization__color-btn"

                :class="{
                  'visualization__color-btn--active':
                    colorScheme === color.value
                }"

                :style="{
                  background: color.preview
                }"

                :title="color.label"

                @click="changeColorScheme(color.value)"
              />

            </div>

          </div>

          <!-- Settings -->
          <div class="visualization__control-section">

            <h3 class="visualization__control-title">
              设置
            </h3>

            <!-- Grid -->
            <div class="visualization__setting-item">

              <label class="visualization__setting-label">
                显示网格
              </label>

              <input
                v-model="showGrid"

                type="checkbox"

                class="visualization__setting-checkbox"
              />

            </div>

            <!-- Rotate -->
            <div class="visualization__setting-item">

              <label class="visualization__setting-label">
                自动旋转
              </label>

              <input
                v-model="autoRotate"

                type="checkbox"

                class="visualization__setting-checkbox"
              />

            </div>

            <!-- Labels -->
            <div class="visualization__setting-item">

              <label class="visualization__setting-label">
                显示标签
              </label>

              <input
                v-model="showLabels"

                type="checkbox"

                class="visualization__setting-checkbox"
              />

            </div>

          </div>

          <!-- Actions -->
          <div class="visualization__control-section">

            <h3 class="visualization__control-title">
              操作
            </h3>

            <div class="visualization__action-buttons">

              <button
                class="visualization__action-btn"

                @click="resetView"
              >
                重置视角
              </button>

              <button
                class="visualization__action-btn"

                @click="exportImage"
              >
                导出图片
              </button>

            </div>

          </div>

        </div>

      </section>

      <!-- =========================
           Information
      ========================== -->

      <section class="visualization__info">

        <!-- Structure -->
        <div class="visualization__info-card">

          <h3 class="visualization__info-title">

            <span class="visualization__info-title-icon">
              📋
            </span>

            结构信息

          </h3>

          <div class="visualization__info-content">

            <div class="visualization__info-row">

              <span class="visualization__info-label">
                靶点名称
              </span>

              <span class="visualization__info-value">
                {{ targetName }}
              </span>

            </div>

            <div class="visualization__info-row">

              <span class="visualization__info-label">
                靶点ID
              </span>

              <span
                class="visualization__info-value visualization__info-value--mono"
              >
                {{ targetId }}
              </span>

            </div>

            <div class="visualization__info-row">

              <span class="visualization__info-label">
                结合亲和力
              </span>

              <span
                class="visualization__info-value visualization__info-value--highlight"
              >
                {{
                  bindingAffinity !== null
                    ? bindingAffinity.toFixed(2) + ' kcal/mol'
                    : 'N/A'
                }}
              </span>

            </div>

            <div class="visualization__info-row">

              <span class="visualization__info-label">
                置信度
              </span>

              <span
                class="visualization__info-value"

                :style="{
                  color: getConfidenceColor(confidenceScore)
                }"
              >

                {{
                  Math.round(
                    confidenceScore * 100
                  )
                }}%

                ({{ getConfidenceText(confidenceLevel) }})

              </span>

            </div>

            <div class="visualization__info-row">

              <span class="visualization__info-label">
                相互作用数
              </span>

              <span class="visualization__info-value">
                {{ interactionCount }}
              </span>

            </div>

          </div>

        </div>

        <!-- Interactions -->
        <div
          v-if="interactions.length"
          class="visualization__info-card"
        >

          <h3 class="visualization__info-title">

            <span class="visualization__info-title-icon">
              🔗
            </span>

            相互作用详情

          </h3>

          <div class="visualization__interaction-list">

            <div
              v-for="(inter, idx) in interactions"

              :key="idx"

              class="visualization__interaction-item"
            >

              <span
                class="visualization__interaction-badge"

                :style="{
                  background:
                    getInteractionColor(inter.type)
                }"
              >

                {{ getInteractionTypeName(inter.type) }}

              </span>

              <span class="visualization__interaction-detail">

                {{ inter.residueName }}{{ inter.residueNumber }}

              </span>

              <span class="visualization__interaction-distance">

                {{ inter.distance }}Å

              </span>

            </div>

          </div>

        </div>

      </section>

    </main>

  </div>
</template>

<script setup lang="ts">

import {
  ref,
  computed,
  watch,
  nextTick,
} from 'vue'

import { useRoute } from 'vue-router'

import {
  mockResults,
  mockTargets,
} from '@/data/mockResults'

import {
  resolveStructure,
  getPdbIdFromUniProt,
} from '@/utils/protein/structureResolver'

import Sidebar from '@/components/Sidebar.vue'

import MolstarViewer from '@/components/protein/MolstarViewer.vue'


/* =========================================================
 * Route
 * ========================================================= */

const route = useRoute()

const molstarRef =
  ref<InstanceType<typeof MolstarViewer> | null>(null)


/* =========================================================
 * Result
 * ========================================================= */

const result = computed(() => {

  const id =
    route.query.id as string

  return (
    mockResults.find(
      item => item.id === id
    ) || null
  )

})


/* =========================================================
 * Basic Info
 * ========================================================= */

const targetName = computed(() => {

  return (
    (route.query.targetName as string) ||
    result.value?.targetName ||
    'Unknown'
  )

})


const targetId = computed(() => {

  return (
    (route.query.targetId as string) ||
    result.value?.targetId ||
    '-'
  )

})


/* =========================================================
 * UniProt
 * ========================================================= */

const uniprotId = computed(() => {

  const name =
    targetName.value

  if (!name) {
    return null
  }

  const target =
    mockTargets.find(
      item =>
        item.name.toLowerCase() ===
        name.toLowerCase()
    )

  const uid =
    target?.uniprotId

  return (
    uid &&
    uid !== '-'
  )
    ? uid.toUpperCase()
    : null

})


/* =========================================================
 * Structure Resolve
 * ========================================================= */

const resolving =
  ref(false)

const resolveError =
  ref(false)

const resolvedPdbId =
  ref<string | null>(null)

const resolvedSource =
  ref('')


async function doResolve(): Promise<void> {

  const uid =
    uniprotId.value

  if (!uid) {

    resolvedPdbId.value =
      null

    resolvedSource.value =
      'none'

    return
  }

  resolving.value =
    true

  resolveError.value =
    false

  try {

    const resolved =
      await resolveStructure(uid)

    console.log(
      '[Structure Resolver]',
      {
        targetName:
          targetName.value,

        uniprotId:
          uid,

        source:
          resolved.source,

        structureId:
          resolved.structureId,
      }
    )

    if (resolved.structureId) {

      resolvedPdbId.value =
        resolved.structureId

      resolvedSource.value =
        resolved.source

    } else {

      resolvedPdbId.value =
        null

      resolvedSource.value =
        'none'
    }

  } catch (err) {

    console.error(
      '[Structure Resolver]',
      err
    )

    const fallback =
      getPdbIdFromUniProt(uid)

    if (fallback) {

      resolvedPdbId.value =
        fallback

      resolvedSource.value =
        'fallback'

      resolveError.value =
        true

    } else {

      resolvedPdbId.value =
        null

      resolvedSource.value =
        'none'

      resolveError.value =
        true
    }

  } finally {

    resolving.value =
      false
  }

}


/* =========================================================
 * Resolve Watch
 * ========================================================= */

watch(
  uniprotId,

  () => {
    doResolve()
  },

  {
    immediate: true,
  }
)


/* =========================================================
 * Info
 * ========================================================= */

const bindingAffinity = computed(() => {

  const value =
    route.query.bindingAffinity as string

  return value
    ? parseFloat(value)
    : result.value?.bindingAffinity ?? null

})


const confidenceScore = computed(() => {

  const value =
    route.query.confidenceScore as string

  return value
    ? parseFloat(value)
    : result.value?.confidenceScore ?? 0

})


const confidenceLevel = computed(() => {

  return (
    (route.query.confidenceLevel as string) ||
    result.value?.confidenceLevel ||
    'medium'
  )

})


const interactions = computed(() => {

  return result.value?.interactions || []

})


const interactionCount = computed(() => {

  return interactions.value.length

})


/* =========================================================
 * UI State
 * ========================================================= */

const displayMode =
  ref('cartoon')

const colorScheme =
  ref('chain')

const showGrid =
  ref(false)

const autoRotate =
  ref(false)

const showLabels =
  ref(false)


/* =========================================================
 * Display Modes
 * ========================================================= */

const displayModes = [

  {
    value: 'cartoon',
    label: '卡通',
  },

  {
    value: 'sphere',
    label: '球体',
  },

  {
    value: 'stick',
    label: '棍状',
  },

  {
    value: 'surface',
    label: '表面',
  },

]


/* =========================================================
 * Colors
 * ========================================================= */

const colorSchemes = [

  {
    value: 'chain',
    label: '链颜色',
    preview:
      'linear-gradient(to right, #1a1a2e, #0f3460)',
  },

  {
    value: 'element',
    label: '元素',
    preview:
      'linear-gradient(to right, #4CAF50, #FF9800, #2196F3)',
  },

  {
    value: 'secondary',
    label: '二级结构',
    preview:
      'linear-gradient(to right, #E91E63, #2196F3)',
  },

  {
    value: 'uniform',
    label: '单色',
    preview:
      '#1a1a2e',
  },

]


/* =========================================================
 * Mol* Mapping
 * ========================================================= */

const MOLSTAR_REP_TYPES: Record<string, string> = {

  cartoon:
    'cartoon',

  sphere:
    'spacefill',

  stick:
    'ball-and-stick',

  surface:
    'molecular-surface',

}


const MOLSTAR_COLOR_TYPES: Record<string, string> = {

  chain:
    'chain-id',

  element:
    'element-symbol',

  secondary:
    'secondary-structure',

  uniform:
    'uniform',

}


/* =========================================================
 * Structure Loaded
 * ========================================================= */

function onStructureLoaded(): void {

  console.log(
    '[Visualization] structure loaded'
  )

  /*
   * Mol* 加载完成后，
   * 将当前 UI 状态重新同步一次。
   */

  nextTick(() => {

    if (displayMode.value !== 'cartoon') {

      const type =
        MOLSTAR_REP_TYPES[
          displayMode.value
        ]

      if (type) {

        molstarRef.value
          ?.updateRepresentation(type)
      }
    }

    if (colorScheme.value !== 'chain') {

      const color =
        MOLSTAR_COLOR_TYPES[
          colorScheme.value
        ]

      if (color) {

        molstarRef.value
          ?.setColorScheme(color)
      }
    }

    if (showLabels.value) {

      molstarRef.value
        ?.setLabelsVisible(true)
    }

  })

}


/* =========================================================
 * Display Mode
 * ========================================================= */

function changeDisplayMode(
  mode: string
): void {

  displayMode.value =
    mode

  const type =
    MOLSTAR_REP_TYPES[mode]

  if (!type) {
    return
  }

  nextTick(() => {

    molstarRef.value
      ?.updateRepresentation(type)

  })

}


/* =========================================================
 * Color
 * ========================================================= */

function changeColorScheme(
  scheme: string
): void {

  colorScheme.value =
    scheme

  const color =
    MOLSTAR_COLOR_TYPES[scheme]

  if (!color) {
    return
  }

  nextTick(() => {

    molstarRef.value
      ?.setColorScheme(color)

  })

}


/* =========================================================
 * Grid
 * ========================================================= */

/* =========================================================
 * Labels
 * ========================================================= */

watch(
  showLabels,
  value => {

    nextTick(() => {

      molstarRef.value
        ?.setLabelsVisible(value)

    })

  }
)


/* =========================================================
 * Reset
 * ========================================================= */

function resetView(): void {

  molstarRef.value
    ?.resetView()

}


/* =========================================================
 * Export
 * ========================================================= */

async function exportImage(): Promise<void> {

  await molstarRef.value
    ?.exportImage()

}


/* =========================================================
 * Confidence
 * ========================================================= */

const getConfidenceColor =
  (score: number): string => {

    if (score >= 0.8) {
      return '#10b981'
    }

    if (score >= 0.6) {
      return '#f59e0b'
    }

    return '#ef4444'
  }


const getConfidenceText =
  (level: string): string => {

    const texts: Record<string, string> = {

      high: '高',

      medium: '中',

      low: '低',

    }

    return texts[level] || level
  }


/* =========================================================
 * Interaction
 * ========================================================= */

const getInteractionColor =
  (type: string): string => {

    const colors: Record<string, string> = {

      hydrogen_bond:
        'rgba(16, 185, 129, 0.15)',

      hydrophobic:
        'rgba(245, 158, 11, 0.15)',

      ionic:
        'rgba(239, 68, 68, 0.15)',

      pi_pi:
        'rgba(139, 92, 246, 0.15)',

      metal:
        'rgba(59, 130, 246, 0.15)',

    }

    return (
      colors[type] ||
      'rgba(148, 163, 184, 0.15)'
    )
  }


const getInteractionTypeName =
  (type: string): string => {

    const types: Record<string, string> = {

      hydrogen_bond:
        '氢键',

      hydrophobic:
        '疏水',

      ionic:
        '离子',

      pi_pi:
        'π-π',

      metal:
        '金属',

    }

    return types[type] || type
  }

</script>

<style lang="scss" scoped>

.visualization {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
  padding-top: $header-height;
}

.visualization__content {
  flex: 1;
  padding: $spacing-lg;
}

.visualization__header {
  margin-bottom: $spacing-lg;
}

.visualization__title {
  font-size: $font-size-3xl;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 2px;
}

.visualization__subtitle {
  font-size: $font-size-xs;
  color: $text-muted;
}


/* =========================================================
 * Main
 * ========================================================= */

.visualization__main {
  display: grid;

  grid-template-columns:
    3fr 1fr;

  gap: $spacing-md;

  margin-bottom: $spacing-lg;
}

.visualization__canvas {
  height: 480px;

  border-radius:
    $border-radius-lg;

  border:
    1px solid $border-light;

  overflow: hidden;

  background: #000;
}


/* =========================================================
 * Placeholder
 * ========================================================= */

.visualization__canvas-placeholder {

  width: 100%;
  height: 100%;

  display: flex;

  flex-direction: column;

  align-items: center;

  justify-content: center;

  gap: $spacing-sm;

  background: #f8fafc;
}

.visualization__canvas-placeholder-icon {
  font-size: 48px;
  opacity: 0.4;
}

.visualization__canvas-placeholder-text {
  font-size: $font-size-base;
  color: $text-secondary;
}

.visualization__canvas-placeholder-hint {
  font-size: $font-size-xs;
  color: $text-muted;
}


/* =========================================================
 * Controls
 * ========================================================= */

.visualization__controls {

  display: flex;

  flex-direction: column;

  gap: $spacing-sm;
}

.visualization__control-section {

  background: $bg-primary;
  border-radius: $border-radius-lg;
  border: 1px solid $border-light;

  padding: $spacing-md;
}

.visualization__control-title {

  font-size: 10px;

  font-weight: 600;

  color: $text-muted;

  text-transform: uppercase;

  letter-spacing: 0.4px;

  margin-bottom: $spacing-sm;
}


/* =========================================================
 * Display Buttons
 * ========================================================= */

.visualization__mode-buttons {

  display: grid;

  grid-template-columns:
    1fr 1fr;

  gap: $spacing-xs;
}

.visualization__mode-btn {

  display: inline-flex; align-items: center; gap: 6px; padding: 6px 14px; background: transparent; border: 1px solid transparent; border-radius: $border-radius-md; font-size: $font-size-sm; color: $text-secondary; cursor: pointer; transition: all $transition-fast; &:hover { background: $bg-tertiary; color: $text-primary; }

  font-size: 11px;

  padding:
    5px 6px;

  &--active {
    background: rgba($primary-color, 0.08); color: $primary-color; border-color: rgba($primary-color, 0.12);
  }
}


/* =========================================================
 * Color
 * ========================================================= */

.visualization__color-options {

  display: flex;

  gap: $spacing-xs;
}

.visualization__color-btn {

  width: 28px;

  height: 28px;

  border-radius: 50%;

  border: 2px solid transparent;

  cursor: pointer;

  transition:
    all $transition-fast;

  &:hover {
    transform: scale(1.1);
  }

  &--active {

    border-color:
      $primary-color;

    box-shadow:
      0 0 0 2px
      rgba(
        $primary-color,
        0.2
      );
  }
}


/* =========================================================
 * Settings
 * ========================================================= */

.visualization__setting-item {

  display: flex;

  justify-content:
    space-between;

  align-items: center;

  margin-bottom:
    $spacing-sm;

  padding-bottom:
    $spacing-sm;

  border-bottom:
    1px solid $border-light;

  &:last-child {

    margin-bottom: 0;

    padding-bottom: 0;

    border-bottom: none;
  }
}

.visualization__setting-label {

  font-size:
    $font-size-xs;

  color:
    $text-secondary;
}

.visualization__setting-checkbox {

  width: 16px;

  height: 16px;

  cursor: pointer;

  accent-color:
    $primary-color;
}


/* =========================================================
 * Action
 * ========================================================= */

.visualization__action-buttons {

  display: flex;

  flex-direction: column;

  gap: $spacing-xs;
}

.visualization__action-btn {

  display: inline-flex; align-items: center; gap: 6px; padding: 10px 16px; background: transparent; color: $text-secondary; border: 1px solid $border-color; border-radius: $border-radius-md; font-size: $font-size-sm; cursor: pointer; transition: all $transition-fast; &:hover { background: $bg-tertiary; }
}


/* =========================================================
 * Info
 * ========================================================= */

.visualization__info {

  display: grid;

  grid-template-columns:
    1fr 1fr;

  gap: $spacing-md;
}

.visualization__info-card {

  background: $bg-primary; border-radius: $border-radius-lg; border: 1px solid $border-light; box-shadow: $shadow-md; border-color: transparent;

  padding: $spacing-lg;
}

.visualization__info-title {

  font-size:
    $font-size-sm;

  font-weight: 600;

  color:
    $text-primary;

  margin-bottom:
    $spacing-md;

  display: flex;

  align-items: center;

  gap: $spacing-xs;
}

.visualization__info-title-icon {
  font-size: 16px;
}

.visualization__info-content {

  display: flex;

  flex-direction: column;

  gap: $spacing-sm;
}

.visualization__info-row {

  display: flex;

  justify-content:
    space-between;

  align-items: center;

  padding-bottom:
    $spacing-xs;

  border-bottom:
    1px dashed $border-light;

  &:last-child {

    padding-bottom: 0;

    border-bottom: none;
  }
}

.visualization__info-label {

  font-size: 10px;

  color:
    $text-muted;

  text-transform:
    uppercase;

  letter-spacing: 0.3px;
}

.visualization__info-value {

  font-size:
    $font-size-xs;

  font-weight: 600;

  color:
    $text-primary;

  &--mono {

    font-family:
      "Consolas", "Monaco", "Courier New", monospace;

    font-size: 10px;
  }

  &--highlight {

    font-weight: 600; letter-spacing: -0.3px;

    color:
      $accent-color;

    font-size:
      $font-size-sm;
  }
}


/* =========================================================
 * Interaction
 * ========================================================= */

.visualization__interaction-list {

  display: flex;

  flex-direction: column;

  gap: $spacing-xs;
}

.visualization__interaction-item {

  display: flex;

  align-items: center;

  gap: $spacing-xs;

  padding:
    $spacing-xs
    $spacing-sm;

  background:
    $bg-secondary;

  border-radius:
    $border-radius-sm;
}

.visualization__interaction-badge {

  padding:
    1px 8px;

  border-radius:
    9999px;

  font-size: 10px;

  font-weight: 500;

  color:
    $text-secondary;

  white-space: nowrap;
}

.visualization__interaction-detail {

  flex: 1;

  font-size: 10px;

  font-weight: 500;

  color:
    $text-primary;

  font-family:
    "Consolas", "Monaco", "Courier New", monospace;
}

.visualization__interaction-distance {

  font-size: 10px;

  color:
    $text-muted;

  font-family:
    "Consolas", "Monaco", "Courier New", monospace;
}

</style>
