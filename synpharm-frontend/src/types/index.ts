export interface Target {
  id: string
  name: string
  uniprotId: string
  pdbId: string
  description: string
  status: 'supported' | 'beta' | 'planned'
  geneName?: string
  organism?: string
  pdbIds?: string[]
  // ===== 精细化分类（面向专业用户） =====
  chineseName?: string     // 中文名
  targetType?: string      // 靶点类型（如 受体酪氨酸激酶 / 离子通道）
  family?: string          // 蛋白家族
  pathway?: string         // 主要通路
  diseaseArea?: string     // 疾病领域（肿瘤 / 感染 / 代谢 / 神经 / 心血管）
  relatedDiseases?: string // 相关疾病
  knownDrugs?: string      // 相关药物/干预方式
}

export interface InputData {
  type: 'pdb' | 'uniprot' | 'smiles' | 'csv'
  value: string
  fileName?: string
}

export interface PredictionResult {
  id: string | number
  targetId: string
  targetName: string
  ligandSmiles: string
  bindingAffinity: number
  confidenceScore: number
  confidenceLevel: 'high' | 'medium' | 'low'
  interactions: Interaction[]
  createdAt: string
  datasetInfo: DatasetInfo
}

export interface Interaction {
  type: 'hydrogen_bond' | 'hydrophobic' | 'ionic' | 'pi_pi' | 'metal'
  residueName: string
  residueNumber: number
  distance: number
}

export interface DatasetInfo {
  name: string
  size: number
  description: string
  source: 'internal'
}

export interface Task {
  id: string | number
  taskNo?: string
  name?: string
  type?: string
  /** 后端 predict_type（dti/ppi/ddi） */
  predictType?: string
  status: 'pending' | 'running' | 'completed' | 'failed' | 'cancelled'
  progress: number
  input?: InputData
  resultId?: string
  createdAt: string
  updatedAt?: string
}

/**
 * 任务详情（对应后端 GET /api/tasks/{id} 返回的 PredictTask 实体）。
 * 字段与后端 PredictTask 对齐，全部可选以兼容未返回的字段。
 */
export interface TaskDetail {
  id: number | string
  taskNo?: string
  userId?: number | string
  /** 预测/算法类型（dti/ppi/ddi） */
  predictType?: string
  inputType?: string
  inputValue?: string
  fileUrl?: string
  status?: string
  progress?: number
  errorMessage?: string
  aiTaskId?: string
  createdAt?: string
  startedAt?: string
  completedAt?: string
}

export interface ValidationResult {
  inputType: string
  inputValue: string
  isValid: boolean
  message: string
  suggestions?: string[]
}

/** 表单字段验证结果（简单版，用于登录/注册等表单验证） */
export interface FieldValidationResult {
  valid: boolean
  message: string
}

export interface User {
  id: string
  email: string
  nickname: string
  avatar?: string
  createdAt: string
}

export interface LoginCredentials {
  loginType: 'qq_email' | 'guest' | 'password' | 'phone'
  email?: string
  captcha?: string
  password?: string
  phone?: string
}

export interface RegisterCredentials {
  email: string
  nickname: string
  password: string
  captcha: string
}

export interface AuthState {
  user: User | null
  isLoggedIn: boolean
  token: string | null
}

export interface RegisterResult {
  success: boolean
  message: string
  user?: User
}

export interface LoginResult {
  success: boolean
  message: string
  user?: User
  token?: string
}