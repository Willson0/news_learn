<script>
import AppButton from '@/components/ui/AppButton.vue'
import IconOil from '@/components/icons/materials/IconOil.vue'
import IconGold from '@/components/icons/materials/IconGold.vue'
import IconGas from '@/components/icons/materials/IconGas.vue'
import IconBtc from '@/components/icons/materials/IconBtc.vue'

export default {
  name: 'ReportCard',
  components: { AppButton, IconOil, IconGold, IconGas, IconBtc },
  props: {
    report: { type: Object, required: true },
    // report: { title, date, description, badge, icon, actionLabel }
  },
  emits: ['open'],
}
</script>

<template>
  <article class="report-card">
    <div class="report-card__media">
      <span v-if="report.badge" class="report-card__badge">{{ report.badge }}</span>
    </div>

    <div class="report-card__body">
      <div class="report-card__head">
        <div class="report-card__titles">
          <h3 class="report-card__title">{{ report.title }}</h3>
          <p class="report-card__date">{{ report.date }}</p>
        </div>
        <component :is="report.icon" v-if="report.icon" class="report-card__icon" />
      </div>

      <p class="report-card__text">{{ report.description }}</p>

      <AppButton variant="soft" @click="$emit('open', report)">
        {{ report.actionLabel || 'Перейти в отчёт' }}
      </AppButton>
    </div>
  </article>
</template>

<style scoped>
.report-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 16px;
  border-radius: var(--radius-card);
  background: var(--color-surface);
  overflow: hidden;
}

.report-card__media {
  position: relative;
  height: 162px;
  border-radius: var(--radius-card);
  /* Голографическая «фольга» из макета — приближение на CSS-градиентах */
  background:
    linear-gradient(115deg, #c9c6d6 0%, #a9adbd 18%, #cfc3cf 34%, #b7c2bf 52%, #c7bcc9 70%, #a7adba 86%, #c4c7d2 100%),
    radial-gradient(60% 80% at 25% 20%, rgba(190, 150, 170, 0.5), transparent 60%),
    radial-gradient(50% 70% at 80% 70%, rgba(150, 170, 150, 0.5), transparent 60%);
}

.report-card__badge {
  position: absolute;
  top: 16px;
  right: 16px;
  padding: 4px 12px;
  border-radius: var(--radius-card);
  background: var(--color-badge);
  color: var(--color-text);
  font-size: var(--font-size-sm);
  font-weight: 600;
}

.report-card__body {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 0 16px;
}

.report-card__head {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.report-card__titles {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.report-card__title {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.report-card__date {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

.report-card__icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  color: var(--color-text);
}

.report-card__text {
  font-size: var(--font-size-base);
  line-height: 1.35;
}
</style>
