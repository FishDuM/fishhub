<template>
  <svg
    class="empty-state-illustration"
    viewBox="0 0 240 160"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    aria-hidden="true"
  >
    <ellipse cx="120" cy="137" rx="74" ry="10" fill="#F3F4F6" />
    <g v-if="variant === 'search'">
      <rect x="77" y="35" width="86" height="86" rx="20" fill="#FFF1F2" />
      <circle cx="113" cy="75" r="23" stroke="#FF6B81" stroke-width="8" />
      <path d="M130 93L151 114" stroke="#FF6B81" stroke-width="8" stroke-linecap="round" />
    </g>
    <g v-else-if="variant === 'relation'">
      <circle cx="91" cy="67" r="19" fill="#FFE4E6" />
      <circle cx="149" cy="67" r="19" fill="#E0F2FE" />
      <path d="M69 114C70 94 79 84 91 84C103 84 112 94 113 114" fill="#FFE4E6" />
      <path d="M127 114C128 94 137 84 149 84C161 84 170 94 171 114" fill="#E0F2FE" />
      <path d="M111 74H129" stroke="#94A3B8" stroke-width="6" stroke-linecap="round" />
    </g>
    <g v-else-if="variant === 'profile'">
      <rect x="62" y="38" width="116" height="81" rx="14" fill="#F8FAFC" stroke="#E2E8F0" stroke-width="3" />
      <circle cx="92" cy="72" r="15" fill="#FECACA" />
      <path d="M72 104C73 89 81 82 92 82C103 82 111 89 112 104" fill="#FECACA" />
      <path d="M125 65H155M125 82H147M125 99H140" stroke="#CBD5E1" stroke-width="6" stroke-linecap="round" />
    </g>
    <g v-else>
      <rect x="57" y="40" width="126" height="79" rx="14" fill="#F8FAFC" stroke="#E2E8F0" stroke-width="3" />
      <path d="M76 102L101 77L119 94L138 70L165 102" stroke="#93C5FD" stroke-width="7" stroke-linecap="round" stroke-linejoin="round" />
      <circle cx="88" cy="62" r="8" fill="#FDE68A" />
    </g>
  </svg>
</template>

<script setup>
defineProps({
  variant: {
    type: String,
    default: 'content'
  }
})
</script>

<style scoped>
.empty-state-illustration {
  display: block;
  width: min(100%, 260px);
  height: auto;
}
</style>
