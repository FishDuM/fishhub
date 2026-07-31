<template>
  <div class="notifications-page">
    <h2 class="text-2xl font-bold text-gray-900 mb-6">通知</h2>
    <!-- 这里放通知页面的内容 -->
  </div>
</template>

<script setup>
// 组件逻辑
</script>

<style scoped>
.notifications {
  padding: 20px;
}

.notifications-page {
  min-height: 50vh;
  padding: 28px;
}

@media (max-width: 767px) {
  .notifications-page { min-height: auto; padding: 20px 16px; }
  .notifications-page h2 { margin-bottom: 20px; font-size: 20px; }
}
</style>
