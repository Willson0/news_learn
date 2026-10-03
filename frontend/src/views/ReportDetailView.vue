<script>
import BackButton from '@/components/ui/BackButton.vue'
import ReportActionButton from '@/components/ui/ReportActionButton.vue'
import CandlestickChart from '@/components/analytics/CandlestickChart.vue'
import IconDoc from '@/components/icons/IconDoc.vue'
import IconChat from '@/components/icons/IconChat.vue'
import IconChartBadge from '@/components/icons/IconChartBadge.vue'
import IconOil from '@/components/icons/materials/IconOil.vue'

export default {
  name: 'ReportDetailView',
  components: {
    BackButton,
    ReportActionButton,
    CandlestickChart,
    IconDoc,
    IconChat,
    IconChartBadge,
    IconOil,
  },
  data() {
    return {
      graphOpen: false,
      reportOpen: false,
      report: {
        title: 'Нефть в 2026 году, как она?',
        date: '12.02.26',
        badge: 'Актуальный',
        description:
          'Здесь мы расскажем о состоянии нефти на момент 2026 года и возможное ее будущее',
      },
    }
  },
  methods: {
    toggleReport() {
      this.reportOpen = !this.reportOpen
    },
    toggleGraph() {
      this.graphOpen = !this.graphOpen
    },
    discuss() {
      this.$router.push({ name: 'community' })
    },
  },
}
</script>

<template>
  <section class="report">
    <div class="report__hero">
      <div class="report__hero-overlay"></div>
      <BackButton class="report__back" />
      <span class="report__badge">{{ report.badge }}</span>

      <div class="report__hero-body">
        <div class="report__hero-head">
          <div class="report__titles">
            <h1 class="report__title">{{ report.title }}</h1>
            <p class="report__date">{{ report.date }}</p>
          </div>
          <span class="report__icon"><IconOil /></span>
        </div>
        <p class="report__desc">{{ report.description }}</p>
      </div>
    </div>

    <div class="report__actions">
      <ReportActionButton label="Открыть отчёт" variant="accent" badge="green" @click="toggleReport">
        <template #icon><IconDoc /></template>
      </ReportActionButton>

      <ReportActionButton label="Обсудить" variant="purple" badge="pink" @click="discuss">
        <template #icon><IconChat /></template>
      </ReportActionButton>

      <ReportActionButton
        :label="graphOpen ? 'Закрыть график' : 'Открыть график'"
        variant="dark"
        badge="green"
        @click="toggleGraph"
      >
        <template #icon><IconChartBadge /></template>
      </ReportActionButton>
    </div>

    <article v-if="reportOpen" class="report__article">
      <p>
        Нефть остаётся одним из ключевых индикаторов мировой экономики. В 2026 году
        на цену влияют баланс спроса и предложения, политика ОПЕК+ и курс доллара.
      </p>
      <p>
        В этом отчёте мы разбираем основные сценарии, факторы риска и возможные
        уровни цены на горизонте ближайших месяцев.
      </p>
    </article>

    <CandlestickChart v-if="graphOpen" class="report__chart" />
  </section>
</template>

<style scoped>
.report {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: var(--app-height);
  padding: 0 var(--space-screen-x) calc(env(safe-area-inset-bottom, 0px) + 32px);
  background: var(--color-bg);
}

.report__hero {
  position: relative;
  margin: 0 calc(-1 * var(--space-screen-x));
  min-height: 420px;
  padding: calc(env(safe-area-inset-top, 0px) + 24px) var(--space-screen-x) 24px;
  border-radius: 0 0 var(--radius-card) var(--radius-card);
  background:
    linear-gradient(115deg, #c9c6d6 0%, #a9adbd 20%, #cfc3cf 38%, #b7c2bf 55%, #c7bcc9 72%, #a7adba 88%, #c4c7d2 100%);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.report__hero-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(26, 27, 29, 0.35) 0%, rgba(26, 27, 29, 0.1) 35%, rgba(26, 27, 29, 0.85) 100%);
}

.report__back {
  position: relative;
  z-index: 1;
}

.report__badge {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 28px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 1;
  padding: 8px 16px;
  border-radius: var(--radius-card);
  background: rgba(40, 40, 44, 0.7);
  backdrop-filter: blur(6px);
  font-size: var(--font-size-sm);
}

.report__hero-body {
  position: relative;
  z-index: 1;
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.report__hero-head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.report__titles {
  flex: 1;
  min-width: 0;
}

.report__title {
  font-size: 20px;
  font-weight: 700;
}

.report__date {
  margin-top: 4px;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

.report__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: rgba(40, 40, 44, 0.7);
  color: #fff;
  flex-shrink: 0;
}
.report__icon :deep(svg) {
  width: 18px;
  height: 18px;
}

.report__desc {
  font-size: var(--font-size-base);
  line-height: 1.4;
  max-width: 300px;
}

.report__actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.report__article {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--color-surface);
  font-size: var(--font-size-base);
  line-height: 1.45;
  color: var(--color-text);
}
</style>
