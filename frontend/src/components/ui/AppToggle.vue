<script>
export default {
  name: 'AppToggle',
  props: {
    modelValue: { type: Boolean, default: false },
    disabled: { type: Boolean, default: false },
  },
  emits: ['update:modelValue'],
  methods: {
    toggle() {
      if (this.disabled) return
      this.$emit('update:modelValue', !this.modelValue)
    },
  },
}
</script>

<template>
  <button
    type="button"
    class="toggle"
    :class="{ 'toggle--on': modelValue, 'toggle--disabled': disabled }"
    role="switch"
    :aria-checked="String(modelValue)"
    @click="toggle"
  >
    <span class="toggle__knob"></span>
  </button>
</template>

<style scoped>
.toggle {
  position: relative;
  width: 56px;
  height: 30px;
  border-radius: 999px;
  background: var(--color-surface-2);
  transition: background 0.18s ease;
  flex-shrink: 0;
}

.toggle--on {
  background: var(--gradient-accent);
}

.toggle--disabled {
  opacity: 0.5;
  pointer-events: none;
}

.toggle__knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #5bb318;
  transition: transform 0.18s ease, background 0.18s ease;
}

.toggle--on .toggle__knob {
  transform: translateX(26px);
  background: #ffffff;
}
</style>
