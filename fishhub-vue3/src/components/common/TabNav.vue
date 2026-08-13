<template>
  <div class="tab-nav">
    <div class="tab-list">
      <button 
        v-for="tab in tabs" 
        :key="tab.key"
        class="tab-item"
        :class="{ 'active': modelValue === tab.key }"
        @click="$emit('update:modelValue', tab.key)"
      >
        <span class="tab-text text-[16px]">{{ tab.label }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
defineProps({
  tabs: {
    type: Array,
    required: true
  },
  modelValue: {
    type: String,
    required: true
  }
})

defineEmits(['update:modelValue'])
</script>

<style scoped>
.tab-nav {
  width: 100%;
}

.tab-list {
  display: flex;
  gap: 5px;
  justify-content: center;
}

.tab-item {
  position: relative;
  width: 64px;
  height: 44px;
  padding: 0;
  font-size: 14px;
  color: var(--color-secondary-label);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: none;
  background: none;
  cursor: pointer;
}

.tab-item.active {
  color: var(--color-primary-label);
  font-weight: 600;
  background-color: var(--color-surface-muted);
  border-radius: 20px;
}

.tab-item:hover {
  color: var(--color-primary-label);
}

@media (max-width: 767px) {
  .tab-list { justify-content: flex-start; padding: 0 12px; overflow-x: auto; }
  .tab-item { flex: 0 0 auto; width: auto; min-width: 68px; padding: 0 14px; }
}
</style> 
