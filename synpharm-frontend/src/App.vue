<template>
  <div class="app-container">
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>

<script setup lang="ts">
// authStore.init() 已在 main.ts 中（router 安装前）完成，避免重复初始化
</script>

<style lang="scss">
@import '@/styles/variables.scss';

:root {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-size: $font-size-base;
  color: $text-primary;
  background: $bg-secondary;
}

#app {
  height: 100%;
}

.app-container {
  height: 100%;
  min-height: 100vh;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: transparent;
}

::-webkit-scrollbar-thumb {
  background: $border-color;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: $text-muted;
}
</style>