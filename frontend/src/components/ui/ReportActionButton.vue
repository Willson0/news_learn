<script>
export default {
  name: 'ReportActionButton',
  props: {
    label: { type: String, required: true },
    // accent (зелёный), purple (малиновый), dark (нейтральный)
    variant: { type: String, default: 'accent' },
    // цвет кружка-иконки справа: green | pink
    badge: { type: String, default: 'green' },
  },
  emits: ['click'],
}
</script>

<template>
  <button
    type="button"
    class="rab"
    :class="`rab--${variant}`"
    @click="$emit('click', $event)"
  >
    <span class="rab__label">{{ label }}</span>
    <span class="rab__badge" :class="`rab__badge--${badge}`">
      <slot name="icon" />
    </span>
  </button>
</template>

<style scoped>
.rab {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  height: 56px;
  padding: 0 8px 0 20px;
  border-radius: var(--radius-pill);
  color: var(--color-text);
  font-size: var(--font-size-md);
}

.rab--accent {
  background: var(--gradient-accent);
}
.rab--purple {
  background: linear-gradient(90deg, #3a1230 0%, #9c2f6e 100%);
}
.rab--dark {
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.rab__label {
  flex: 1;
}

.rab__badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  margin-left: auto;
  color: #fff;
}
.rab__badge--green {
  background: #4e9c17;
}
.rab__badge--pink {
  background: #d7609f;
}
.rab__badge :deep(svg) {
  width: 22px;
  height: 22px;
}
</style>
