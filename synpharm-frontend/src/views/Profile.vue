<template>
  <div class="profile">
    <Sidebar />
    <main class="profile__content">
      <header class="profile__header">
        <div class="profile__header-bg"></div>
        <div class="profile__header-content">
          <div class="profile__avatar">
            <span class="profile__avatar-text">{{ avatarText }}</span>
          </div>
          <div class="profile__user-info">
            <h1 class="profile__user-name">{{ authStore.userNickname }}</h1>
            <p class="profile__user-email">{{ authStore.user?.email }}</p>
            <span class="profile__user-role">{{ authStore.isGuest ? '游客账号' : '已认证用户' }}</span>
          </div>
          <button class="profile__edit-btn" @click="openEditProfile">编辑资料</button>
        </div>
      </header>
      
      <section class="profile__main">
        <div class="profile__card">
          <h3 class="profile__card-title">账户安全</h3>
          <div class="profile__card-content">
            <button class="profile__card-item" @click="openPasswordModal">
              <span class="profile__card-icon">🔐</span>
              <span class="profile__card-text">修改密码</span>
              <span class="profile__card-arrow">→</span>
            </button>
            <button class="profile__card-item">
              <span class="profile__card-icon">📧</span>
              <span class="profile__card-text">绑定邮箱</span>
              <span class="profile__card-arrow">→</span>
            </button>
            <button class="profile__card-item">
              <span class="profile__card-icon">📱</span>
              <span class="profile__card-text">绑定手机号</span>
              <span class="profile__card-arrow">→</span>
            </button>
            <button class="profile__card-item">
              <span class="profile__card-icon">🔑</span>
              <span class="profile__card-text">API密钥管理</span>
              <span class="profile__card-arrow">→</span>
            </button>
          </div>
        </div>
        
        <div class="profile__card">
          <h3 class="profile__card-title">使用统计</h3>
          <div v-if="statsLoading" class="profile__card-content" style="color: #64748b;">加载中...</div>
          <div v-else-if="statsError" class="profile__card-content" style="color: #ef4444;">{{ statsError }}</div>
          <div v-else class="profile__card-content profile__stats">
            <div class="profile__stat-item">
              <span class="profile__stat-icon">📋</span>
              <span class="profile__stat-value">{{ stats.totalTasks }}</span>
              <span class="profile__stat-label">总任务数</span>
            </div>
            <div class="profile__stat-item">
              <span class="profile__stat-icon">✅</span>
              <span class="profile__stat-value">{{ stats.completedTasks }}</span>
              <span class="profile__stat-label">已完成</span>
            </div>
            <div class="profile__stat-item">
              <span class="profile__stat-icon">📊</span>
              <span class="profile__stat-value">{{ stats.totalResults }}</span>
              <span class="profile__stat-label">预测结果</span>
            </div>
            <div class="profile__stat-item">
              <span class="profile__stat-icon">💾</span>
              <span class="profile__stat-value">{{ stats.storageUsed }}</span>
              <span class="profile__stat-label">存储空间</span>
            </div>
          </div>
        </div>
        
        <div class="profile__card">
          <h3 class="profile__card-title">系统设置</h3>
          <div class="profile__card-content">
            <div class="profile__card-item profile__card-item--toggle">
              <div>
                <span class="profile__card-icon">🌙</span>
                <span class="profile__card-text">深色模式</span>
              </div>
              <div class="profile__toggle-wrapper">
                <input v-model="darkMode" type="checkbox" class="profile__toggle" />
                <span class="profile__toggle-bg"></span>
              </div>
            </div>
            <div class="profile__card-item profile__card-item--toggle">
              <div>
                <span class="profile__card-icon">🔔</span>
                <span class="profile__card-text">邮件通知</span>
              </div>
              <div class="profile__toggle-wrapper">
                <input v-model="notifications" type="checkbox" class="profile__toggle" />
                <span class="profile__toggle-bg"></span>
              </div>
            </div>
            <div class="profile__card-item profile__card-item--toggle">
              <div>
                <span class="profile__card-icon">📊</span>
                <span class="profile__card-text">数据追踪</span>
              </div>
              <div class="profile__toggle-wrapper">
                <input v-model="dataTracking" type="checkbox" class="profile__toggle" />
                <span class="profile__toggle-bg"></span>
              </div>
            </div>
          </div>
        </div>
        
        <div class="profile__card profile__card--danger">
          <button class="profile__danger-btn" @click="openDeleteModal">
            删除账户
          </button>
        </div>
      </section>
    </main>

    <!-- 成功提示 -->
    <div v-if="successMessage" class="profile__toast">{{ successMessage }}</div>

    <!-- 编辑资料弹窗 -->
    <div v-if="showEditModal" class="pmodal" @click.self="closeEditModal">
      <div class="pmodal__dialog">
        <h3 class="pmodal__title">编辑资料</h3>
        <div class="pmodal__field">
          <label class="pmodal__label">昵称</label>
          <input
            v-model="editNickname"
            type="text"
            class="pmodal__input"
            placeholder="请输入昵称"
            @keyup.enter="saveProfile"
          />
        </div>
        <div v-if="editError" class="pmodal__error">{{ editError }}</div>
        <div class="pmodal__actions">
          <button class="pmodal__btn pmodal__btn--ghost" @click="closeEditModal">取消</button>
          <button class="pmodal__btn pmodal__btn--primary" :disabled="editLoading" @click="saveProfile">
            {{ editLoading ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 修改密码弹窗 -->
    <div v-if="showPasswordModal" class="pmodal" @click.self="closePasswordModal">
      <div class="pmodal__dialog">
        <h3 class="pmodal__title">修改密码</h3>
        <div class="pmodal__field">
          <label class="pmodal__label">原密码</label>
          <input v-model="oldPassword" type="password" class="pmodal__input" placeholder="请输入原密码" />
        </div>
        <div class="pmodal__field">
          <label class="pmodal__label">新密码</label>
          <input v-model="newPassword" type="password" class="pmodal__input" placeholder="请输入新密码" />
        </div>
        <div class="pmodal__field">
          <label class="pmodal__label">确认新密码</label>
          <input
            v-model="confirmPassword"
            type="password"
            class="pmodal__input"
            placeholder="请再次输入新密码"
            @keyup.enter="submitPassword"
          />
        </div>
        <div v-if="passwordError" class="pmodal__error">{{ passwordError }}</div>
        <div class="pmodal__actions">
          <button class="pmodal__btn pmodal__btn--ghost" @click="closePasswordModal">取消</button>
          <button class="pmodal__btn pmodal__btn--primary" :disabled="passwordLoading" @click="submitPassword">
            {{ passwordLoading ? '提交中...' : '确认修改' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 删除账户弹窗 -->
    <div v-if="showDeleteModal" class="pmodal" @click.self="closeDeleteModal">
      <div class="pmodal__dialog">
        <h3 class="pmodal__title pmodal__title--danger">删除账户</h3>
        <p class="pmodal__warning">删除账户后所有数据将被永久清除且无法恢复，请谨慎操作。</p>
        <div class="pmodal__field">
          <label class="pmodal__label">当前密码</label>
          <input
            v-model="deletePassword"
            type="password"
            class="pmodal__input"
            placeholder="请输入当前密码以确认删除"
            @keyup.enter="confirmDeleteAccount"
          />
        </div>
        <div v-if="deleteError" class="pmodal__error">{{ deleteError }}</div>
        <div class="pmodal__actions">
          <button class="pmodal__btn pmodal__btn--ghost" @click="closeDeleteModal">取消</button>
          <button class="pmodal__btn pmodal__btn--danger" :disabled="deleteLoading" @click="confirmDeleteAccount">
            {{ deleteLoading ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { taskApi, resultApi } from '@/api/predict'
import { authApi } from '@/api/auth'
import Sidebar from '@/components/Sidebar.vue'
import type { Task } from '@/types'

const authStore = useAuthStore()
const router = useRouter()

const darkMode = ref(false)
const notifications = ref(true)
const dataTracking = ref(true)

const tasks = ref<Task[]>([])
const resultsTotal = ref(0)
const statsLoading = ref(false)
const statsError = ref('')

const avatarText = computed(() => {
  if (!authStore.userNickname) return '👤'
  return authStore.userNickname.charAt(0).toUpperCase()
})

const stats = computed(() => ({
  totalTasks: tasks.value.length,
  completedTasks: tasks.value.filter((t) => t.status === 'completed').length,
  totalResults: resultsTotal.value,
  storageUsed: '暂无数据'
}))

const loadStats = async () => {
  statsLoading.value = true
  statsError.value = ''
  try {
    const [taskList, resultPage] = await Promise.all([
      taskApi.getTaskList(),
      resultApi.getResultList(1, 1)
    ])
    tasks.value = taskList as unknown as Task[]
    resultsTotal.value = resultPage.total
  } catch (error: unknown) {
    statsError.value = error instanceof Error ? error.message : '加载统计数据失败'
  } finally {
    statsLoading.value = false
  }
}

onMounted(async () => {
  try {
    await authStore.refreshUser()
  } catch (error) {
    console.error('刷新用户信息失败', error)
  }

  await loadStats()
})

// ================= 成功提示 =================
const successMessage = ref('')
let successTimer: number | null = null

const showSuccess = (msg: string) => {
  successMessage.value = msg
  if (successTimer) clearTimeout(successTimer)
  successTimer = window.setTimeout(() => {
    successMessage.value = ''
  }, 3000)
}

onUnmounted(() => {
  if (successTimer) clearTimeout(successTimer)
})

// ================= 编辑资料 =================
const showEditModal = ref(false)
const editNickname = ref('')
const editLoading = ref(false)
const editError = ref('')

const openEditProfile = () => {
  editNickname.value = authStore.user?.nickname || ''
  editError.value = ''
  showEditModal.value = true
}

const closeEditModal = () => {
  showEditModal.value = false
}

const saveProfile = async () => {
  const nickname = editNickname.value.trim()
  if (!nickname) {
    editError.value = '昵称不能为空'
    return
  }
  editLoading.value = true
  editError.value = ''
  try {
    await authApi.updateProfile({ nickname })
    authStore.updateNickname(nickname)
    showEditModal.value = false
    showSuccess('资料已更新')
  } catch (error: unknown) {
    editError.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    editLoading.value = false
  }
}

// ================= 修改密码 =================
const showPasswordModal = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordLoading = ref(false)
const passwordError = ref('')

const openPasswordModal = () => {
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  passwordError.value = ''
  showPasswordModal.value = true
}

const closePasswordModal = () => {
  showPasswordModal.value = false
}

const submitPassword = async () => {
  if (!oldPassword.value) {
    passwordError.value = '请输入原密码'
    return
  }
  if (!newPassword.value) {
    passwordError.value = '请输入新密码'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }
  passwordLoading.value = true
  passwordError.value = ''
  try {
    await authApi.changePassword(oldPassword.value, newPassword.value)
    showPasswordModal.value = false
    showSuccess('密码修改成功')
  } catch (error: unknown) {
    passwordError.value = error instanceof Error ? error.message : '修改密码失败'
  } finally {
    passwordLoading.value = false
  }
}

// ================= 删除账户 =================
const showDeleteModal = ref(false)
const deletePassword = ref('')
const deleteLoading = ref(false)
const deleteError = ref('')

const openDeleteModal = () => {
  deletePassword.value = ''
  deleteError.value = ''
  showDeleteModal.value = true
}

const closeDeleteModal = () => {
  showDeleteModal.value = false
}

const confirmDeleteAccount = async () => {
  if (!deletePassword.value) {
    deleteError.value = '请输入当前密码'
    return
  }
  deleteLoading.value = true
  deleteError.value = ''
  try {
    await authApi.deleteAccount(deletePassword.value)
    await authStore.logout()
    showDeleteModal.value = false
    router.push('/login')
  } catch (error: unknown) {
    deleteError.value = error instanceof Error ? error.message : '删除失败，请重试'
  } finally {
    deleteLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.profile {
  display: flex;
  min-height: 100vh;
  background: #f8fafc;
  padding-top: $header-height;
}

.profile__content {
  flex: 1;
  min-height: calc(100vh - #{$header-height});
}

.profile__header {
  position: relative;
  padding: $spacing-xl;
  overflow: hidden;
  background: linear-gradient(135deg, $info-color 0%, $primary-color 100%);
}

.profile__header-bg {
  position: absolute;
  top: -50%;
  left: -50%;
  width: 1000px;
  height: 1000px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  animation: headerGlow 12s ease-in-out infinite;
}

@keyframes headerGlow {
  0%, 100% { opacity: 0.5; transform: scale(1); }
  50% { opacity: 0.8; transform: scale(1.1); }
}

.profile__header-content {
  position: relative;
  display: flex;
  align-items: center;
  gap: $spacing-lg;
  max-width: 1000px;
}

.profile__avatar {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid rgba(255, 255, 255, 0.3);
  transition: all $transition-normal;
  
  &:hover {
    transform: scale(1.05);
    border-color: rgba(255, 255, 255, 0.5);
  }
}

.profile__avatar-text {
  font-size: 48px;
  font-weight: 700;
  color: #ffffff;
}

.profile__user-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.profile__user-name {
  font-size: 32px;
  font-weight: 700;
  color: #ffffff;
  margin: 0;
}

.profile__user-email {
  font-size: $font-size-base;
  color: rgba(255, 255, 255, 0.85);
  margin: 0;
}

.profile__user-role {
  display: inline-block;
  padding: 6px 16px;
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: $border-radius-lg;
  font-size: $font-size-xs;
  color: rgba(255, 255, 255, 0.9);
  width: fit-content;
}

.profile__edit-btn {
  padding: $spacing-sm $spacing-lg;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  color: #ffffff;
  cursor: pointer;
  transition: all $transition-fast;
  
  &:hover {
    background: rgba(255, 255, 255, 0.3);
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.2);
  }
}

.profile__main {
  max-width: 1000px;
  padding: $spacing-xl;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: $spacing-lg;
}

.profile__card {
  background: #ffffff;
  border-radius: $border-radius-xl;
  padding: $spacing-xl;
  border: 1px solid $border-color;
  box-shadow: 
    0 4px 24px rgba(0, 0, 0, 0.04),
    0 1px 3px rgba(0, 0, 0, 0.02);
}

.profile__card--danger {
  grid-column: 1 / -1;
  border: 1px solid rgba(239, 68, 68, 0.2);
  background: rgba(239, 68, 68, 0.02);
}

.profile__card-title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-lg;
}

.profile__card-content {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.profile__card-item {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md;
  border-radius: $border-radius-lg;
  cursor: pointer;
  transition: all $transition-fast;
  
  &:hover {
    background: $bg-tertiary;
    transform: translateX(4px);
  }
  
  &--toggle {
    justify-content: space-between;
    transform: none;
    
    &:hover {
      transform: none;
    }
  }
}

.profile__card-icon {
  font-size: $font-size-xl;
}

.profile__card-text {
  flex: 1;
  font-size: $font-size-sm;
  color: $text-primary;
}

.profile__card-arrow {
  font-size: $font-size-lg;
  color: $text-muted;
}

.profile__stats {
  flex-direction: row;
  justify-content: space-around;
  gap: $spacing-md;
}

.profile__stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-lg;
  background: $bg-tertiary;
  border: 1px solid $border-color;
  border-radius: $border-radius-lg;
  transition: all $transition-fast;
  
  &:hover {
    transform: translateY(-4px);
    background: rgba(59, 130, 246, 0.08);
    border-color: $accent-color;
    box-shadow: 0 8px 24px rgba(59, 130, 246, 0.1);
  }
}

.profile__stat-icon {
  font-size: 32px;
  margin-bottom: $spacing-xs;
}

.profile__stat-value {
  font-size: 32px;
  font-weight: 700;
  color: $text-primary;
}

.profile__stat-label {
  font-size: $font-size-xs;
  color: $text-muted;
}

.profile__toggle-wrapper {
  position: relative;
  width: 52px;
  height: 30px;
}

.profile__toggle {
  width: 100%;
  height: 100%;
  border-radius: 15px;
  background: $border-color;
  appearance: none;
  cursor: pointer;
  position: relative;
  transition: all $transition-fast;
  
  &:checked {
    background: linear-gradient(135deg, $accent-color 0%, #2563eb 100%);
  }
  
  &::after {
    content: '';
    position: absolute;
    width: 24px;
    height: 24px;
    background: #ffffff;
    border-radius: 50%;
    top: 3px;
    left: 3px;
    transition: left $transition-fast;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  }
  
  &:checked::after {
    left: 25px;
  }
}

.profile__danger-btn {
  width: 100%;
  padding: $spacing-md;
  background: transparent;
  border: 1px solid rgba(239, 68, 68, 0.4);
  border-radius: $border-radius-lg;
  font-size: $font-size-sm;
  font-weight: 500;
  color: $error-color;
  cursor: pointer;
  transition: all $transition-fast;
  
  &:hover {
    background: rgba(239, 68, 68, 0.08);
    transform: translateY(-2px);
    box-shadow: 0 8px 24px rgba(239, 68, 68, 0.1);
  }
}

/* ===================== 弹窗 & 提示 ===================== */
.profile__toast {
  position: fixed;
  top: 90px;
  right: $spacing-xl;
  padding: $spacing-sm $spacing-lg;
  background: $success-color;
  color: #ffffff;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  box-shadow: $shadow-md;
  z-index: 1001;
}

.pmodal {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.pmodal__dialog {
  width: 420px;
  max-width: calc(100vw - 40px);
  background: #ffffff;
  border-radius: $border-radius-xl;
  padding: $spacing-xl;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.pmodal__title {
  font-size: $font-size-lg;
  font-weight: 600;
  color: $text-primary;
  margin: 0 0 $spacing-lg;

  &--danger {
    color: $error-color;
  }
}

.pmodal__warning {
  margin: 0 0 $spacing-lg;
  padding: $spacing-sm $spacing-md;
  background: rgba(239, 68, 68, 0.08);
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  color: $error-color;
}

.pmodal__field {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  margin-bottom: $spacing-md;
}

.pmodal__label {
  font-size: $font-size-sm;
  font-weight: 500;
  color: $text-secondary;
}

.pmodal__input {
  padding: $spacing-sm $spacing-md;
  border: 1px solid $border-color;
  border-radius: $border-radius-md;
  font-size: $font-size-base;
  color: $text-primary;
  background: #ffffff;

  &:focus {
    outline: none;
    border-color: $accent-color;
    box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
  }
}

.pmodal__error {
  margin-bottom: $spacing-md;
  padding: $spacing-sm $spacing-md;
  background: rgba(239, 68, 68, 0.08);
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  color: $error-color;
}

.pmodal__actions {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-md;
  margin-top: $spacing-sm;
}

.pmodal__btn {
  padding: $spacing-sm $spacing-lg;
  border: none;
  border-radius: $border-radius-md;
  font-size: $font-size-sm;
  cursor: pointer;
  transition: $transition-fast;

  &--ghost {
    background: $bg-tertiary;
    color: $text-secondary;

    &:hover {
      background: $border-light;
    }
  }

  &--primary {
    background: $primary-color;
    color: #ffffff;

    &:hover:not(:disabled) {
      background: $primary-dark;
    }
  }

  &--danger {
    background: $error-color;
    color: #ffffff;

    &:hover:not(:disabled) {
      background: #dc2626;
    }
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}
</style>