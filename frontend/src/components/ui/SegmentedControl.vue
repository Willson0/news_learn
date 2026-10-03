<script>
export default {
  name: 'SegmentedControl',
  props: {
    modelValue: { type: String, default: '' },
    options: { type: Array, required: true }, // [{ value, label }]
  },
  emits: ['update:modelValue'],
}
</script>

<template>
  <div class="segmented">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      class="segmented__item"
      :class="{ 'segmented__item--active': modelValue === opt.value }"
      @click="$emit('update:modelValue', opt.value)"
    >
      {{ opt.label }}
    </button>
  </div>
</template>

<style scoped>
.segmented {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px;
  border-radius: var(--radius-card);
  background: var(--color-surface-muted);
}

.segmented__item {
  flex: 1;
  height: 44px;
  border-radius: var(--radius-chip);
  color: var(--color-text);
  font-size: var(--font-size-base);
}

.segmented__item--active {
  background: var(--gradient-accent-purple);
}
</style>
