<template>
  <svg
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path
      v-if="type === 'home'"
      d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1V10Z"
    />
    <template v-else>
      <rect x="3" y="5" width="18" height="14" rx="4" />
      <path d="M12 8.5v7M8.5 12h7" />
    </template>
  </svg>
</template>

<script setup>
defineProps({
  type: {
    type: String,
    required: true
  }
})
</script>
