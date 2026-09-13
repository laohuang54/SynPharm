<template>
  <div class="login">
    <div class="login__left">
      <div class="login__logo">
        <span class="login__logo-icon">🧬</span>
        <span class="login__logo-text">SynPharm</span>
      </div>
      
      <h2 class="login__title">智能药物研发平台</h2>
      <p class="login__desc">基于多智能体协同技术，精准预测分子互作</p>
      
      <div class="login__features">
        <div class="login__feature">
          <span class="login__feature-icon">🔬</span>
          <span class="login__feature-text">PPI/DTI/DDI预测</span>
        </div>
        <div class="login__feature">
          <span class="login__feature-icon">3️⃣D</span>
          <span class="login__feature-text">3D可视化</span>
        </div>
        <div class="login__feature">
          <span class="login__feature-icon">📊</span>
          <span class="login__feature-text">批量处理</span>
        </div>
      </div>
    </div>
    
    <div class="login__right">
      <div class="login__form-container">
        <router-link to="/" class="login__back-link">
          ← 返回首页
        </router-link>
        
        <h3 class="login__form-title">{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h3>
        
        <!-- 模式切换：登录 / 注册 -->
        <div class="login__tabs">
          <button
            type="button"
            class="login__tab"
            :class="{ 'login__tab--active': mode === 'login' }"
            @click="switchMode('login')"
          >登录</button>
          <button
            type="button"
            class="login__tab"
            :class="{ 'login__tab--active': mode === 'register' }"
            @click="switchMode('register')"
          >注册</button>
        </div>
        
        <!-- ===================== 登录模式 ===================== -->
        <template v-if="mode === 'login'">
          <!-- 登录方式子切换：验证码 / 密码 -->
          <div class="login__tabs login__tabs--sub">
            <button
              type="button"
              class="login__tab"
              :class="{ 'login__tab--active': loginMethod === 'captcha' }"
              @click="loginMethod = 'captcha'"
            >验证码登录</button>
            <button
              type="button"
              class="login__tab"
              :class="{ 'login__tab--active': loginMethod === 'password' }"
              @click="loginMethod = 'password'"
            >密码登录</button>
          </div>
          
          <!-- 验证码登录 -->
          <form v-if="loginMethod === 'captcha'" @submit.prevent="handleCaptchaLogin" class="login__form">
            <div class="login__form-group">
              <label class="login__form-label">QQ邮箱</label>
              <div class="login__input-wrapper">
                <span class="login__input-icon">📧</span>
                <input 
                  v-model="email"
                  type="email"
                  autocomplete="email"
                  class="login__form-input"
                  :class="{ 'login__form-input--error': errors.email }"
                  placeholder="请输入QQ邮箱"
                  @blur="validateEmail"
                  @input="errors.email && validateEmail()"
                />
              </div>
              <span v-if="errors.email" class="login__form-error">{{ errors.email }}</span>
            </div>
            
            <div class="login__form-group">
              <label class="login__form-label">验证码</label>
              <div class="login__input-wrapper login__input-wrapper--captcha">
                <span class="login__input-icon">🔑</span>
                <input 
                  v-model="captcha"
                  type="text"
                  maxlength="6"
                  class="login__form-input login__form-input--captcha"
                  :class="{ 'login__form-input--error': errors.captcha }"
                  placeholder="请输入6位验证码"
                  @blur="validateCaptcha"
                  @input="errors.captcha && validateCaptcha()"
                />
                <button 
                  type="button"
                  class="login__captcha-btn"
                  :disabled="isCaptchaSending || captchaCountdown > 0"
                  @click="handleSendCaptcha"
                >
                  {{ captchaCountdown > 0 ? `${captchaCountdown}s` : '获取验证码' }}
                </button>
              </div>
              <span v-if="errors.captcha" class="login__form-error">{{ errors.captcha }}</span>
            </div>
            
            <div v-if="captchaHint" class="login__form-message login__form-message--success">{{ captchaHint }}</div>
            
            <div class="login__form-group login__form-group--remember">
              <label class="login__form-checkbox">
                <input 
                  v-model="rememberMe"
                  type="checkbox" 
                  class="login__checkbox"
                />
                <span class="login__checkbox-checkmark"></span>
                <span>记住我</span>
              </label>
              <button 
                type="button"
                class="login__form-link"
                @click="showResetModal = true"
              >忘记密码?</button>
            </div>
            
            <button 
              type="submit" 
              class="login__form-btn"
              :disabled="isLoading"
            >
              <span v-if="isLoading" class="login__btn-spinner"></span>
              {{ isLoading ? '登录中...' : '登录' }}
            </button>
          </form>
          
          <!-- 密码登录 -->
          <form v-else @submit.prevent="handlePasswordLogin" class="login__form">
            <div class="login__form-group">
              <label class="login__form-label">QQ邮箱</label>
              <div class="login__input-wrapper">
                <span class="login__input-icon">📧</span>
                <input 
                  v-model="email"
                  type="email"
                  autocomplete="email"
                  class="login__form-input"
                  :class="{ 'login__form-input--error': errors.email }"
                  placeholder="请输入QQ邮箱"
                  @blur="validateEmail"
                  @input="errors.email && validateEmail()"
                />
              </div>
              <span v-if="errors.email" class="login__form-error">{{ errors.email }}</span>
            </div>
            
            <div class="login__form-group">
              <label class="login__form-label">密码</label>
              <div class="login__input-wrapper">
                <span class="login__input-icon">🔒</span>
                <input 
                  v-model="loginPassword"
                  type="password"
                  autocomplete="current-password"
                  class="login__form-input"
                  :class="{ 'login__form-input--error': errors.loginPassword }"
                  placeholder="请输入密码"
                  @blur="validateLoginPassword"
                  @input="errors.loginPassword && validateLoginPassword()"
                />
              </div>
              <span v-if="errors.loginPassword" class="login__form-error">{{ errors.loginPassword }}</span>
            </div>
            
            <div class="login__form-group login__form-group--remember">
              <button 
                type="button"
                class="login__form-link"
                @click="showResetModal = true"
              >忘记密码?</button>
            </div>
            
            <button 
              type="submit" 
              class="login__form-btn"
              :disabled="isLoading"
            >
              <span v-if="isLoading" class="login__btn-spinner"></span>
              {{ isLoading ? '登录中...' : '登录' }}
            </button>
          </form>
          
          <div v-if="loginError" class="login__form-message login__form-message--error">
            {{ loginError }}
          </div>
          
          <div class="login__divider">
            <span class="login__divider-text">或</span>
          </div>
          
          <button 
            type="button"
            class="login__form-btn login__form-btn--outline"
            @click="handleGuestLogin"
          >
            游客模式
          </button>
        </template>
        
        <!-- ===================== 注册模式 ===================== -->
        <form v-else @submit.prevent="handleRegister" class="login__form">
          <div class="login__form-group">
            <label class="login__form-label">QQ邮箱</label>
            <div class="login__input-wrapper">
              <span class="login__input-icon">📧</span>
              <input 
                v-model="regEmail"
                type="email"
                autocomplete="email"
                class="login__form-input"
                :class="{ 'login__form-input--error': errors.regEmail }"
                placeholder="请输入QQ邮箱"
                @blur="validateRegEmail"
                @input="errors.regEmail && validateRegEmail()"
              />
            </div>
            <span v-if="errors.regEmail" class="login__form-error">{{ errors.regEmail }}</span>
          </div>
          
          <div class="login__form-group">
            <label class="login__form-label">昵称</label>
            <div class="login__input-wrapper">
              <span class="login__input-icon">👤</span>
              <input 
                v-model="regNickname"
                type="text"
                class="login__form-input"
                :class="{ 'login__form-input--error': errors.regNickname }"
                placeholder="2-20位中文、字母、数字"
                @blur="validateRegNickname"
                @input="errors.regNickname && validateRegNickname()"
              />
            </div>
            <span v-if="errors.regNickname" class="login__form-error">{{ errors.regNickname }}</span>
          </div>
          
          <div class="login__form-group">
            <label class="login__form-label">密码</label>
            <div class="login__input-wrapper">
              <span class="login__input-icon">🔒</span>
              <input 
                v-model="regPassword"
                type="password"
                autocomplete="new-password"
                class="login__form-input"
                :class="{ 'login__form-input--error': errors.regPassword }"
                placeholder="至少8位，含大小写字母和数字"
                @blur="validateRegPassword"
                @input="errors.regPassword && validateRegPassword()"
              />
            </div>
            <span v-if="errors.regPassword" class="login__form-error">{{ errors.regPassword }}</span>
          </div>
          
          <div class="login__form-group">
            <label class="login__form-label">确认密码</label>
            <div class="login__input-wrapper">
              <span class="login__input-icon">🔒</span>
              <input 
                v-model="regConfirmPassword"
                type="password"
                autocomplete="new-password"
                class="login__form-input"
                :class="{ 'login__form-input--error': errors.regConfirmPassword }"
                placeholder="请再次输入密码"
                @blur="validateRegConfirmPassword"
                @input="errors.regConfirmPassword && validateRegConfirmPassword()"
              />
            </div>
            <span v-if="errors.regConfirmPassword" class="login__form-error">{{ errors.regConfirmPassword }}</span>
          </div>
          
          <div class="login__form-group">
            <label class="login__form-label">验证码</label>
            <div class="login__input-wrapper login__input-wrapper--captcha">
              <span class="login__input-icon">🔑</span>
              <input 
                v-model="regCaptcha"
                type="text"
                maxlength="6"
                class="login__form-input login__form-input--captcha"
                :class="{ 'login__form-input--error': errors.regCaptcha }"
                placeholder="请输入6位验证码"
                @blur="validateRegCaptcha"
                @input="errors.regCaptcha && validateRegCaptcha()"
              />
              <button 
                type="button"
                class="login__captcha-btn"
                :disabled="isCaptchaSending || captchaCountdown > 0"
                @click="handleSendRegisterCaptcha"
              >
                {{ captchaCountdown > 0 ? `${captchaCountdown}s` : '获取验证码' }}
              </button>
            </div>
            <span v-if="errors.regCaptcha" class="login__form-error">{{ errors.regCaptcha }}</span>
          </div>
          
          <div v-if="captchaHint" class="login__form-message login__form-message--success">{{ captchaHint }}</div>
          
          <button 
            type="submit" 
            class="login__form-btn"
            :disabled="isLoading"
          >
            <span v-if="isLoading" class="login__btn-spinner"></span>
            {{ isLoading ? '注册中...' : '注册' }}
          </button>
        </form>
        
        <div v-if="registerError" class="login__form-message login__form-message--error">
          {{ registerError }}
        </div>
      </div>
    </div>

    <div v-if="showResetModal" class="login__modal-overlay" @click.self="showResetModal = false">
      <div class="login__modal">
        <h4 class="login__modal-title">忘记密码</h4>
        <div class="login__modal-form">
          <div class="login__form-group">
            <label class="login__form-label">QQ邮箱</label>
            <input 
              v-model="resetEmail"
              type="email"
              class="login__form-input"
              placeholder="请输入注册邮箱"
            />
          </div>
          <div class="login__form-group">
            <label class="login__form-label">验证码</label>
            <div class="login__input-wrapper login__input-wrapper--captcha">
              <input 
                v-model="resetCaptcha"
                type="text"
                maxlength="6"
                class="login__form-input login__form-input--captcha"
                placeholder="请输入验证码"
              />
              <button 
                type="button"
                class="login__captcha-btn"
                :disabled="resetCaptchaCountdown > 0"
                @click="handleSendResetCaptcha"
              >
                {{ resetCaptchaCountdown > 0 ? `${resetCaptchaCountdown}s` : '获取验证码' }}
              </button>
            </div>
          </div>
          <div class="login__form-group">
            <label class="login__form-label">新密码</label>
            <input 
              v-model="resetPassword"
              type="password"
              class="login__form-input"
              placeholder="请输入新密码"
            />
          </div>
        </div>
        <div v-if="resetHint" class="login__form-message login__form-message--success">
          {{ resetHint }}
        </div>
        <div v-if="resetError" class="login__form-message login__form-message--error">
          {{ resetError }}
        </div>
        <div class="login__modal-actions">
          <button class="login__modal-btn login__modal-btn--cancel" @click="showResetModal = false">取消</button>
          <button class="login__modal-btn" @click="handleResetPassword">确认重置</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter, useRoute } from 'vue-router'
import { validateQqEmail, validatePassword, validateNickname, validateConfirmPassword } from '@/utils/validators'

const authStore = useAuthStore()
const router = useRouter()
const route = useRoute()

/**
 * 登录/注册成功后统一跳转：
 * 1. 优先使用 query 中的 redirect（用户访问受保护页被拦截时写入的原目标）
 * 2. 没有 redirect 则跳默认仪表盘
 * 3. 使用 replace 避免登录页残留在浏览器历史栈（点后退不会回到登录页）
 * 4. 使用 await 确保跳转完成后再关闭 loading，避免竞态中断
 */
const navigateAfterLogin = async () => {
  const redirect = (route.query.redirect as string) || '/dashboard'
  // 防止 redirect 被拼接成外链或异常路径，做基本安全校验
  const safeRedirect = redirect.startsWith('/') ? redirect : '/dashboard'
  await router.replace(safeRedirect)
}

const mode = ref<'login' | 'register'>('login')
const loginMethod = ref<'captcha' | 'password'>('captcha')

// 登录（验证码 / 密码）
const email = ref('')
const captcha = ref('')
const loginPassword = ref('')

// 注册
const regEmail = ref('')
const regNickname = ref('')
const regPassword = ref('')
const regConfirmPassword = ref('')
const regCaptcha = ref('')

const rememberMe = ref(false)
const isLoading = ref(false)
const loginError = ref('')
const registerError = ref('')
const captchaHint = ref('')
const resetHint = ref('')

const isCaptchaSending = ref(false)
const captchaCountdown = ref(0)
let captchaTimer: number | null = null

const showResetModal = ref(false)
const resetEmail = ref('')
const resetCaptcha = ref('')
const resetPassword = ref('')
const resetError = ref('')
const resetCaptchaCountdown = ref(0)
let resetCaptchaTimer: number | null = null

const errors = reactive({
  email: '',
  captcha: '',
  loginPassword: '',
  regEmail: '',
  regNickname: '',
  regPassword: '',
  regConfirmPassword: '',
  regCaptcha: ''
})

const switchMode = (m: 'login' | 'register') => {
  mode.value = m
  loginError.value = ''
  registerError.value = ''
  captchaHint.value = ''
}

const validateEmail = (): boolean => {
  const result = validateQqEmail(email.value)
  errors.email = result.valid ? '' : result.message
  return result.valid
}

const validateCaptcha = (): boolean => {
  if (!captcha.value.trim()) {
    errors.captcha = '验证码不能为空'
    return false
  }
  if (!/^\d{6}$/.test(captcha.value)) {
    errors.captcha = '验证码为6位数字'
    return false
  }
  errors.captcha = ''
  return true
}

const validateLoginPassword = (): boolean => {
  const result = validatePassword(loginPassword.value)
  errors.loginPassword = result.valid ? '' : result.message
  return result.valid
}

const validateRegEmail = (): boolean => {
  const result = validateQqEmail(regEmail.value)
  errors.regEmail = result.valid ? '' : result.message
  return result.valid
}

const validateRegNickname = (): boolean => {
  const result = validateNickname(regNickname.value)
  errors.regNickname = result.valid ? '' : result.message
  return result.valid
}

const validateRegPassword = (): boolean => {
  const result = validatePassword(regPassword.value)
  errors.regPassword = result.valid ? '' : result.message
  return result.valid
}

const validateRegConfirmPassword = (): boolean => {
  const result = validateConfirmPassword(regPassword.value, regConfirmPassword.value)
  errors.regConfirmPassword = result.valid ? '' : result.message
  return result.valid
}

const validateRegCaptcha = (): boolean => {
  if (!regCaptcha.value.trim()) {
    errors.regCaptcha = '验证码不能为空'
    return false
  }
  if (!/^\d{6}$/.test(regCaptcha.value)) {
    errors.regCaptcha = '验证码为6位数字'
    return false
  }
  errors.regCaptcha = ''
  return true
}

const validateLoginForm = (): boolean => {
  const emailValid = validateEmail()
  const captchaValid = validateCaptcha()
  return emailValid && captchaValid
}

const validatePasswordLoginForm = (): boolean => {
  const emailValid = validateEmail()
  const pwdValid = validateLoginPassword()
  return emailValid && pwdValid
}

const validateRegisterForm = (): boolean => {
  const e = validateRegEmail()
  const n = validateRegNickname()
  const p = validateRegPassword()
  const c = validateRegConfirmPassword()
  const cap = validateRegCaptcha()
  return e && n && p && c && cap
}

const handleCaptchaLogin = async () => {
  if (!validateLoginForm()) return

  isLoading.value = true
  loginError.value = ''
  
  const result = await authStore.login({
    loginType: 'qq_email',
    email: email.value.trim(),
    captcha: captcha.value
  })
  
  if (result.success) {
    await navigateAfterLogin()
  } else {
    loginError.value = result.message || '登录失败，请稍后重试'
  }
  
  isLoading.value = false
}

const handlePasswordLogin = async () => {
  if (!validatePasswordLoginForm()) return

  isLoading.value = true
  loginError.value = ''
  
  const result = await authStore.login({
    loginType: 'password',
    email: email.value.trim(),
    password: loginPassword.value
  })
  
  if (result.success) {
    await navigateAfterLogin()
  } else {
    loginError.value = result.message || '登录失败，请稍后重试'
  }
  
  isLoading.value = false
}

const handleRegister = async () => {
  if (!validateRegisterForm()) return

  isLoading.value = true
  registerError.value = ''
  
  const result = await authStore.register({
    email: regEmail.value.trim(),
    nickname: regNickname.value.trim(),
    password: regPassword.value,
    captcha: regCaptcha.value
  })
  
  if (result.success) {
    await navigateAfterLogin()
  } else {
    registerError.value = result.message || '注册失败，请稍后重试'
  }
  
  isLoading.value = false
}

const handleGuestLogin = async () => {
  const result = await authStore.login({
    loginType: 'guest'
  })
  if (result.success) {
    await navigateAfterLogin()
  }
}

const startCountdown = () => {
  captchaCountdown.value = 60
  captchaTimer = window.setInterval(() => {
    captchaCountdown.value--
    if (captchaCountdown.value <= 0) {
      if (captchaTimer) clearInterval(captchaTimer)
    }
  }, 1000)
}

const showDevCaptchaHint = (result: { devMode?: boolean; code?: string }) => {
  captchaHint.value = (result.devMode && result.code)
    ? `[开发模式] 验证码：${result.code}（未配置发件邮箱，不会真正发送邮件）`
    : ''
}

const handleSendCaptcha = async () => {
  if (!validateEmail()) return
  
  isCaptchaSending.value = true
  captchaHint.value = ''
  const result = await authStore.sendCaptcha(email.value.trim(), 'login')
  isCaptchaSending.value = false
  
  if (result.success) {
    startCountdown()
    showDevCaptchaHint(result)
  } else {
    loginError.value = result.message
  }
}

const handleSendRegisterCaptcha = async () => {
  if (!validateRegEmail()) return
  
  isCaptchaSending.value = true
  captchaHint.value = ''
  const result = await authStore.sendCaptcha(regEmail.value.trim(), 'register')
  isCaptchaSending.value = false
  
  if (result.success) {
    startCountdown()
    showDevCaptchaHint(result)
  } else {
    registerError.value = result.message
  }
}

const handleSendResetCaptcha = async () => {
  if (!resetEmail.value) return

  resetHint.value = ''
  const result = await authStore.sendCaptcha(resetEmail.value.trim(), 'reset')
  if (result.success) {
    resetCaptchaCountdown.value = 60
    resetCaptchaTimer = window.setInterval(() => {
      resetCaptchaCountdown.value--
      if (resetCaptchaCountdown.value <= 0) {
        if (resetCaptchaTimer) clearInterval(resetCaptchaTimer)
      }
    }, 1000)
    if (result.devMode && result.code) {
      resetHint.value = `[开发模式] 验证码：${result.code}`
    }
  } else {
    resetError.value = result.message
  }
}

const handleResetPassword = async () => {
  if (!resetEmail.value || !resetCaptcha.value || !resetPassword.value) {
    resetError.value = '请填写完整信息'
    return
  }

  const pwdCheck = validatePassword(resetPassword.value)
  if (!pwdCheck.valid) {
    resetError.value = pwdCheck.message
    return
  }
  
  const result = await authStore.resetPassword(
    resetEmail.value.trim(),
    resetCaptcha.value,
    resetPassword.value
  )
  
  if (result.success) {
    showResetModal.value = false
    resetEmail.value = ''
    resetCaptcha.value = ''
    resetPassword.value = ''
    resetError.value = ''
    loginError.value = '密码重置成功，请登录'
  } else {
    resetError.value = result.message
  }
}

onUnmounted(() => {
  if (captchaTimer) clearInterval(captchaTimer)
  if (resetCaptchaTimer) clearInterval(resetCaptchaTimer)
})
</script>

<style lang="scss" scoped>
.login {
  min-height: 100vh;
  display: flex;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 50%, #e2e8f0 100%);
}

.login__left {
  width: 50%;
  background: linear-gradient(135deg, $info-color 0%, $primary-color 100%);
  padding: $spacing-2xl;
  display: flex;
  flex-direction: column;
  justify-content: center;
  color: #ffffff;
}

.login__logo {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-xl;
}

.login__logo-icon {
  font-size: 48px;
}

.login__logo-text {
  font-size: 32px;
  font-weight: 700;
}

.login__title {
  font-size: 42px;
  font-weight: 700;
  margin-bottom: $spacing-md;
}

.login__desc {
  font-size: $font-size-lg;
  color: rgba(255, 255, 255, 0.85);
  margin-bottom: $spacing-xl;
  max-width: 450px;
}

.login__features {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-lg;
}

.login__feature {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-md;
  background: rgba(255, 255, 255, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: $border-radius-lg;
  backdrop-filter: blur(10px);
  transition: all $transition-normal;
  
  &:hover {
    background: rgba(255, 255, 255, 0.25);
    transform: translateY(-2px);
  }
}

.login__feature-icon {
  font-size: 28px;
}

.login__feature-text {
  font-size: $font-size-sm;
}

.login__right {
  width: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl;
}

.login__form-container {
  width: 100%;
  max-width: 450px;
  background: #ffffff;
  padding: $spacing-2xl;
  border-radius: $border-radius-2xl;
  box-shadow: 
    0 25px 50px -12px rgba(0, 0, 0, 0.08),
    0 4px 24px rgba(0, 0, 0, 0.04);
}

.login__back-link {
  display: inline-block;
  font-size: $font-size-sm;
  color: $text-secondary;
  text-decoration: none;
  margin-bottom: $spacing-lg;
  transition: all $transition-fast;
  
  &:hover {
    color: $accent-color;
    text-decoration: underline;
  }
}

.login__form-title {
  font-size: 28px;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: $spacing-lg;
  text-align: center;
}

.login__tabs {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-lg;
  padding: 4px;
  background: $bg-tertiary;
  border-radius: $border-radius-lg;

  &--sub {
    margin-top: -$spacing-md;
  }
}

.login__tab {
  flex: 1;
  padding: $spacing-sm $spacing-md;
  border: none;
  background: transparent;
  border-radius: $border-radius-md;
  font-size: $font-size-base;
  font-weight: 500;
  color: $text-secondary;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    color: $text-primary;
  }

  &--active {
    background: #ffffff;
    color: $accent-color;
    font-weight: 600;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  }
}

.login__form {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.login__form-group {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.login__form-group--remember {
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.login__form-label {
  font-size: $font-size-sm;
  font-weight: 500;
  color: $text-primary;
}

.login__input-wrapper {
  position: relative;
  display: flex;
  align-items: center;

  &--captcha {
    input {
      flex: 1;
      border-radius: $border-radius-lg 0 0 $border-radius-lg;
    }
  }
}

.login__input-icon {
  position: absolute;
  left: $spacing-md;
  font-size: 18px;
  color: $text-muted;
  z-index: 1;
}

.login__form-input {
  width: 100%;
  padding: $spacing-md $spacing-md $spacing-md calc(#{$spacing-md} + 40px);
  background: $bg-tertiary;
  border: 1px solid $border-color;
  border-radius: $border-radius-lg;
  font-size: $font-size-base;
  color: $text-primary;
  transition: all $transition-fast;
  
  &::placeholder {
    color: $text-muted;
  }
  
  &:focus {
    outline: none;
    border-color: $accent-color;
    background: #ffffff;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
  }

  &--error {
    border-color: $error-color;

    &:focus {
      border-color: $error-color;
      box-shadow: 0 0 0 3px rgba(239, 68, 68, 0.1);
    }
  }

  &--captcha {
    padding-right: $spacing-md;
  }
}

.login__captcha-btn {
  padding: $spacing-md $spacing-lg;
  background: $accent-color;
  color: #ffffff;
  border: none;
  border-radius: 0 $border-radius-lg $border-radius-lg 0;
  font-size: $font-size-sm;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-fast;
  white-space: nowrap;

  &:hover:not(:disabled) {
    background: #2563eb;
  }

  &:disabled {
    background: $border-color;
    cursor: not-allowed;
  }
}

.login__form-error {
  font-size: $font-size-xs;
  color: $error-color;
  margin-top: $spacing-xs;
}

.login__form-message {
  margin-top: $spacing-md;
  padding: $spacing-sm $spacing-md;
  border-radius: $border-radius-md;
  text-align: center;
  font-size: $font-size-sm;

  &--error {
    background: rgba(239, 68, 68, 0.1);
    color: $error-color;
  }

  &--success {
    background: rgba(16, 185, 129, 0.1);
    color: #059669;
  }
}

.login__form-checkbox {
  font-size: $font-size-sm;
  color: $text-secondary;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.login__checkbox {
  position: absolute;
  opacity: 0;
  cursor: pointer;
}

.login__checkbox-checkmark {
  width: 18px;
  height: 18px;
  border: 1.5px solid $border-color;
  border-radius: $border-radius-sm;
  position: relative;
  transition: all $transition-fast;
  
  &::after {
    content: '';
    position: absolute;
    left: 5px;
    top: 1px;
    width: 5px;
    height: 10px;
    border: solid $accent-color;
    border-width: 0 2px 2px 0;
    transform: rotate(45deg);
    opacity: 0;
    transition: opacity $transition-fast;
  }
}

.login__checkbox:checked + .login__checkbox-checkmark {
  background: $accent-color;
  border-color: $accent-color;
  
  &::after {
    opacity: 1;
  }
}

.login__form-link {
  color: $accent-color;
  text-decoration: none;
  font-size: $font-size-sm;
  background: none;
  border: none;
  cursor: pointer;
  
  &:hover {
    text-decoration: underline;
  }
}

.login__form-btn {
  padding: $spacing-md;
  background: linear-gradient(135deg, $accent-color 0%, #2563eb 100%);
  color: #ffffff;
  border: none;
  border-radius: $border-radius-lg;
  font-size: $font-size-base;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-fast;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  
  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 10px 30px rgba(59, 130, 246, 0.3);
  }
  
  &:disabled {
    background: $border-color;
    cursor: not-allowed;
  }
  
  &--outline {
    background: transparent;
    color: $text-primary;
    border: 1px solid $border-color;
    
    &:hover {
      background: $bg-tertiary;
      border-color: $accent-color;
      transform: none;
      box-shadow: none;
    }
  }
}

.login__btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.login__divider {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin: $spacing-lg 0;
}

.login__divider::before,
.login__divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: $border-color;
}

.login__divider-text {
  font-size: $font-size-xs;
  color: $text-muted;
}

.login__modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.login__modal {
  background: #ffffff;
  padding: $spacing-xl;
  border-radius: $border-radius-xl;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.2);
}

.login__modal-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-lg;
  text-align: center;
}

.login__modal-form {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.login__modal-actions {
  display: flex;
  gap: $spacing-md;
  margin-top: $spacing-xl;
}

.login__modal-btn {
  flex: 1;
  padding: $spacing-md;
  background: $accent-color;
  color: #ffffff;
  border: none;
  border-radius: $border-radius-lg;
  font-size: $font-size-base;
  font-weight: 500;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: #2563eb;
  }

  &--cancel {
    background: $bg-tertiary;
    color: $text-secondary;

    &:hover {
      background: $border-color;
    }
  }
}
</style>