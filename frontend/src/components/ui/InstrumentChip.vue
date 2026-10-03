<script>
export default {
  name: 'InstrumentChip',
  props: {
    label: { type: String, required: true },
    modelValue: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  methods: {
    toggle() {
      this.$emit('update:modelValue', !this.modelValue)
    },
  },
}
</script>

<template>
  <button
    type="button"
    class="ichip"
    :class="{ 'ichip--on': modelValue }"
    role="checkbox"
    :aria-checked="String(modelValue)"
    @click="toggle"
  >
    <span class="ichip__label">{{ label }}</span>
    <span class="ichip__box">
      <svg v-if="modelValue" viewBox="0 0 16 16" fill="none" aria-hidden="true">
        <path d="M3.5 8.5 7 12l5.5-7" stroke="#fff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </span>
  </button>
</template>

<style scoped>
.ichip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 12px;
  border-radius: var(--radius-chip);
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: var(--color-text);
  white-space: nowrap;
}

.ichip--on {
  background: var(--gradient-accent);
  border-color: transparent;
}

.ichip__label {
  font-size: var(--font-size-base);
}

.ichip__box {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 5px;
  border: 1.5px solid rgba(255, 255, 255, 0.6);
}
.ichip--on .ichip__box {
  background: rgba(0, 0, 0, 0.25);
  border-color: rgba(255, 255, 255, 0.9);
}
.ichip__box svg {
  width: 14px;
  height: 14px;
}
</style>
