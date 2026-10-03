<script>
import IconBack from '@/components/icons/IconBack.vue'

export default {
  name: 'BackButton',
  components: { IconBack },
  props: {
    // Куда вернуться. Если не задано — router.back().
    to: { type: [String, Object], default: null },
  },
  methods: {
    onClick() {
      if (this.to) this.$router.push(this.to)
      else this.$router.back()
    },
  },
}
</script>

<template>
  <button type="button" class="back-button" aria-label="Назад" @click="onClick">
    <IconBack class="back-button__icon" />
  </button>
</template>

<style scoped>
.back-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 65px;
  background: var(--color-surface-muted);
  color: var(--color-text);
  flex-shrink: 0;
}

.back-button__icon {
  width: 22px;
  height: 22px;
}
</style>
