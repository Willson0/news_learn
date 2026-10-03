<script>
import MaterialChips from '@/components/home/MaterialChips.vue'
import ReportCard from '@/components/home/ReportCard.vue'
import { markRaw } from 'vue'
import IconOil from '@/components/icons/materials/IconOil.vue'
import IconGold from '@/components/icons/materials/IconGold.vue'
import IconBtc from '@/components/icons/materials/IconBtc.vue'

export default {
  name: 'HomeView',
  components: { MaterialChips, ReportCard },
  data() {
    return {
      filter: 'all',
      // Демо-данные. Реальные материалы придут с бэкенда.
      reports: [
        {
          id: 1,
          title: 'Нефть в 2026 году, как она?',
          date: '12.02.2026',
          description:
            'Здесь мы расскажем о состоянии нефти на момент 2026 года и возможное ее будущее',
          badge: 'Актуальный',
          icon: markRaw(IconOil),
          material: 'wti',
        },
        {
          id: 2,
          title: 'Золото: тихая гавань или пузырь?',
          date: '10.02.2026',
          description:
            'Разбираем динамику золота и что ждёт драгоценные металлы в ближайшие месяцы',
          badge: 'Актуальный',
          icon: markRaw(IconGold),
          material: 'gold',
        },
        {
          id: 3,
          title: 'Биткоин после халвинга',
          date: '05.02.2026',
          description: 'Что происходит с криптовалютой и стоит ли ждать нового максимума',
          icon: markRaw(IconBtc),
          material: 'btc',
        },
      ],
    }
  },
  computed: {
    visibleReports() {
      if (this.filter === 'all') return this.reports
      return this.reports.filter((r) => r.material === this.filter)
    },
  },
  methods: {
    openReport() {
      // TODO: переход на детальную страницу отчёта
      this.$router.push({ name: 'analytics' })
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
