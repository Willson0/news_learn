<script>
import MaterialChips from '@/components/home/MaterialChips.vue'
import ReportCard from '@/components/home/ReportCard.vue'
import { fetchReports } from '@/api/resources'
import { decorateReport } from '@/api/materialIcon'

export default {
  name: 'HomeView',
  components: { MaterialChips, ReportCard },
  data() {
    return {
      filter: 'all',
      reports: [],
      loading: false,
    }
  },
  watch: {
    filter() {
      this.load()
    },
  },
  mounted() {
    this.load()
  },
  computed: {
    visibleReports() {
      return this.reports
    },
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const data = await fetchReports({ instrument: this.filter })
        this.reports = data.map(decorateReport)
      } catch {
        this.reports = []
      } finally {
        this.loading = false
      }
    },
    openReport(report) {
      this.$router.push({ name: 'report-detail', params: { id: report.id } })
    },
  },
}
</script>

<template>
  <section class="home">
    <h1 class="home__title screen-title">Лента материалов</h1>

    <MaterialChips v-model="filter" class="home__chips" />

    <div class="home__list">
      <ReportCard
        v-for="report in visibleReports"
        :key="report.id"
        :report="report"
        @open="openReport"
      />
      <p v-if="!visibleReports.length" class="home__empty">
        По этому фильтру материалов пока нет
      </p>
    </div>
  </section>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 60px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 120px);
  background: var(--color-bg);
}

.home__title {
  margin: 0;
}

.home__chips {
  margin: 0 calc(-1 * 0px);
}

.home__list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.home__empty {
  padding: 40px 0;
  text-align: center;
  color: var(--color-text-secondary);
}
</style>
