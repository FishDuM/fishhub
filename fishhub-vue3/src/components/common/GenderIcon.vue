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
    <template v-if="sex === 0">
      <circle cx="12" cy="9" r="5" />
      <path d="M12 14v7M9 18h6" />
    </template>
    <template v-else>
      <circle cx="9" cy="15" r="5" />
      <path d="m13 11 7-7M15 4h5v5" />
    </template>
  </svg>
</template>

<script setup>
defineProps({
  sex: {
    type: Number,
    default: null
  }
})
</script>
