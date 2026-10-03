<script>
import InstrumentChip from '@/components/ui/InstrumentChip.vue'
import SegmentedControl from '@/components/ui/SegmentedControl.vue'
import AppCalendar from '@/components/ui/AppCalendar.vue'
import IconCalendar from '@/components/icons/IconCalendar.vue'
import IconClose from '@/components/icons/IconClose.vue'
import { haptic } from '@/telegram/webapp'

export default {
  name: 'FiltersSheet',
  components: { InstrumentChip, SegmentedControl, AppCalendar, IconCalendar, IconClose },
  props: {
    open: { type: Boolean, default: false },
  },
  emits: ['close', 'apply'],
  data() {
    return {
      instruments: [
        { key: 'gold', label: 'Золото', on: true },
        { key: 'silver', label: 'Серебро', on: false },
        { key: 'platinum', label: 'Платина', on: true },
        { key: 'wti', label: 'Нефть WTI', on: false },
        { key: 'brent', label: 'Нефть Brent', on: true },
        { key: 'eurusd', label: 'EUR/USD', on: false },
        { key: 'gas', label: 'Нат. газ', on: false },
        { key: 'btc', label: 'Биткоин', on: false },
      ],
      statuses: [
        { key: 'hot', label: 'С пылу с жару', on: true },
        { key: 'actual', label: 'Актуальное', on: true },
        { key: 'inactual', label: 'Не актуально', on: true },
        { key: 'archive', label: 'Архивное', on: false },
      ],
      dateMode: 'week',
      dateModes: [
        { value: 'week', label: 'Неделя' },
        { value: 'month', label: 'Месяц' },
        { value: 'year', label: 'Год' },
      ],
      selectedDate: '2026-03-17',
      range: 'С 17 МАР - 28 МАР',
      calendarOpen: true,
    }
  },
  methods: {
    close() {
      this.$emit('close')
    },
    apply() {
      haptic('medium')
      this.$emit('apply', {
        instruments: this.instruments.filter((i) => i.on).map((i) => i.key),
        statuses: this.statuses.filter((s) => s.on).map((s) => s.key),
        dateMode: this.dateMode,
      })
      this.close()
    },
    resetDate() {
      this.range = ''
      this.calendarOpen = true
    },
    toggleCalendar() {
      this.calendarOpen = !this.calendarOpen
    },
  },
}
</script>

<template>
  <transition name="sheet">
    <div v-if="open" class="sheet-root" @click.self="close">
      <div class="sheet">
        <div class="sheet__grabber"></div>

        <div class="sheet__head">
          <h2 class="sheet__title screen-title">Инструменты</h2>
          <button type="button" class="sheet__search" @click="apply">Искать</button>
        </div>

        <div class="sheet__scroll">
          <!-- Инструменты -->
          <div class="sheet__card">
            <div class="sheet__chips">
              <InstrumentChip
                v-for="item in instruments"
                :key="item.key"
                v-model="item.on"
                :label="item.label"
              />
            </div>
          </div>

          <!-- По статусу -->
          <h3 class="sheet__section screen-title">По статусу</h3>
          <div class="sheet__card">
            <div class="sheet__chips">
              <InstrumentChip
                v-for="item in statuses"
                :key="item.key"
                v-model="item.on"
                :label="item.label"
              />
            </div>
          </div>

          <!-- По дате -->
          <div class="sheet__date-head">
            <h3 class="sheet__section screen-title">По дате</h3>
            <div class="sheet__date-controls">
              <template v-if="range">
                <button type="button" class="sheet__date-reset" aria-label="Сбросить" @click="resetDate">
                  <IconClose />
                </button>
                <span class="sheet__range">{{ range }}</span>
              </template>
              <button type="button" class="sheet__cal-btn" aria-label="Календарь" @click="toggleCalendar">
                <IconCalendar />
              </button>
            </div>
          </div>

          <AppCalendar v-if="calendarOpen" v-model="selectedDate" class="sheet__calendar" />

          <SegmentedControl v-model="dateMode" :options="dateModes" class="sheet__segmented" />
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
.sheet-root {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
}

.sheet {
  width: 100%;
  max-width: var(--app-width);
  max-height: 88vh;
  display: flex;
  flex-direction: column;
  padding: 10px var(--space-screen-x) calc(env(safe-area-inset-bottom, 0px) + 20px);
  border-radius: 28px 28px 0 0;
  background: #1c1d1f;
}

.sheet__grabber {
  width: 40px;
  height: 4px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.3);
  margin: 4px auto 12px;
}

.sheet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.sheet__search {
  padding: 10px 22px;
  border-radius: var(--radius-pill);
  background: var(--gradient-accent);
  color: var(--color-text);
  font-size: var(--font-size-base);
}

.sheet__scroll {
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.sheet__card {
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.sheet__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.sheet__section {
  margin: 0;
}

.sheet__date-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.sheet__date-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sheet__date-reset {
  display: flex;
  color: var(--color-text-secondary);
}
.sheet__date-reset svg {
  width: 18px;
  height: 18px;
}

.sheet__range {
  padding: 8px 12px;
  border-radius: var(--radius-pill);
  background: var(--gradient-accent);
  font-size: var(--font-size-sm);
}

.sheet__cal-btn {
  display: flex;
  color: var(--color-text);
}
.sheet__cal-btn svg {
  width: 24px;
  height: 24px;
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity 0.2s ease;
}
.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}
</style>
