<script>
export default {
  name: 'ConfirmDialog',
  props: {
    open: { type: Boolean, default: false },
    title: { type: String, required: true },
    text: { type: String, default: '' },
    confirmLabel: { type: String, default: 'Удалить' },
    cancelLabel: { type: String, default: 'Отмена' },
    busy: { type: Boolean, default: false },
  },
  emits: ['confirm', 'cancel'],
}
</script>

<template>
  <div v-if="open" class="confirm" role="dialog" aria-modal="true" @click.self="$emit('cancel')">
    <div class="confirm__card">
      <h2 class="confirm__title">{{ title }}</h2>
      <p v-if="text" class="confirm__text">{{ text }}</p>
      <button type="button" class="confirm__btn confirm__btn--danger" :disabled="busy" @click="$emit('confirm')">
        {{ confirmLabel }}
      </button>
      <button type="button" class="confirm__btn confirm__btn--ghost" @click="$emit('cancel')">
        {{ cancelLabel }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.confirm {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 60px;
  background: rgba(0, 0, 0, 0.25);
}

.confirm__card {
  width: 100%;
  max-width: 276px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px;
  border-radius: 20px;
  background: rgba(32, 33, 36, 0.92);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.5);
}

.confirm__title {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.confirm__text {
  margin-bottom: 8px;
  font-size: var(--font-size-base);
  line-height: 1.35;
  color: var(--color-text-secondary);
}

.confirm__btn {
  height: 48px;
  border-radius: var(--radius-pill);
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.confirm__btn--danger {
  background: linear-gradient(180deg, #8c1b1b 0%, #6e1212 100%);
}

.confirm__btn--ghost {
  border: 1px solid rgba(255, 255, 255, 0.35);
}

.confirm__btn:disabled {
  opacity: 0.6;
}
</style>
