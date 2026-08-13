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
    <template v-if="type === 'edit'">
      <path d="M12 3a2 2 0 0 1 3.8-.8 2 2 0 0 1 2.9 2.9A2 2 0 0 1 19.5 9a2 2 0 0 1 0 6 2 2 0 0 1-.8 3.8 2 2 0 0 1-2.9 2.9 2 2 0 0 1-3.8.8 2 2 0 0 1-3.8-.8 2 2 0 0 1-2.9-2.9A2 2 0 0 1 4.5 15a2 2 0 0 1 0-6 2 2 0 0 1 .8-3.8 2 2 0 0 1 2.9-2.9A2 2 0 0 1 12 3Z" />
      <circle cx="12" cy="12" r="3" />
    </template>
    <template v-else>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 10v5M12 7h.01" />
    </template>
  </svg>
</template>

<script setup>
defineProps({
  type: {
    type: String,
    default: 'edit'
  }
})
</script>
