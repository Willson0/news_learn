<script>
export default {
  name: 'AppButton',
  props: {
    // accent — жёлто-зелёный градиент (основное действие)
    // secondary — приглушённая плашка (напр. «Войти с Yandex»)
    variant: { type: String, default: 'accent' },
    type: { type: String, default: 'button' },
    disabled: { type: Boolean, default: false },
  },
  emits: ['click'],
}
</script>

<template>
  <button
    class="app-button"
    :class="[`app-button--${variant}`, { 'app-button--disabled': disabled }]"
    :type="type"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <slot />
  </button>
</template>

<style scoped>
.app-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 48px;
  padding: 0 16px;
  border-radius: var(--radius-input);
  font-size: var(--font-size-base);
  color: var(--color-text);
  transition: opacity 0.15s ease;
}

.app-button--accent {
  background: var(--gradient-accent);
}

.app-button--secondary {
  background: var(--color-surface-muted);
}

.app-button--disabled {
  opacity: 0.5;
  pointer-events: none;
}

.app-button:active {
  opacity: 0.85;
}
</style>
