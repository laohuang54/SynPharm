<template>
  <div class="pc">
    <Sidebar />
    <main class="pc__main">
      <!-- 页头 -->
      <header class="pc__header">
        <div>
          <h1 class="pc__title">预测中心</h1>
          <p class="pc__subtitle">药物 · 蛋白质 · 分子间相互作用智能预测</p>
        </div>
        <div class="pc__stats">
          <span class="pc__stat"><b>2,500+</b><em>预测任务</em></span>
          <span class="pc__stat"><b>98%</b><em>准确率</em></span>
        </div>
      </header>

      <!-- 模式切换 -->
      <div class="pc__tabs">
        <button class="pc__tab" :class="{ 'pc__tab--active': mode === 'single' }" @click="mode = 'single'">单条预测</button>
        <button class="pc__tab" :class="{ 'pc__tab--active': mode === 'batch' }" @click="mode = 'batch'">批量预测</button>
      </div>

      <!-- ===== 单条预测 ===== -->
      <section v-show="mode === 'single'" class="pc__panel">
        <div class="pc__card">
          <div class="pc__types">
            <button
              v-for="type in predictionTypes"
              :key="type.value"
              class="pc__type"
              :class="{ 'pc__type--active': selectedType === type.value }"
              @click="selectedType = type.value"
            >
              <span class="pc__type-icon">{{ type.icon }}</span>
              <span class="pc__type-name">{{ type.label }}</span>
            </button>
          </div>

          <p class="pc__form-subtitle">{{ getFormSubtitle() }}</p>

          <div class="pc__form">
            <div class="pc__input-block">
              <div class="pc__input-head">
                <label class="pc__label">{{ getFirstInputLabel() }}</label>
                <button v-if="firstInputValue" class="pc__clear" @click="clearFirstInput" title="清空">✕</button>
              </div>
              <input
                v-model="firstInputValue"
                type="text"
                class="pc__input"
                :class="{ 'pc__input--error': firstInputError }"
                :placeholder="getFirstInputPlaceholder()"
                @input="validateFirstInput"
              />
              <div class="pc__hint">
                <span v-if="firstInputError" class="pc__err">{{ firstInputError }}</span>
                <span v-else-if="firstInputValue" class="pc__ok">✓ 格式有效</span>
              </div>
            </div>

            <div class="pc__vs"><span>VS</span></div>

            <div class="pc__input-block">
              <div class="pc__input-head">
                <label class="pc__label">{{ getSecondInputLabel() }}</label>
                <button v-if="secondInputValue" class="pc__clear" @click="clearSecondInput" title="清空">✕</button>
              </div>
              <input
                v-model="secondInputValue"
                type="text"
                class="pc__input"
                :class="{ 'pc__input--error': secondInputError }"
                :placeholder="getSecondInputPlaceholder()"
                @input="validateSecondInput"
              />
              <div class="pc__hint">
                <span v-if="secondInputError" class="pc__err">{{ secondInputError }}</span>
                <span v-else-if="secondInputValue" class="pc__ok">✓ 格式有效</span>
              </div>
            </div>
          </div>

          <div class="pc__advanced">
            <button class="pc__advanced-toggle" @click="showAdvancedOptions = !showAdvancedOptions">
              <span class="pc__chevron" :class="{ 'pc__chevron--open': showAdvancedOptions }">▾</span>
              {{ showAdvancedOptions ? '收起' : '展开' }}高级选项
            </button>
            <div v-if="showAdvancedOptions" class="pc__advanced-body">
              <div class="pc__advanced-item">
                <div class="pc__advanced-head">
                  <label class="pc__label">置信度阈值</label>
                  <span class="pc__range-val">{{ confidenceThreshold }}%</span>
                </div>
                <input v-model="confidenceThreshold" type="range" min="0" max="100" class="pc__range" />
              </div>
              <label class="pc__advanced-item pc__advanced-item--row">
                <span class="pc__label">输出详细结果</span>
                <span class="pc__checkbox">
                  <input v-model="detailedOutput" type="checkbox" />
                  <span class="pc__checkbox-box"></span>
                </span>
                <span class="pc__desc">包含完整相互作用与可视化数据</span>
              </label>
            </div>
          </div>

          <div class="pc__actions">
            <button class="pc__btn pc__btn--ghost" @click="handleDemoPredict">
              <svg class="pc__btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><path d="M15 9l-6 6"></path><path d="M9 9l6 6"></path></svg>
              使用演示数据
            </button>
            <button class="pc__btn pc__btn--primary" :disabled="!isValidInput || isLoading" @click="handlePredict">
              <span v-if="isLoading" class="pc__spin"></span>
              {{ isLoading ? '预测中...' : '开始预测' }}
            </button>
          </div>
          <div v-if="predictError" class="pc__error">{{ predictError }}</div>
        </div>
      </section>

      <!-- ===== 批量预测 ===== -->
      <section v-show="mode === 'batch'" class="pc__panel">
        <div class="pc__card">
          <div class="pc__card-head">
            <h3 class="pc__card-title">CSV 批量预测</h3>
            <p class="pc__card-sub">上传 CSV 文件，系统异步处理，支持 DTI / PPI / DDI</p>
          </div>

          <div class="pc__batch-body">
            <select v-model="batchAlgoType" class="pc__select">
              <option value="DTI">DTI 预测</option>
              <option value="PPI">PPI 预测</option>
              <option value="DDI">DDI 预测</option>
            </select>

            <label class="pc__dropzone" :class="{ 'pc__dropzone--has': batchFile }">
              <input type="file" accept=".csv" class="pc__file" @change="handleBatchFileChange" />
              <template v-if="!batchFile">
                <span class="pc__dropzone-icon">📄</span>
                <span class="pc__dropzone-text">点击选择 CSV 文件</span>
                <span class="pc__dropzone-sub">每行一个输入（SMILES / 序列）</span>
              </template>
              <template v-else>
                <span class="pc__dropzone-icon">✅</span>
                <span class="pc__dropzone-text">{{ batchFile.name }}</span>
                <span class="pc__dropzone-sub">点击可重新选择</span>
              </template>
            </label>

            <button
              class="pc__btn pc__btn--primary pc__btn--block"
              :disabled="!batchFile || batchUploading"
              @click="handleBatchUpload"
            >
              <span v-if="batchUploading" class="pc__spin"></span>
              {{ batchUploading ? '上传中...' : '开始批量预测' }}
            </button>
          </div>

          <div v-if="batchError" class="pc__error">{{ batchError }}</div>

          <div v-if="batchStatus" class="pc__batch-status">
            <div class="pc__batch-status-row">
              <span class="pc__batch-id">批次 {{ batchStatus.batchId?.substring(0, 8) }}</span>
              <span
                class="pc__batch-state"
                :class="'pc__batch-state--' + batchStatus.status.toLowerCase()"
              >{{ batchStatus.status }}</span>
              <span class="pc__batch-pct">{{ batchStatus.progress }}%</span>
            </div>
            <div class="pc__progress">
              <div class="pc__progress-fill" :style="{ width: (batchStatus.progress || 0) + '%' }"></div>
            </div>
            <button
              v-if="batchStatus.status === 'SUCCESS'"
              class="pc__btn pc__btn--secondary pc__btn--block"
              @click="handleBatchDownload"
            >
              ⬇ 下载结果
            </button>
          </div>
        </div>
      </section>

      <!-- ===== 预测结果 ===== -->
      <section v-if="predictionResult" class="pc__panel">
        <div class="pc__card">
          <div class="pc__result-head">
            <div>
              <h3 class="pc__card-title">预测结果</h3>
              <span class="pc__result-time">{{ formatTime(predictionResult.createdAt) }}</span>
            </div>
            <span class="pc__badge">{{ predictionResult.datasetInfo.name }}</span>
          </div>

          <div class="pc__result-grid">
            <div class="pc__score">
              <div class="pc__score-ring">
                <svg class="pc__score-svg" viewBox="0 0 120 120">
                  <circle cx="60" cy="60" r="50" fill="none" stroke="#e8edf3" stroke-width="10" />
                  <circle
                    cx="60" cy="60" r="50" fill="none"
                    :stroke="getConfidenceColor(predictionResult.confidenceScore)"
                    stroke-width="10" stroke-linecap="round"
                    :stroke-dasharray="`${predictionResult.confidenceScore * 314} 314`"
                    transform="rotate(-90 60 60)"
                  />
                </svg>
                <div class="pc__score-num">{{ Math.round(predictionResult.confidenceScore * 100) }}%</div>
              </div>
              <span class="pc__score-label">结合概率</span>
              <span
                class="pc__score-level"
                :style="{ color: getConfidenceColor(predictionResult.confidenceScore) }"
              >{{ getConfidenceText(predictionResult.confidenceLevel) }}置信度</span>
            </div>

            <div class="pc__details">
              <div class="pc__detail">
                <span class="pc__detail-label">靶点名称</span>
                <span class="pc__detail-val">{{ predictionResult.targetName }}</span>
              </div>
              <div class="pc__detail">
                <span class="pc__detail-label">靶点 ID</span>
                <span class="pc__detail-val">{{ predictionResult.targetId }}</span>
              </div>
              <div class="pc__detail pc__detail--accent">
                <span class="pc__detail-label">结合亲和力</span>
                <span class="pc__detail-val">{{ predictionResult.bindingAffinity.toFixed(2) }} <small>kcal/mol</small></span>
              </div>
            </div>
          </div>

          <div class="pc__section">
            <h4 class="pc__section-title">相互作用分析</h4>
            <div class="pc__interactions">
              <div v-for="(it, i) in predictionResult.interactions" :key="i" class="pc__interaction">
                <span
                  class="pc__interaction-tag"
                  :style="{ background: getInteractionBgColor(it.type), color: getInteractionColor(it.type) }"
                >{{ getInteractionIcon(it.type) }} {{ getInteractionTypeName(it.type) }}</span>
                <span class="pc__interaction-meta">{{ it.residueName }} {{ it.residueNumber }} · {{ it.distance }} Å</span>
              </div>
            </div>
          </div>

          <div class="pc__section">
            <h4 class="pc__section-title">数据集信息</h4>
            <div class="pc__dataset">
              <div class="pc__dataset-item"><span>名称</span><b>{{ predictionResult.datasetInfo.name }}</b></div>
              <div class="pc__dataset-item"><span>大小</span><b>{{ formatNumber(predictionResult.datasetInfo.size) }} 条记录</b></div>
              <div class="pc__dataset-item"><span>数据源</span><b>{{ predictionResult.datasetInfo.description }}</b></div>
            </div>
          </div>

          <div class="pc__result-actions">
            <button class="pc__btn pc__btn--secondary">
              <svg class="pc__btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
              保存结果
            </button>
            <button class="predict__btn predict__btn--primary" @click="goToVisualization">
              <svg class="predict__btn-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"></path>
                <polyline points="3.27 6.96 12 12.01 20.73 6.96"></polyline>
                <line x1="12" y1="22.08" x2="12" y2="12"></line>
              </svg>
              3D可视化
            </button>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { predictApi, batchApi, type BatchStatus } from '@/api/predict'
import Sidebar from '@/components/Sidebar.vue'
import type { PredictionResult } from '@/types'

const selectedType = ref<'ppi' | 'dti' | 'ddi'>('dti')
const selectedInputType = ref<'pdb' | 'uniprot' | 'smiles' | 'csv'>('smiles')
const mode = ref<'single' | 'batch'>('single')

const firstInputValue = ref('')
const secondInputValue = ref('')

const firstInputError = ref('')
const secondInputError = ref('')

const confidenceThreshold = ref(70)
const detailedOutput = ref(true)
const showAdvancedOptions = ref(false)
const isLoading = ref(false)
const predictionResult = ref<PredictionResult | null>(null)
const predictError = ref('')
const router = useRouter()

const predictionTypes: Array<{ value: 'ppi' | 'dti' | 'ddi', label: string, icon: string, description: string }> = [
  { value: 'ppi', label: 'PPI预测', icon: '🔬', description: '蛋白质-蛋白质相互作用' },
  { value: 'dti', label: 'DTI预测', icon: '💊', description: '药物-靶点相互作用' },
  { value: 'ddi', label: 'DDI预测', icon: '⚗️', description: '药物-药物相互作用' }
]

const isValidInput = computed(() => {
  return firstInputValue.value.trim() !== '' && secondInputValue.value.trim() !== '' && 
         !firstInputError.value && !secondInputError.value
})

const SMILES_REGEX = /^[A-Za-z0-9@+\-\[\]\(\)\{\}.=#$%&\/\\<>~`'*:;]+$/
const PDB_REGEX = /^[0-9A-Za-z]{4}$/
const UNIPROT_REGEX = /^[A-Z0-9]{6,10}$/

const validateSMILES = (value: string): string => {
  if (!value) return ''
  if (value.length < 3) return 'SMILES表达式过短'
  if (!SMILES_REGEX.test(value)) return 'SMILES格式无效，请检查输入'
  return ''
}

const validatePDB = (value: string): string => {
  if (!value) return ''
  if (!PDB_REGEX.test(value)) return 'PDB ID应为4位字母数字组合'
  return ''
}

const validateUniProt = (value: string): string => {
  if (!value) return ''
  if (!UNIPROT_REGEX.test(value)) return 'UniProt ID格式无效'
  return ''
}

const validateFirstInput = () => {
  if (selectedType.value === 'dti') {
    firstInputError.value = validateSMILES(firstInputValue.value)
  } else if (selectedType.value === 'ddi') {
    firstInputError.value = validateSMILES(firstInputValue.value)
  } else if (selectedType.value === 'ppi') {
    if (selectedInputType.value === 'pdb') {
      firstInputError.value = validatePDB(firstInputValue.value)
    } else if (selectedInputType.value === 'uniprot') {
      firstInputError.value = validateUniProt(firstInputValue.value)
    } else {
      firstInputError.value = ''
    }
  }
}

const validateSecondInput = () => {
  if (selectedType.value === 'dti') {
    if (selectedInputType.value === 'pdb') {
      secondInputError.value = validatePDB(secondInputValue.value)
    } else if (selectedInputType.value === 'uniprot') {
      secondInputError.value = validateUniProt(secondInputValue.value)
    } else {
      secondInputError.value = ''
    }
  } else if (selectedType.value === 'ddi') {
    secondInputError.value = validateSMILES(secondInputValue.value)
  } else if (selectedType.value === 'ppi') {
    if (selectedInputType.value === 'pdb') {
      secondInputError.value = validatePDB(secondInputValue.value)
    } else if (selectedInputType.value === 'uniprot') {
      secondInputError.value = validateUniProt(secondInputValue.value)
    } else {
      secondInputError.value = ''
    }
  }
}

const clearFirstInput = () => {
  firstInputValue.value = ''
  firstInputError.value = ''
}

const clearSecondInput = () => {
  secondInputValue.value = ''
  secondInputError.value = ''
}

const getFormSubtitle = () => {
  const subtitles: Record<string, string> = {
    ppi: '分析两个蛋白质之间的相互作用关系',
    dti: '预测药物与靶点蛋白的结合能力',
    ddi: '分析两种药物之间的相互作用'
  }
  return subtitles[selectedType.value] || ''
}

const getFirstInputLabel = () => {
  const labels: Record<string, string> = {
    dti: '药物 (SMILES)',
    ddi: '药物 A (SMILES)',
    ppi: `蛋白质 A (${selectedInputType.value === 'pdb' ? 'PDB ID' : 'UniProt ID'})`
  }
  return labels[selectedType.value] || '输入A'
}

const getSecondInputLabel = () => {
  const labels: Record<string, string> = {
    dti: `靶点 (${selectedInputType.value === 'pdb' ? 'PDB ID' : 'UniProt ID'})`,
    ddi: '药物 B (SMILES)',
    ppi: `蛋白质 B (${selectedInputType.value === 'pdb' ? 'PDB ID' : 'UniProt ID'})`
  }
  return labels[selectedType.value] || '输入B'
}

const getFirstInputPlaceholder = () => {
  const placeholders: Record<string, string> = {
    dti: '输入药物SMILES表达式，如: CC(=O)OC1=CC=CC=C1C(=O)O',
    ddi: '输入药物A的SMILES表达式',
    ppi: selectedInputType.value === 'pdb' ? '输入PDB ID，如: 6M0J' : '输入UniProt ID，如: P0DTC2'
  }
  return placeholders[selectedType.value] || ''
}

const getSecondInputPlaceholder = () => {
  const placeholders: Record<string, string> = {
    dti: selectedInputType.value === 'pdb' ? '输入PDB ID，如: 6M0J' : '输入UniProt ID，如: P0DTC2',
    ddi: '输入药物B的SMILES表达式',
    ppi: selectedInputType.value === 'pdb' ? '输入PDB ID，如: 6LU7' : '输入UniProt ID，如: P05067'
  }
  return placeholders[selectedType.value] || ''
}

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
    hydrogen_bond: '#10b981',
    hydrophobic: '#f59e0b',
    ionic: '#ef4444',
    pi_pi: '#8b5cf6',
    metal: '#3b82f6'
  }
  return colors[type] || '#64748b'
}

const getInteractionBgColor = (type: string): string => {
  const colors: Record<string, string> = {
    hydrogen_bond: 'rgba(16, 185, 129, 0.1)',
    hydrophobic: 'rgba(245, 158, 11, 0.1)',
    ionic: 'rgba(239, 68, 68, 0.1)',
    pi_pi: 'rgba(139, 92, 246, 0.1)',
    metal: 'rgba(59, 130, 246, 0.1)'
  }
  return colors[type] || 'rgba(100, 116, 139, 0.1)'
}

const getInteractionIcon = (type: string): string => {
  const icons: Record<string, string> = {
    hydrogen_bond: 'H',
    hydrophobic: '◉',
    ionic: '⚡',
    pi_pi: 'π',
    metal: '⊕'
  }
  return icons[type] || '●'
}

const getInteractionTypeName = (type: string): string => {
  const types: Record<string, string> = {
    hydrogen_bond: '氢键',
    hydrophobic: '疏水',
    ionic: '离子',
    pi_pi: 'π-π堆积',
    metal: '金属配位'
  }
  return types[type] || type
}

const formatTime = (dateStr: string): string => {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', { 
    month: 'short', 
    day: 'numeric', 
    hour: '2-digit', 
    minute: '2-digit' 
  })
}

const formatNumber = (num: number): string => {
  if (num >= 10000) {
    return (num / 10000).toFixed(1) + '万'
  }
  return num.toLocaleString()
}

const handleDemoPredict = async () => {
  // 根据当前预测类型填充演示输入
  if (selectedType.value === 'dti') {
    firstInputValue.value = 'C(=O)(C(=O)O)NC(CCC(=O)O)C(=O)O'
    secondInputValue.value = 'P0DTC2'
  } else if (selectedType.value === 'ppi') {
    firstInputValue.value = 'MGLGLGQ'
    secondInputValue.value = 'MVHLTEK'
  } else {
    firstInputValue.value = 'CC(=O)OC1=CC=CC=C1C(=O)O'
    secondInputValue.value = 'C1CCCCC1'
  }
  firstInputError.value = ''
  secondInputError.value = ''
  
  await handlePredict()
}

const handlePredict = async () => {
  if (!isValidInput.value) return
  
  isLoading.value = true
  predictError.value = ''
  predictionResult.value = null
  
  try {
    let response
    if (selectedType.value === 'dti') {
      response = await predictApi.predictDTI({
        smiles: firstInputValue.value.trim(),
        targetId: secondInputValue.value.trim()
      })
    } else if (selectedType.value === 'ppi') {
      response = await predictApi.predictPPI({
        proteinA: firstInputValue.value.trim(),
        proteinB: secondInputValue.value.trim()
      })
    } else {
      response = await predictApi.predictDDI({
        drugASmiles: firstInputValue.value.trim(),
        drugBSmiles: secondInputValue.value.trim()
      })
    }
    predictionResult.value = response as unknown as PredictionResult
  } catch (error: unknown) {
    predictError.value = error instanceof Error ? error.message : '预测失败，请稍后重试'
  } finally {
    isLoading.value = false
  }
}

// ================= 批量预测 =================
const batchFile = ref<File | null>(null)
const batchAlgoType = ref('DTI')
const batchUploading = ref(false)
const batchError = ref('')
const batchStatus = ref<BatchStatus | null>(null)
let batchTimer: number | null = null

const handleBatchFileChange = (e: Event) => {
  const input = e.target as HTMLInputElement
  batchFile.value = input.files?.[0] || null
  batchError.value = ''
}

const handleBatchUpload = async () => {
  if (!batchFile.value) return
  batchUploading.value = true
  batchError.value = ''
  batchStatus.value = null
  try {
    const result = await batchApi.upload(batchFile.value, batchAlgoType.value)
    startBatchPolling(result.batchId)
  } catch (error: unknown) {
    batchError.value = error instanceof Error ? error.message : '批量上传失败'
  } finally {
    batchUploading.value = false
  }
}

const startBatchPolling = (batchId: string) => {
  if (batchTimer) clearInterval(batchTimer)
  batchTimer = window.setInterval(async () => {
    try {
      const status = await batchApi.getStatus(batchId)
      batchStatus.value = status
      if (status.status === 'SUCCESS' || status.status === 'FAIL') {
        if (batchTimer) clearInterval(batchTimer)
        batchTimer = null
      }
    } catch {
      if (batchTimer) clearInterval(batchTimer)
      batchTimer = null
    }
  }, 2000)
}

const handleBatchDownload = async () => {
  if (!batchStatus.value) return
  try {
    await batchApi.download(batchStatus.value.batchId)
  } catch (error: unknown) {
    batchError.value = error instanceof Error ? error.message : '下载失败'
  }
}

onUnmounted(() => {
  if (batchTimer) clearInterval(batchTimer)
})

// ================= 3D 可视化导航 =================
const goToVisualization = () => {
  if (!predictionResult.value) return
  router.push({
    path: '/visualization',
    query: {
      id: String(predictionResult.value.id),
      targetName: predictionResult.value.targetName,
      targetId: predictionResult.value.targetId,
    },
  })
}
</script>

<style lang="scss" scoped>
.predict {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
}

.predict__content {
  flex: 1;
  margin-left: $sidebar-width;
  padding: $spacing-xl;
}

.predict__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-2xl;
  padding: $spacing-xl;
  background: linear-gradient(135deg, $primary-color 0%, $primary-light 100%);
  border-radius: $border-radius-xl;
  color: #ffffff;
}

.predict__header-left {
  flex: 1;
}

.predict__title {
  font-size: $font-size-2xl;
  font-weight: 700;
  margin-bottom: $spacing-xs;
}

.predict__subtitle {
  font-size: $font-size-sm;
  opacity: 0.8;
}

.predict__header-stats {
  display: flex;
  gap: $spacing-xl;
}

.predict__stat-item {
  text-align: center;
}

.predict__stat-value {
  display: block;
  font-size: $font-size-xl;
  font-weight: 700;
}

.predict__stat-label {
  font-size: $font-size-xs;
  opacity: 0.8;
}

.predict__type-selector {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-lg;
  margin-bottom: $spacing-2xl;
}

.predict__type-card {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  background: $bg-primary;
  padding: $spacing-lg;
  border-radius: $border-radius-lg;
  cursor: pointer;
  border: 2px solid transparent;
  transition: all $transition-normal;
  
  &:hover {
    border-color: $border-color;
    box-shadow: $shadow-md;
    transform: translateY(-2px);
  }
  
  &--active {
    border-color: $primary-color;
    background: rgba($primary-color, 0.03);
    box-shadow: $shadow-md;
  }
}

.predict__type-icon-wrapper {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $bg-secondary;
  border-radius: $border-radius-md;
  flex-shrink: 0;
}

.predict__type-icon {
  font-size: 24px;
}

.predict__type-info {
  flex: 1;
}

.predict__type-name {
  display: block;
  font-size: $font-size-base;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 2px;
}

.predict__type-desc {
  display: block;
  font-size: $font-size-xs;
  color: $text-muted;
}

.predict__type-arrow {
  color: $text-muted;
}

.predict__check-icon {
  width: 16px;
  height: 16px;
  color: $success-color;
}

.predict__form {
  max-width: 800px;
  margin: 0 auto $spacing-2xl;
}

.predict__form-card {
  background: $bg-primary;
  border-radius: $border-radius-xl;
  padding: $spacing-2xl;
  border: 1px solid $border-light;
  box-shadow: $shadow-sm;
}

.predict__form-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-2xl;
  padding-bottom: $spacing-xl;
  border-bottom: 1px solid $border-light;
}

.predict__form-header-left {
  flex: 1;
}

.predict__form-title {
  font-size: $font-size-xl;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-xs;
}

.predict__form-subtitle {
  font-size: $font-size-sm;
  color: $text-muted;
}

.predict__demo-btn {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-md;
  background: rgba($info-color, 0.1);
  color: $info-color;
  border: none;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  cursor: pointer;
  transition: all $transition-fast;
  
  &:hover {
    background: rgba($info-color, 0.15);
  }
}

.predict__demo-icon {
  width: 14px;
  height: 14px;
}

.predict__input-wrapper {
  margin-bottom: $spacing-xl;
}

.predict__input-column {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: $spacing-md;
  align-items: start;
}

.predict__input-group {
  &--single {
    width: 100%;
  }
}

.predict__input-group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-sm;
}

.predict__label {
  font-size: $font-size-sm;
  font-weight: 500;
  color: $text-primary;
}

.predict__input-clear {
  background: transparent;
  border: none;
  color: $text-muted;
  cursor: pointer;
  padding: 0;
  
  svg {
    width: 14px;
    height: 14px;
  }
  
  &:hover {
    color: $text-secondary;
  }
}

.predict__input-type-group {
  display: flex;
  gap: $spacing-xs;
}

.predict__input-type-btn {
  padding: $spacing-xs $spacing-sm;
  border: 1px solid $border-color;
  border-radius: $border-radius-sm;
  background: $bg-secondary;
  color: $text-secondary;
  font-size: $font-size-xs;
  cursor: pointer;
  transition: all $transition-fast;
  
  &:hover {
    background: $bg-tertiary;
  }
  
  &--active {
    background: $primary-color;
    color: #ffffff;
    border-color: $primary-color;
  }
}

.predict__input {
  width: 100%;
  padding: $spacing-md;
  border: 1.5px solid $border-color;
  border-radius: $border-radius-md;
  font-size: $font-size-base;
  background: $bg-secondary;
  transition: all $transition-fast;
  
  &:focus {
    outline: none;
    border-color: $primary-color;
    box-shadow: 0 0 0 3px rgba($primary-color, 0.05);
  }
  
  &--error {
    border-color: $error-color;
    
    &:focus {
      box-shadow: 0 0 0 3px rgba($error-color, 0.1);
    }
  }
}

.predict__input-error {
  display: block;
  font-size: $font-size-xs;
  color: $error-color;
  margin-top: $spacing-xs;
}

.predict__input-valid {
  display: block;
  font-size: $font-size-xs;
  color: $success-color;
  margin-top: $spacing-xs;
}

.predict__input-divider {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-lg 0;
}

.predict__divider-text {
  font-size: $font-size-xs;
  font-weight: 600;
  color: $text-muted;
  padding: $spacing-xs $spacing-sm;
  background: $bg-secondary;
  border-radius: $border-radius-sm;
}

.predict__options {
  margin-bottom: $spacing-xl;
}

.predict__options-toggle {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  background: transparent;
  border: none;
  color: $primary-color;
  font-size: $font-size-sm;
  cursor: pointer;
  padding: $spacing-xs 0;
}

.predict__toggle-icon {
  width: 14px;
  height: 14px;
  transition: transform $transition-fast;
  
  &--rotated {
    transform: rotate(180deg);
  }
}

.predict__advanced-options {
  margin-top: $spacing-md;
  padding: $spacing-xl;
  background: $bg-secondary;
  border-radius: $border-radius-lg;
}

.predict__advanced-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-xl;
}

.predict__advanced-option {
  &:last-child {
    margin-bottom: 0;
  }
}

.predict__advanced-option-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-sm;
}

.predict__range-input {
  width: 100%;
  height: 6px;
  border-radius: 3px;
  background: $bg-tertiary;
  appearance: none;
  cursor: pointer;
  
  &::-webkit-slider-thumb {
    appearance: none;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: $primary-color;
    cursor: pointer;
    border: 3px solid #ffffff;
    box-shadow: $shadow-sm;
    transition: transform $transition-fast;
    
    &:hover {
      transform: scale(1.1);
    }
  }
}

.predict__range-labels {
  display: flex;
  justify-content: space-between;
  font-size: $font-size-xs;
  color: $text-muted;
  margin-top: $spacing-xs;
}

.predict__range-value {
  font-size: $font-size-sm;
  color: $text-primary;
  font-weight: 600;
  width: 50px;
  text-align: right;
}

.predict__checkbox-wrapper {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  cursor: pointer;
}

.predict__checkbox {
  display: none;
}

.predict__checkbox-custom {
  width: 18px;
  height: 18px;
  border: 2px solid $border-color;
  border-radius: $border-radius-sm;
  position: relative;
  transition: all $transition-fast;
  
  &::before {
    content: '';
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%) rotate(-45deg);
    width: 6px;
    height: 10px;
    border-left: 2px solid transparent;
    border-bottom: 2px solid transparent;
    transition: all $transition-fast;
  }
}

.predict__checkbox:checked + .predict__checkbox-custom {
  background: $primary-color;
  border-color: $primary-color;
  
  &::before {
    border-left-color: #ffffff;
    border-bottom-color: #ffffff;
  }
}

.predict__advanced-option-desc {
  font-size: $font-size-xs;
  color: $text-muted;
}

.predict__actions {
  display: flex;
  gap: $spacing-md;
}

.predict__error {
  margin-top: $spacing-md;
  padding: $spacing-sm $spacing-md;
  border-radius: $border-radius-md;
  background: rgba(239, 68, 68, 0.1);
  color: $error-color;
  font-size: $font-size-sm;
  text-align: center;
}

.predict__batch {
  margin-top: $spacing-xl;
}

.predict__batch-card {
  background: $bg-primary;
  border: 1px solid $border-light;
  border-radius: $border-radius-xl;
  padding: $spacing-xl;
}

.predict__batch-header {
  margin-bottom: $spacing-lg;
}

.predict__batch-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-xs;
}

.predict__batch-subtitle {
  font-size: $font-size-sm;
  color: $text-muted;
}

.predict__batch-row {
  display: flex;
  gap: $spacing-md;
  align-items: center;
}

.predict__batch-select {
  padding: $spacing-sm $spacing-md;
  border: 1px solid $border-color;
  border-radius: $border-radius-md;
  font-size: $font-size-base;
  background: $bg-tertiary;
}

.predict__batch-file {
  flex: 1;
  padding: $spacing-sm;
  font-size: $font-size-sm;
}

.predict__batch-status {
  margin-top: $spacing-lg;
  padding: $spacing-md;
  background: $bg-tertiary;
  border-radius: $border-radius-md;
}

.predict__batch-status-row {
  display: flex;
  justify-content: space-between;
  font-size: $font-size-sm;
  color: $text-secondary;
  margin-bottom: $spacing-sm;
}

.predict__batch-progress-track {
  width: 100%;
  height: 8px;
  background: $border-color;
  border-radius: $border-radius-md;
  overflow: hidden;
  margin-bottom: $spacing-md;
}

.predict__batch-progress-fill {
  height: 100%;
  background: $primary-color;
  border-radius: $border-radius-md;
  transition: width 0.3s ease;
}

.predict__btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: $spacing-md;
  border-radius: $border-radius-md;
  font-size: $font-size-base;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all $transition-fast;
  
  &--primary {
    background: $primary-color;
    color: #ffffff;
    
    &:hover:not(:disabled) {
      background: $primary-dark;
      transform: translateY(-1px);
      box-shadow: $shadow-md;
    }
    
    &:disabled {
      background: $border-color;
      cursor: not-allowed;
    }
  }
  
  &--outline {
    background: transparent;
    color: $primary-color;
    border: 1px solid $border-color;
    
    &:hover {
      background: rgba($primary-color, 0.05);
    }
  }
  
  &--secondary {
    background: $bg-secondary;
    color: $text-primary;
    
    &:hover {
      background: $bg-tertiary;
    }
  }
}

.predict__btn-icon {
  width: 16px;
  height: 16px;
  
  &--loading {
    animation: spin 1s linear infinite;
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.predict__loading-spinner {
  stroke-dasharray: 283;
  stroke-dashoffset: 70;
  animation: spin 1s linear infinite;
}

.predict__result {
  max-width: 900px;
  margin: 0 auto;
}

.predict__result-card {
  background: $bg-primary;
  border-radius: $border-radius-xl;
  padding: $spacing-2xl;
  border: 1px solid $border-light;
  box-shadow: $shadow-sm;
}

.predict__result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-xl;
}

.predict__result-header-right {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.predict__result-title {
  font-size: $font-size-xl;
  font-weight: 600;
  color: $text-primary;
}

.predict__result-badge {
  font-size: $font-size-xs;
  font-weight: 500;
  padding: 4px 12px;
  border-radius: 100px;
  background: rgba($info-color, 0.1);
  color: $info-color;
}

.predict__result-time {
  font-size: $font-size-xs;
  color: $text-muted;
}

.predict__result-summary {
  margin-bottom: $spacing-xl;
}

.predict__result-main {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: $spacing-xl;
}

.predict__result-score-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl;
  background: linear-gradient(135deg, $bg-secondary 0%, $bg-tertiary 100%);
  border-radius: $border-radius-lg;
}

.predict__score-label {
  font-size: $font-size-sm;
  color: $text-muted;
  margin-bottom: $spacing-md;
}

.predict__score-ring {
  position: relative;
  width: 140px;
  height: 140px;
  margin-bottom: $spacing-md;
}

.predict__score-ring-svg {
  width: 100%;
  height: 100%;
}

.predict__score-ring-bg {
  stroke-width: 10;
}

.predict__score-ring-progress {
  stroke-width: 10;
  transition: stroke-dasharray 0.5s ease;
}

.predict__score-value {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 32px;
  font-weight: 700;
  color: $text-primary;
}

.predict__score-badge {
  font-size: $font-size-xs;
  font-weight: 500;
  padding: 4px 12px;
  border-radius: 100px;
}

.predict__result-details {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.predict__result-detail-item {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md;
  background: $bg-secondary;
  border-radius: $border-radius-md;
  
  &--highlight {
    background: rgba($primary-color, 0.05);
    border-left: 3px solid $primary-color;
  }
}

.predict__detail-icon {
  width: 20px;
  height: 20px;
  color: $text-muted;
  flex-shrink: 0;
}

.predict__detail-content {
  flex: 1;
}

.predict__detail-label {
  display: block;
  font-size: $font-size-xs;
  color: $text-muted;
  margin-bottom: 2px;
}

.predict__detail-value {
  font-size: $font-size-base;
  font-weight: 600;
  color: $text-primary;
  
  &--highlight {
    font-size: $font-size-lg;
    color: $primary-color;
  }
}

.predict__detail-unit {
  font-size: $font-size-sm;
  font-weight: 400;
  color: $text-muted;
}

.predict__result-section {
  margin-bottom: $spacing-xl;
  padding-top: $spacing-xl;
  border-top: 1px solid $border-light;
  
  &:first-of-type {
    border-top: none;
    padding-top: 0;
  }
}

.predict__result-section-title {
  font-size: $font-size-base;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-lg;
}

.predict__interaction-list {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.predict__interaction-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  background: $bg-secondary;
  border-radius: $border-radius-md;
}

.predict__interaction-type {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-xs;
  font-weight: 500;
  padding: 4px 8px;
  border-radius: $border-radius-sm;
}

.predict__interaction-icon {
  font-size: 10px;
}

.predict__interaction-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.predict__interaction-residue {
  font-size: $font-size-xs;
  color: $text-primary;
  font-weight: 500;
}

.predict__interaction-distance {
  font-size: $font-size-xs;
  color: $text-muted;
}

.predict__dataset-info {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-md;
}

.predict__dataset-item {
  padding: $spacing-md;
  background: $bg-secondary;
  border-radius: $border-radius-md;
}

.predict__dataset-label {
  display: block;
  font-size: $font-size-xs;
  color: $text-muted;
  margin-bottom: $spacing-xs;
}

.predict__dataset-value {
  font-size: $font-size-sm;
  font-weight: 600;
  color: $text-primary;
}

.predict__result-actions {
  display: flex;
  gap: $spacing-md;
  padding-top: $spacing-xl;
  border-top: 1px solid $border-light;
}
</style>

<style lang="scss" scoped>
/* ===================== 预测中心（重构版） ===================== */
.pc {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
  padding-top: $header-height;
}

.pc__main {
  flex: 1;
  padding: $spacing-xl;
  max-width: 1080px;
}

/* ---------- 页头 ---------- */
.pc__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $spacing-lg;
  padding: $spacing-lg $spacing-xl;
  background: $bg-primary;
  border: 1px solid $border-color;
  border-left: 4px solid $accent-color;
  border-radius: $border-radius-lg;
  box-shadow: $shadow-sm;
  margin-bottom: $spacing-lg;
}

.pc__title {
  font-size: $font-size-xl;
  font-weight: 700;
  color: $text-primary;
}

.pc__subtitle {
  margin-top: $spacing-xs;
  font-size: $font-size-sm;
  color: $text-muted;
}

.pc__stats {
  display: flex;
  gap: $spacing-lg;
}

.pc__stat {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  b {
    font-size: $font-size-xl;
    color: $primary-color;
  }
  em {
    font-style: normal;
    font-size: $font-size-xs;
    color: $text-muted;
  }
}

/* ---------- Tabs ---------- */
.pc__tabs {
  display: inline-flex;
  background: $bg-tertiary;
  border-radius: $border-radius-md;
  padding: 4px;
  margin-bottom: $spacing-lg;
  gap: 4px;
}

.pc__tab {
  padding: $spacing-sm $spacing-lg;
  border: none;
  background: transparent;
  border-radius: $border-radius-sm;
  font-size: $font-size-base;
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

/* ---------- 面板 / 卡片 ---------- */
.pc__panel {
  margin-bottom: $spacing-lg;
}

.pc__card {
  background: $bg-primary;
  border: 1px solid $border-color;
  border-radius: $border-radius-lg;
  padding: $spacing-xl;
  box-shadow: $shadow-sm;
}

.pc__card-head {
  margin-bottom: $spacing-lg;
}

.pc__card-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
}

.pc__card-sub {
  margin-top: $spacing-xs;
  font-size: $font-size-sm;
  color: $text-muted;
}

/* ---------- 类型选择 ---------- */
.pc__types {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-md;
}

.pc__type {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-md;
  border: 1px solid $border-color;
  border-radius: $border-radius-md;
  background: $bg-primary;
  cursor: pointer;
  transition: $transition-normal;
  color: $text-secondary;
  &:hover { border-color: $accent-light; }
  &--active {
    border-color: $accent-color;
    background: rgba(59, 130, 246, 0.06);
    color: $accent-color;
    box-shadow: 0 0 0 1px $accent-color;
  }
}

.pc__type-icon {
  font-size: $font-size-xl;
}

.pc__type-name {
  font-weight: 600;
  font-size: $font-size-base;
}

.pc__form-subtitle {
  margin: $spacing-lg 0 $spacing-md;
  font-size: $font-size-sm;
  color: $text-muted;
}

/* ---------- 输入区 ---------- */
.pc__form {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: $spacing-md;
  align-items: start;
}

.pc__input-block {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.pc__input-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pc__label {
  font-size: $font-size-sm;
  font-weight: 600;
  color: $text-secondary;
}

.pc__clear {
  border: none;
  background: transparent;
  color: $text-muted;
  cursor: pointer;
  font-size: $font-size-xs;
  padding: 2px 6px;
  border-radius: $border-radius-sm;
  &:hover { color: $error-color; background: rgba(239, 68, 68, 0.08); }
}

.pc__input {
  width: 100%;
  padding: $spacing-md;
  border: 1px solid $border-color;
  border-radius: $border-radius-md;
  font-size: $font-size-base;
  color: $text-primary;
  background: $bg-primary;
  transition: $transition-fast;
  &::placeholder { color: $text-light; }
  &:focus {
    outline: none;
    border-color: $accent-color;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
  }
  &--error {
    border-color: $error-color;
    &:focus { box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.12); }
  }
}

.pc__hint {
  min-height: 18px;
  font-size: $font-size-xs;
}

.pc__err { color: $error-color; }
.pc__ok { color: $success-color; }

.pc__vs {
  display: flex;
  align-items: center;
  align-self: center;
  margin-top: 26px;
  span {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: $bg-tertiary;
    color: $text-muted;
    font-size: $font-size-sm;
    font-weight: 700;
  }
}

/* ---------- 高级选项 ---------- */
.pc__advanced {
  margin-top: $spacing-lg;
  border-top: 1px dashed $border-color;
  padding-top: $spacing-md;
}

.pc__advanced-toggle {
  border: none;
  background: transparent;
  color: $text-secondary;
  font-size: $font-size-sm;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  &:hover { color: $primary-color; }
}

.pc__chevron {
  display: inline-block;
  transition: $transition-fast;
  &--open { transform: rotate(180deg); }
}

.pc__advanced-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-lg;
  margin-top: $spacing-md;
  padding: $spacing-md;
  background: $bg-tertiary;
  border-radius: $border-radius-md;
}

.pc__advanced-item {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  &--row {
    flex-direction: row;
    align-items: center;
    gap: $spacing-sm;
    cursor: pointer;
  }
}

.pc__advanced-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pc__range {
  width: 100%;
  accent-color: $accent-color;
}

.pc__range-val {
  font-size: $font-size-sm;
  font-weight: 600;
  color: $accent-color;
}

.pc__checkbox {
  display: inline-flex;
  input { display: none; }
  .pc__checkbox-box {
    width: 18px;
    height: 18px;
    border: 1px solid $border-color;
    border-radius: 4px;
    position: relative;
    transition: $transition-fast;
  }
  input:checked + .pc__checkbox-box {
    background: $accent-color;
    border-color: $accent-color;
    &::after {
      content: '';
      position: absolute;
      left: 5px;
      top: 2px;
      width: 5px;
      height: 9px;
      border: solid #fff;
      border-width: 0 2px 2px 0;
      transform: rotate(45deg);
    }
  }
}

.pc__desc {
  font-size: $font-size-xs;
  color: $text-muted;
}

/* ---------- 操作按钮 ---------- */
.pc__actions {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-md;
  margin-top: $spacing-lg;
}

.pc__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  border-radius: $border-radius-md;
  border: none;
  font-size: $font-size-base;
  cursor: pointer;
  transition: $transition-fast;
  &--primary {
    background: $primary-color;
    color: #fff;
    &:hover:not(:disabled) { background: $primary-light; }
  }
  &--secondary {
    background: $bg-tertiary;
    color: $text-secondary;
    border: 1px solid $border-color;
    &:hover:not(:disabled) { background: $border-light; }
  }
  &--ghost {
    background: transparent;
    color: $accent-color;
    &:hover { background: rgba(59, 130, 246, 0.08); }
  }
  &--block { width: 100%; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

.pc__btn-icon {
  width: 16px;
  height: 16px;
}

.pc__spin {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: pc-spin 0.6s linear infinite;
}

@keyframes pc-spin {
  to { transform: rotate(360deg); }
}

.pc__error {
  margin-top: $spacing-md;
  padding: $spacing-sm $spacing-md;
  border-radius: $border-radius-md;
  background: rgba(239, 68, 68, 0.08);
  color: $error-color;
  font-size: $font-size-sm;
  text-align: center;
}

/* ---------- 批量预测 ---------- */
.pc__batch-body {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.pc__select {
  padding: $spacing-sm $spacing-md;
  border: 1px solid $border-color;
  border-radius: $border-radius-md;
  font-size: $font-size-base;
  background: $bg-primary;
  color: $text-primary;
  align-self: flex-start;
  min-width: 160px;
  &:focus { outline: none; border-color: $accent-color; }
}

.pc__dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: $spacing-xl;
  border: 2px dashed $border-color;
  border-radius: $border-radius-md;
  background: $bg-secondary;
  cursor: pointer;
  text-align: center;
  transition: $transition-normal;
  &:hover { border-color: $accent-light; background: rgba(59, 130, 246, 0.04); }
  &--has { border-style: solid; border-color: $success-color; background: rgba(16, 185, 129, 0.05); }
}

.pc__file {
  display: none;
}

.pc__dropzone-icon {
  font-size: $font-size-3xl;
}

.pc__dropzone-text {
  font-size: $font-size-base;
  font-weight: 600;
  color: $text-primary;
}

.pc__dropzone-sub {
  font-size: $font-size-xs;
  color: $text-muted;
}

.pc__batch-status {
  margin-top: $spacing-lg;
  padding: $spacing-md;
  background: $bg-tertiary;
  border-radius: $border-radius-md;
}

.pc__batch-status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: $font-size-sm;
  margin-bottom: $spacing-sm;
}

.pc__batch-id {
  color: $text-secondary;
  font-family: monospace;
}

.pc__batch-pct {
  font-weight: 700;
  color: $text-primary;
}

.pc__batch-state {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: $font-size-xs;
  font-weight: 600;
  &--pending { background: rgba(148, 163, 184, 0.15); color: $text-muted; }
  &--processing { background: rgba(59, 130, 246, 0.12); color: $info-color; }
  &--success { background: rgba(16, 185, 129, 0.12); color: $success-color; }
  &--fail { background: rgba(239, 68, 68, 0.12); color: $error-color; }
}

.pc__progress {
  width: 100%;
  height: 8px;
  background: $border-color;
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: $spacing-md;
}

.pc__progress-fill {
  height: 100%;
  background: linear-gradient(90deg, $accent-color, $accent-light);
  border-radius: 999px;
  transition: width 0.3s ease;
}

/* ---------- 结果 ---------- */
.pc__result-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: $spacing-lg;
}

.pc__result-time {
  display: block;
  margin-top: $spacing-xs;
  font-size: $font-size-xs;
  color: $text-muted;
}

.pc__badge {
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.1);
  color: $accent-color;
  font-size: $font-size-xs;
  font-weight: 600;
}

.pc__result-grid {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: $spacing-xl;
  padding: $spacing-lg;
  background: $bg-secondary;
  border-radius: $border-radius-md;
}

.pc__score {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
}

.pc__score-ring {
  position: relative;
  width: 120px;
  height: 120px;
}

.pc__score-svg {
  width: 100%;
  height: 100%;
}

.pc__score-num {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $font-size-xl;
  font-weight: 700;
  color: $text-primary;
}

.pc__score-label {
  font-size: $font-size-sm;
  color: $text-muted;
}

.pc__score-level {
  font-size: $font-size-sm;
  font-weight: 600;
}

.pc__details {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: $spacing-md;
}

.pc__detail {
  display: flex;
  align-items: baseline;
  gap: $spacing-md;
  &--accent .pc__detail-val { color: $accent-color; font-size: $font-size-xl; }
}

.pc__detail-label {
  width: 90px;
  font-size: $font-size-sm;
  color: $text-muted;
}

.pc__detail-val {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  small { font-size: $font-size-xs; font-weight: 400; color: $text-muted; }
}

.pc__section {
  margin-top: $spacing-lg;
}

.pc__section-title {
  font-size: $font-size-base;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-md;
  padding-bottom: $spacing-sm;
  border-bottom: 1px solid $border-light;
}

.pc__interactions {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-md;
}

.pc__interaction {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border: 1px solid $border-color;
  border-radius: $border-radius-md;
  background: $bg-primary;
}

.pc__interaction-tag {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: $font-size-xs;
  font-weight: 600;
}

.pc__interaction-meta {
  font-size: $font-size-sm;
  color: $text-secondary;
}

.pc__dataset {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-md;
}

.pc__dataset-item {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  padding: $spacing-md;
  background: $bg-tertiary;
  border-radius: $border-radius-md;
  span { font-size: $font-size-xs; color: $text-muted; }
  b { font-size: $font-size-sm; color: $text-primary; }
}

.pc__result-actions {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-md;
  margin-top: $spacing-lg;
  padding-top: $spacing-lg;
  border-top: 1px solid $border-light;
}
</style>