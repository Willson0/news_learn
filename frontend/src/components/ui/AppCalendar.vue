<script>
import IconBack from '@/components/icons/IconBack.vue'
import IconChevronRight from '@/components/icons/IconChevronRight.vue'

const MONTHS = [
  'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
  'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь',
]
const WEEKDAYS = ['ПН', 'ВТ', 'СР', 'ЧТ', 'ПТ', 'СБ', 'ВС']

export default {
  name: 'AppCalendar',
  components: { IconBack, IconChevronRight },
  props: {
    // Выбранная дата (ISO-строка) и «сегодня» для подсветки
    modelValue: { type: String, default: '2026-03-17' },
    today: { type: String, default: '2026-03-28' },
  },
  emits: ['update:modelValue'],
  data() {
    const d = new Date(this.modelValue || '2026-03-01')
    return {
      viewYear: d.getFullYear(),
      viewMonth: d.getMonth(),
      weekdays: WEEKDAYS,
    }
  },
  computed: {
    monthLabel() {
      return MONTHS[this.viewMonth]
    },
    cells() {
      const year = this.viewYear
      const month = this.viewMonth
      const first = new Date(year, month, 1)
      // Понедельник — первый день недели
      const lead = (first.getDay() + 6) % 7
      const daysInMonth = new Date(year, month + 1, 0).getDate()
      const daysPrev = new Date(year, month, 0).getDate()
      const cells = []
      for (let i = 0; i < lead; i += 1) {
        cells.push({ day: daysPrev - lead + 1 + i, out: true })
      }
      for (let d = 1; d <= daysInMonth; d += 1) {
        const iso = `${year}-${String(month + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
        cells.push({ day: d, iso, out: false })
      }
      let tail = 1
      while (cells.length % 7 !== 0) {
        cells.push({ day: tail, out: true })
        tail += 1
      }
      return cells
    },
  },
  methods: {
    prevMonth() {
      if (this.viewMonth === 0) {
        this.viewMonth = 11
        this.viewYear -= 1
      } else {
        this.viewMonth -= 1
      }
    },
    nextMonth() {
      if (this.viewMonth === 11) {
        this.viewMonth = 0
        this.viewYear += 1
      } else {
        this.viewMonth += 1
      }
    },
    select(cell) {
      if (cell.out) return
      this.$emit('update:modelValue', cell.iso)
    },
  },
}
</script>

<template>
  <div class="calendar">
    <div class="calendar__head">
      <button type="button" class="calendar__nav" aria-label="Предыдущий месяц" @click="prevMonth">
        <IconBack />
      </button>
      <span class="calendar__month">{{ monthLabel }}</span>
      <button type="button" class="calendar__nav" aria-label="Следующий месяц" @click="nextMonth">
        <IconChevronRight />
      </button>
    </div>

    <div class="calendar__divider"></div>

    <div class="calendar__grid calendar__weekdays">
      <span v-for="w in weekdays" :key="w" class="calendar__weekday">{{ w }}</span>
    </div>

    <div class="calendar__grid">
      <button
        v-for="(cell, i) in cells"
        :key="i"
        type="button"
        class="calendar__day"
        :class="{
          'calendar__day--out': cell.out,
          'calendar__day--selected': cell.iso === modelValue,
          'calendar__day--today': cell.iso === today && cell.iso !== modelValue,
        }"
        @click="select(cell)"
      >
        <span class="calendar__daynum">{{ cell.day }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.calendar {
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.calendar__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.calendar__nav {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--color-text);
}
.calendar__nav svg {
  width: 20px;
  height: 20px;
}

.calendar__month {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.calendar__divider {
  height: 1px;
  background: rgba(255, 255, 255, 0.1);
  margin-bottom: 8px;
}

.calendar__grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}

.calendar__weekday {
  text-align: center;
  padding: 6px 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.calendar__day {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  color: var(--color-text);
  font-size: var(--font-size-base);
}

.calendar__daynum {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
}

.calendar__day--out {
  color: var(--color-text-muted);
  pointer-events: none;
}

.calendar__day--selected .calendar__daynum {
  background: #5bb318;
  color: #fff;
}

.calendar__day--today .calendar__daynum {
  background: #fff;
  color: #5bb318;
}
</style>
