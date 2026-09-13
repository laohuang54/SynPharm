import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)

// 关键修复：在安装 router 之前先初始化登录态，避免首次导航时状态未就绪
const authStore = useAuthStore()
authStore.init()

app.use(router)
app.use(ElementPlus)

app.mount('#app')