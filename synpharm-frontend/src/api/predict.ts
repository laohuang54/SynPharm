import { request, baseURL } from '@/utils/request'
import type { PredictionResult, Task } from '@/types'

export interface DTIPredictRequest {
  smiles: string
  targetId: string
}

export interface PPIPredictRequest {
  proteinA: string
  proteinB: string
}

export interface DDIPredictRequest {
  drugASmiles: string
  drugBSmiles: string
}

/** 预测结果响应（与后端 PredictResultResponse 对齐） */
export interface PredictResultResponse {
  id?: string | number
  algoType?: string
  targetId?: string
  targetName?: string
  ligandSmiles?: string
  bindingAffinity?: number
  confidenceScore?: number
  confidenceLevel?: string
  interactions?: Array<{
    type?: string
    residueName?: string
    residueNumber?: string
    distance?: number
  }>
  createdAt?: string
  datasetInfo?: {
    name?: string
    size?: number
    description?: string
    source?: string
  }
}

/** 分页结果结构（后端 /api/results 返回 { total, list }） */
export interface PagedResult<T> {
  total: number
  list: T[]
}

export const predictApi = {
  predictDTI(data: DTIPredictRequest): Promise<PredictResultResponse> {
    return request.post<PredictResultResponse>('/api/predict/dti', data)
  },

  predictPPI(data: PPIPredictRequest): Promise<PredictResultResponse> {
    return request.post<PredictResultResponse>('/api/predict/ppi', data)
  },

  predictDDI(data: DDIPredictRequest): Promise<PredictResultResponse> {
    return request.post<PredictResultResponse>('/api/predict/ddi', data)
  },

  /** 获取当前用户的预测历史列表（按创建时间倒序） */
  getPredictHistory(): Promise<PredictResultResponse[]> {
    return request.get<PredictResultResponse[]>('/api/predict/history')
  }
}

export const taskApi = {
  getTaskList(): Promise<Task[]> {
    return request.get<Task[]>('/api/tasks')
  },

  getTaskDetail(id: string | number): Promise<Task> {
    return request.get<Task>(`/api/tasks/${id}`)
  },

  cancelTask(id: string | number): Promise<void> {
    return request.delete<void>(`/api/tasks/${id}`)
  }
}

export const resultApi = {
  getResultList(page = 1, pageSize = 10): Promise<PagedResult<PredictionResult>> {
    return request.get<PagedResult<PredictionResult>>('/api/results', { params: { page, pageSize } })
  },

  getResultDetail(id: string | number): Promise<PredictionResult> {
    return request.get<PredictionResult>(`/api/results/${id}`)
  },

  deleteResult(id: string | number): Promise<void> {
    return request.delete<void>(`/api/results/${id}`)
  }
}

export interface BatchUploadResult {
  batchId: string
  totalCount: number
  status: string
}

export interface BatchStatus {
  batchId: string
  algoType?: string
  totalCount: number
  successCount: number
  failCount: number
  progress: number
  status: string
  resultUrl?: string
  createTime?: string
  updateTime?: string
}

export const batchApi = {
  /** 上传 CSV 批量预测 */
  upload(file: File, algoType: string): Promise<BatchUploadResult> {
    const form = new FormData()
    form.append('file', file)
    form.append('algoType', algoType)
    return request.post<BatchUploadResult>('/api/batch/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  /** 查询批量任务进度 */
  getStatus(batchId: string): Promise<BatchStatus> {
    return request.get<BatchStatus>(`/api/batch/status/${batchId}`)
  },

  /** 下载批量结果 CSV（带 token，绕过拦截器） */
  async download(batchId: string): Promise<void> {
    const token = localStorage.getItem('auth_token')
    const url = `${baseURL}/api/batch/download/${batchId}`
    const resp = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
    if (!resp.ok) {
      throw new Error('下载失败')
    }
    const blob = await resp.blob()
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `${batchId}_result.csv`
    link.click()
    URL.revokeObjectURL(link.href)
  }
}
