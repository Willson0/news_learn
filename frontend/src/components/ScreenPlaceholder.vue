<script>
export default {
  name: 'ScreenPlaceholder',
  props: {
    title: { type: String, required: true },
    note: { type: String, default: 'Экран будет свёрстан по макету Figma в отдельном PR.' },
  },
}
</script>

<template>
  <section class="placeholder">
    <h1 class="screen-title">{{ title }}</h1>
    <p class="placeholder__note">{{ note }}</p>
  </section>
</template>

<style scoped>
.placeholder {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 94px var(--space-screen-x) 120px;
}

.placeholder__note {
  color: var(--color-text-secondary);
  line-height: 1.4;
}
</style>
