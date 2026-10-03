<script>
import AppInput from '@/components/ui/AppInput.vue'
import ReportCard from '@/components/home/ReportCard.vue'
import IconFilter from '@/components/icons/IconFilter.vue'
import FiltersSheet from '@/components/analytics/FiltersSheet.vue'
import { fetchReports } from '@/api/resources'
import { decorateReport } from '@/api/materialIcon'

export default {
  name: 'AnalyticsView',
  components: { AppInput, ReportCard, IconFilter, FiltersSheet },
  data() {
    return {
      query: '',
      filtersOpen: false,
      statusFilter: null,
      reports: [],
    }
  },
  mounted() {
    this.load()
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
    async load() {
      try {
        const data = await fetchReports({ status: this.statusFilter })
        this.reports = data.map(decorateReport)
      } catch {
        this.reports = []
      }
    },
    openReport(report) {
      this.$router.push({ name: 'report-detail', params: { id: report.id } })
    },
    openFilters() {
      this.filtersOpen = true
    },
    applyFilters(filters) {
      this.statusFilter = filters && filters.statuses && filters.statuses.length
        ? filters.statuses
        : null
      this.load()
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

    <FiltersSheet :open="filtersOpen" @close="filtersOpen = false" @apply="applyFilters" />
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
