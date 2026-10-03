<script>
import AppInput from '@/components/ui/AppInput.vue'
import ReportCard from '@/components/home/ReportCard.vue'
import IconFilter from '@/components/icons/IconFilter.vue'
import { markRaw } from 'vue'
import IconOil from '@/components/icons/materials/IconOil.vue'
import IconGold from '@/components/icons/materials/IconGold.vue'
import IconGas from '@/components/icons/materials/IconGas.vue'

export default {
  name: 'AnalyticsView',
  components: { AppInput, ReportCard, IconFilter },
  data() {
    return {
      query: '',
      // Демо-данные. Реальные отчёты придут с бэкенда.
      reports: [
        {
          id: 1,
          title: 'Нефть в 2026 году, как она?',
          date: '12.02.2026',
          description:
            'Здесь мы расскажем о состоянии нефти на момент 2026 года и возможное ее будущее',
          badge: 'Актуальный',
          icon: markRaw(IconOil),
        },
        {
          id: 2,
          title: 'Золото: отчёт за февраль',
          date: '11.02.2026',
          description: 'Полный разбор динамики драгоценных металлов за прошедший месяц',
          badge: 'Актуальный',
          icon: markRaw(IconGold),
        },
        {
          id: 3,
          title: 'Природный газ: прогноз',
          date: '08.02.2026',
          description: 'Что ждёт рынок природного газа в начале года',
          icon: markRaw(IconGas),
        },
      ],
    }
  },
  computed: {
    visibleReports() {
      const q = this.query.trim().toLowerCase()
      if (!q) return this.reports
      return this.reports.filter(
        (r) =>
          r.title.toLowerCase().includes(q) || r.description.toLowerCase().includes(q),
      )
    },
  },
  methods: {
    openReport() {
      // TODO: переход на детальную страницу отчёта с графиком
    },
    openFilters() {
      // TODO: панель фильтров (по инструменту и по дате)
    },
  },
}
</script>

<template>
  <section class="analytics">
    <h1 class="analytics__title screen-title">Список материалов</h1>

    <div class="analytics__search">
      <AppInput v-model="query" placeholder="Нужный отчёт или материал" class="analytics__input" />
      <button
        type="button"
        class="analytics__filter"
        aria-label="Фильтры"
        @click="openFilters"
      >
        <IconFilter />
      </button>
    </div>

    <div class="analytics__list">
      <ReportCard
        v-for="report in visibleReports"
        :key="report.id"
        :report="report"
        @open="openReport"
      />
      <p v-if="!visibleReports.length" class="analytics__empty">Ничего не найдено</p>
    </div>
  </section>
</template>

<style scoped>
.analytics {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 60px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 120px);
  background: var(--color-bg);
}

.analytics__title {
  margin: 0;
}

.analytics__search {
  display: flex;
  gap: 12px;
  align-items: center;
}

.analytics__input {
  flex: 1;
}

.analytics__filter {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: var(--radius-card);
  background: var(--gradient-accent-soft);
  color: var(--color-text);
}
.analytics__filter svg {
  width: 20px;
  height: 20px;
}

.analytics__list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.analytics__empty {
  padding: 40px 0;
  text-align: center;
  color: var(--color-text-secondary);
}
</style>
