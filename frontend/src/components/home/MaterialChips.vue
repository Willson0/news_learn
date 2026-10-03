<script>
import IconGold from '@/components/icons/materials/IconGold.vue'
import IconOil from '@/components/icons/materials/IconOil.vue'
import IconUsd from '@/components/icons/materials/IconUsd.vue'
import IconEur from '@/components/icons/materials/IconEur.vue'
import IconGas from '@/components/icons/materials/IconGas.vue'
import IconBtc from '@/components/icons/materials/IconBtc.vue'

export default {
  name: 'MaterialChips',
  components: { IconGold, IconOil, IconUsd, IconEur, IconGas, IconBtc },
  props: {
    modelValue: { type: String, default: 'all' },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      items: [
        { key: 'all', label: 'Все' },
        { key: 'gold', label: 'Золото', icon: 'IconGold' },
        { key: 'silver', label: 'Серебро', icon: 'IconGold' },
        { key: 'platinum', label: 'Платина', icon: 'IconGold' },
        { key: 'wti', label: 'Нефть WTI', icon: 'IconOil' },
        { key: 'brent', label: 'Нефть Brent', icon: 'IconOil' },
        { key: 'usd', label: 'USD', icon: 'IconUsd' },
        { key: 'eur', label: 'EUR', icon: 'IconEur' },
        { key: 'gas', label: 'Натуральный газ', icon: 'IconGas' },
        { key: 'btc', label: 'Биткоин', icon: 'IconBtc' },
      ],
    }
  },
  methods: {
    select(key) {
      this.$emit('update:modelValue', key)
    },
  },
}
</script>

<template>
  <div class="chips no-scrollbar">
    <button
      v-for="item in items"
      :key="item.key"
      type="button"
      class="chip"
      :class="{ 'chip--active': modelValue === item.key }"
      @click="select(item.key)"
    >
      <span class="chip__label">{{ item.label }}</span>
      <component :is="item.icon" v-if="item.icon" class="chip__icon" />
    </button>
  </div>
</template>

<style scoped>
.chips {
  display: flex;
  gap: 4px;
  align-items: center;
  height: 56px;
  padding: 4px;
  border-radius: var(--radius-card);
  background: var(--color-surface-muted);
  overflow-x: auto;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 48px;
  padding: 0 12px;
  border-radius: var(--radius-chip);
  color: var(--color-text);
  white-space: nowrap;
  flex-shrink: 0;
}

.chip--active {
  background: var(--gradient-accent-purple);
}

.chip__label {
  font-size: var(--font-size-base);
}

.chip__icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}
</style>
