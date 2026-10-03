<script>
import BackButton from '@/components/ui/BackButton.vue'
import AppToggle from '@/components/ui/AppToggle.vue'
import InstrumentChip from '@/components/ui/InstrumentChip.vue'

export default {
  name: 'NotificationsView',
  components: { BackButton, AppToggle, InstrumentChip },
  data() {
    return {
      allNotifications: true,
      byInstrument: false,
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
    }
  },
}
</script>

<template>
  <section class="notif">
    <div class="notif__top">
      <BackButton :to="{ name: 'profile' }" />
    </div>

    <h1 class="notif__title screen-title">Уведомления</h1>

    <!-- Основные переключатели -->
    <div class="notif__card">
      <div class="notif__row">
        <span class="notif__row-label">Все уведомления</span>
        <AppToggle v-model="allNotifications" />
      </div>
      <!-- «Только по инструментам» показывается, когда общие уведомления выключены -->
      <div v-if="!allNotifications" class="notif__row">
        <span class="notif__row-label">Только по инструментам</span>
        <AppToggle v-model="byInstrument" />
      </div>
    </div>

    <!-- Уведомления по инструментам -->
    <h2 class="notif__section screen-title">Уведомления<br />по инструментам</h2>
    <p class="notif__hint">
      Для этой функции выше включите уведомления по инструментам
    </p>

    <div class="notif__card">
      <div class="notif__chips">
        <InstrumentChip
          v-for="item in instruments"
          :key="item.key"
          v-model="item.on"
          :label="item.label"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.notif {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 16px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 40px);
  background: var(--color-bg);
}

.notif__top {
  display: flex;
  align-items: center;
}

.notif__title {
  margin: 4px 0 0;
}

.notif__card {
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--color-surface);
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.notif__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.notif__row-label {
  font-size: var(--font-size-md);
}

.notif__section {
  margin: 8px 0 0;
  line-height: 1.2;
}

.notif__hint {
  margin: -8px 0 0;
  font-size: var(--font-size-base);
  color: var(--color-text-muted);
}

.notif__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
