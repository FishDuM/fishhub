<template>
  <div class="app-shell">
    <div>
      <!-- 顶部导航栏 -->
      <AppHeader />

      <div class="app-body">
        <!-- 左侧侧边栏 -->
        <AppSidebar />

        <!-- 右侧内容区域 -->
        <main class="app-main">
          <router-view></router-view>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup>
import AppHeader from '@/components/layout/AppHeader.vue'
import AppSidebar from '@/components/layout/AppSidebar.vue'

</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  max-width: 1920px;
  margin: 0 auto;
  background: var(--color-page);
}

.app-body {
  min-height: 100vh;
  padding: 88px clamp(20px, 4vw, 96px) 32px;
}

.app-main {
  min-width: 0;
  margin-left: 320px;
}

@media (max-width: 1279px) {
  .app-body { padding-inline: 24px; }
  .app-main { margin-left: 240px; }
}

@media (max-width: 767px) {
  .app-body {
    min-height: auto;
    padding: 64px 0 calc(74px + env(safe-area-inset-bottom));
  }

  .app-main { margin-left: 0; }
}
</style> 
