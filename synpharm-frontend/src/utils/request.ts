import axios, { type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig, type AxiosResponse } from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

const envBase = (import.meta.env.VITE_API_BASE_URL as string || '').replace(/\/+$/, '')
// 生产（Docker/nginx 同源反代）走相对路径；本地开发默认直连后端（跨域由 CORS 放开）
export const baseURL = envBase || (import.meta.env.PROD ? '' : 'http://localhost:8080')

const service: AxiosInstance = axios.create({
  baseURL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

/**
 * 在请求 config 上暂存发送时使用的 token，用于响应拦截器中判断
 * "收到 401 时，这个 401 对应的 token 是否已经被替换（即用户重新登录过）"
 * 如果请求时的 token !== 当前 store 的 token，说明这是一个"过期请求"的响应，
 * 不应该触发 logout，否则会把用户刚登录成功的新状态一并清掉。
 */
const REQ_TOKEN_KEY = '__synpharm_req_token__'

service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const authStore = useAuthStore()
    const currentToken = authStore.token
    if (currentToken) {
      config.headers.Authorization = `Bearer ${currentToken}`
    }
    // 记录本请求发出时的 token（供响应拦截器比对）
    ;(config as unknown as Record<string, unknown>)[REQ_TOKEN_KEY] = currentToken ?? null
    return config
  },
  (error: unknown) => {
    return Promise.reject(error)
  }
)

service.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    if (res.code !== 200) {
      // 401 或业务 Token 错误码（2001/2002/2003）都视为未登录/失效
      if (res.code === 401 || res.code === 2001 || res.code === 2002 || res.code === 2003) {
        handleAuthFailure(response.config)
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data
  },
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      handleAuthFailure(error.config)
    }
    return Promise.reject(error)
  }
)

/**
 * 统一处理认证失败：只有当"请求时的 token"与"当前 store 的 token"一致时，
 * 才执行 logout + 跳登录页；否则说明这个 401 来自旧请求，忽略即可。
 * 这是修复"首次登录成功却被踢回登录页（第二次才正常）"的关键。
 */
function handleAuthFailure(config: InternalAxiosRequestConfig | AxiosRequestConfig | undefined) {
  const authStore = useAuthStore()
  const reqToken = (config as unknown as Record<string, unknown> | undefined)?.[REQ_TOKEN_KEY] as string | null | undefined
  const currentToken = authStore.token ?? null

  // 关键比对：只有请求时的 token 与当前 token 一致时，才认为是"当前登录态真的失效了"
  if (reqToken !== currentToken) {
    return
  }

  // 如果当前已经是登录页，则不再重复跳转（避免无限循环 / 打断登录流程）
  if (router.currentRoute.value.path === '/login') {
    return
  }

  authStore.logout()
  router.push('/login')
}

export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

export const request = {
  get<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.get(url, config)
  },
  post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.post(url, data, config)
  },
  put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return service.put(url, data, config)
  },
  delete<T = unknown>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return service.delete(url, config)
  }
}

export default service
