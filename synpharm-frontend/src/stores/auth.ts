import { defineStore } from 'pinia'
import type { User, LoginCredentials, LoginResult, RegisterCredentials } from '@/types'
import { authApi, type UserDTO } from '@/api/auth'

export interface SendCaptchaResult {
  success: boolean
  message: string
  /** 是否开发模式（未配置发件邮箱，验证码直接返回给前端显示） */
  devMode?: boolean
  /** 开发模式下返回的验证码 */
  code?: string
}

const STORAGE_KEY = {
  USER: 'auth_user',
  TOKEN: 'auth_token',
  IS_GUEST: 'auth_is_guest'
}

const USE_MOCK = import.meta.env.VITE_ENABLE_MOCK === 'true'

/** 将后端用户 DTO 归一化为前端 User 类型（字段名/类型对齐） */
const normalizeUser = (user: UserDTO): User => ({
  id: String(user.id),
  email: user.email,
  nickname: user.nickname,
  avatar: user.avatarUrl || '',
  createdAt: user.createdAt || ''
})

const MOCK_USERS: Array<{ email: string; password: string; user: User }> = [
  {
    email: 'demo@protein.com',
    password: 'demo123',
    user: {
      id: 'user_demo_001',
      email: 'demo@protein.com',
      nickname: '演示用户',
      createdAt: '2024-01-01T00:00:00Z'
    }
  }
]

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null as User | null,
    token: null as string | null,
    isLoggedIn: false,
    isGuest: false
  }),

  getters: {
    currentUser: (state) => state.user,
    isAuthenticated: (state) => state.isLoggedIn,
    userNickname: (state) => state.user?.nickname || '未登录',
    isGuestUser: (state) => state.isGuest
  },

  actions: {
    init() {
      const storedUser = localStorage.getItem(STORAGE_KEY.USER)
      const storedToken = localStorage.getItem(STORAGE_KEY.TOKEN)
      const storedIsGuest = localStorage.getItem(STORAGE_KEY.IS_GUEST)
      
      if (storedUser && storedToken) {
        try {
          this.user = JSON.parse(storedUser)
          this.token = storedToken
          this.isLoggedIn = true
          this.isGuest = storedIsGuest === 'true' || false
        } catch {
          localStorage.removeItem(STORAGE_KEY.USER)
          localStorage.removeItem(STORAGE_KEY.TOKEN)
          localStorage.removeItem(STORAGE_KEY.IS_GUEST)
        }
      }
    },

    async login(credentials: LoginCredentials): Promise<LoginResult> {
      if (USE_MOCK) {
        return this.mockLogin(credentials)
      }
      
      try {
        const response = await authApi.login(credentials)
        this.user = normalizeUser(response.user)
        this.token = response.accessToken
        this.isLoggedIn = true
        this.isGuest = credentials.loginType === 'guest'

        localStorage.setItem(STORAGE_KEY.USER, JSON.stringify(this.user))
        localStorage.setItem(STORAGE_KEY.TOKEN, this.token)
        localStorage.setItem(STORAGE_KEY.IS_GUEST, String(credentials.loginType === 'guest'))

        return {
          success: true,
          message: '登录成功',
          user: this.user,
          token: this.token
        }
      } catch (error: unknown) {
        const message = error instanceof Error ? error.message : '登录失败'
        return {
          success: false,
          message
        }
      }
    },

    async register(credentials: RegisterCredentials): Promise<LoginResult> {
      if (USE_MOCK) {
        return {
          success: false,
          message: '当前为演示模式，请关闭 Mock 后使用注册功能'
        }
      }

      try {
        const response = await authApi.register(credentials)
        this.user = normalizeUser(response.user)
        this.token = response.accessToken
        this.isLoggedIn = true
        this.isGuest = false

        localStorage.setItem(STORAGE_KEY.USER, JSON.stringify(this.user))
        localStorage.setItem(STORAGE_KEY.TOKEN, this.token)
        localStorage.setItem(STORAGE_KEY.IS_GUEST, 'false')

        return {
          success: true,
          message: '注册成功，已自动登录',
          user: this.user,
          token: this.token
        }
      } catch (error: unknown) {
        const message = error instanceof Error ? error.message : '注册失败'
        return {
          success: false,
          message
        }
      }
    },

    async mockLogin(credentials: LoginCredentials): Promise<LoginResult> {
      await new Promise(resolve => setTimeout(resolve, 800))

      if (credentials.loginType === 'guest') {
        const guestUser: User = {
          id: 'guest_user_' + Date.now(),
          email: 'guest_' + Date.now() + '@guest.local',
          nickname: '游客_' + (Date.now() % 10000),
          createdAt: new Date().toISOString()
        }
        const token = 'guest_token_' + Date.now()
        this.user = guestUser
        this.token = token
        this.isLoggedIn = true
        this.isGuest = true

        localStorage.setItem(STORAGE_KEY.USER, JSON.stringify(guestUser))
        localStorage.setItem(STORAGE_KEY.TOKEN, token)
        localStorage.setItem(STORAGE_KEY.IS_GUEST, 'true')

        return {
          success: true,
          message: '游客登录成功',
          user: guestUser,
          token
        }
      }

      const mockUser = MOCK_USERS.find(
        u => u.email === credentials.email
      )

      if (mockUser) {
        const token = 'mock_token_' + Date.now()
        this.user = mockUser.user
        this.token = token
        this.isLoggedIn = true
        this.isGuest = false

        localStorage.setItem(STORAGE_KEY.USER, JSON.stringify(mockUser.user))
        localStorage.setItem(STORAGE_KEY.TOKEN, token)
        localStorage.setItem(STORAGE_KEY.IS_GUEST, 'false')

        return {
          success: true,
          message: '登录成功',
          user: mockUser.user,
          token
        }
      }

      return {
        success: false,
        message: '邮箱或验证码错误'
      }
    },

    async sendCaptcha(email: string, type: 'login' | 'register' | 'reset'): Promise<SendCaptchaResult> {
      if (USE_MOCK) {
        await new Promise(resolve => setTimeout(resolve, 500))
        return {
          success: true,
          message: '验证码已发送，有效期1分钟'
        }
      }
      
      try {
        const response = await authApi.sendCaptcha(email, type)
        return {
          success: true,
          message: '验证码已发送，有效期1分钟',
          devMode: response.devMode,
          code: response.code
        }
      } catch (error: unknown) {
        const message = error instanceof Error ? error.message : '发送失败'
        return {
          success: false,
          message
        }
      }
    },

    async resetPassword(email: string, captcha: string, newPassword: string): Promise<SendCaptchaResult> {
      if (USE_MOCK) {
        await new Promise(resolve => setTimeout(resolve, 500))
        return {
          success: true,
          message: '密码重置成功'
        }
      }
      
      try {
        await authApi.resetPassword(email, captcha, newPassword)
        return {
          success: true,
          message: '密码重置成功'
        }
      } catch (error: unknown) {
        const message = error instanceof Error ? error.message : '重置失败'
        return {
          success: false,
          message
        }
      }
    },

    async logout() {
      if (!USE_MOCK) {
        try {
          await authApi.logout()
        } catch (e) {
          console.error('Logout API error:', e)
        }
      }
      
      this.user = null
      this.token = null
      this.isLoggedIn = false
      this.isGuest = false

      localStorage.removeItem(STORAGE_KEY.USER)
      localStorage.removeItem(STORAGE_KEY.TOKEN)
      localStorage.removeItem(STORAGE_KEY.IS_GUEST)
    },

    updateNickname(nickname: string) {
      if (this.user) {
        this.user.nickname = nickname
        localStorage.setItem(STORAGE_KEY.USER, JSON.stringify(this.user))
      }
    },

    async refreshUser() {
      try {
        const user = await authApi.getProfile()

        this.user = user

        localStorage.setItem(
          STORAGE_KEY.USER,
          JSON.stringify(user)
        )

        return user
      } catch (error) {
        console.error('获取用户信息失败:', error)
        throw error
      }
    }
  }
})