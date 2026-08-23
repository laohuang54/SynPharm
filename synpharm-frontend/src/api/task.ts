import { request } from '@/utils/request'
import type { Task, TaskDetail } from '@/types'

/**
 * 任务管理 API 封装
 *
 * 统一使用项目已有的 axios/request 封装，不直接写 fetch。
 * 对应后端 TaskController（/api/tasks）。
 */

/** 获取任务列表 */
export function getTasks(): Promise<Task[]> {
  return request.get<Task[]>('/api/tasks')
}

/** 获取任务详情 */
export function getTask(id: string | number): Promise<TaskDetail> {
  return request.get<TaskDetail>(`/api/tasks/${id}`)
}

/** 取消任务（对应后端 DELETE /api/tasks/{id}，语义为取消而非删除） */
export function cancelTask(id: string | number): Promise<void> {
  return request.delete<void>(`/api/tasks/${id}`)
}
