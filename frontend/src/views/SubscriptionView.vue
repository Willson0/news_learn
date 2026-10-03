<script>
import BackButton from '@/components/ui/BackButton.vue'
import AppToggle from '@/components/ui/AppToggle.vue'
import IconPlus from '@/components/icons/IconPlus.vue'
import { fetchSubscription, updateSubscription } from '@/api/resources'

export default {
  name: 'SubscriptionView',
  components: { BackButton, AppToggle, IconPlus },
  data() {
    return {
      active: false,
      until: '',
      plan: '',
      // Привязанный способ оплаты (null — если не привязан)
      account: null,
      autoPay: false,
    }
  },
  watch: {
    autoPay(value) {
      this.save({ auto_pay: value })
    },
  },
  async mounted() {
    try {
      const sub = await fetchSubscription()
      if (sub) {
        this.active = sub.active
        this.until = sub.until
        this.plan = sub.plan
        this.autoPay = sub.auto_pay
        this.account = sub.payment_method ? { label: sub.payment_method } : null
      }
    } catch {
      /* значения по умолчанию */
    }
  },
  methods: {
    async save(payload) {
      try {
        await updateSubscription(payload)
      } catch {
        /* игнорируем */
      }
    },
    toggleAccount() {
      this.account = this.account ? null : { label: 'СБП 1488' }
      this.save({ payment_method: this.account ? this.account.label : null })
    },
  },
}
</script>

<template>
  <section class="sub">
    <div class="sub__top">
      <BackButton :to="{ name: 'profile' }" />
    </div>

    <h1 class="sub__title screen-title">Управление подпиской</h1>

    <!-- Статус подписки -->
    <div class="sub__card">
      <div class="sub__row">
        <span class="sub__label">Подписка</span>
        <span class="sub__value" :class="active ? 'sub__value--active' : 'sub__value--inactive'">
          {{ active ? 'Активна' : 'Неактивна' }}
          <small v-if="active" class="sub__until">до {{ until }}</small>
        </span>
      </div>
      <div class="sub__divider"></div>
      <div class="sub__row">
        <span class="sub__label">Тарифный план</span>
        <span class="sub__value sub__value--muted">{{ plan }}</span>
      </div>
    </div>

    <!-- Способ оплаты -->
    <div class="sub__card sub__pay">
      <span class="sub__avatar" :class="{ 'sub__avatar--add': !account }">
        <IconPlus v-if="!account" />
      </span>
      <span class="sub__pay-label">{{ account ? account.label : 'Новый счет' }}</span>
      <button type="button" class="sub__pill" @click="toggleAccount">
        {{ account ? 'Отвязать' : 'Привязать' }}
      </button>
    </div>

    <!-- Авто-платёж -->
    <div class="sub__card sub__autopay">
      <span class="sub__label">Авто-платеж</span>
      <AppToggle v-model="autoPay" />
    </div>
  </section>
</template>

<style scoped>
.sub {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 16px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 40px);
  background: var(--color-bg);
}

.sub__top {
  display: flex;
  align-items: center;
}

.sub__title {
  margin: 4px 0 4px;
}

.sub__card {
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.sub__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 0;
}

.sub__label {
  font-size: var(--font-size-md);
  font-weight: 600;
}

.sub__value {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: var(--font-size-base);
}
.sub__value--active {
  color: #7bc043;
  font-weight: 600;
}
.sub__value--inactive {
  color: var(--color-error);
  font-weight: 600;
}
.sub__value--muted {
  color: var(--color-text-secondary);
}

.sub__until {
  color: var(--color-text-secondary);
  font-weight: 400;
}

.sub__divider {
  height: 1px;
  background: rgba(255, 255, 255, 0.08);
  margin: 10px 0;
}

.sub__pay {
  display: flex;
  align-items: center;
  gap: 12px;
}

.sub__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #ffffff;
  color: #1a1b1d;
  flex-shrink: 0;
}
.sub__avatar svg {
  width: 20px;
  height: 20px;
}

.sub__pay-label {
  flex: 1;
  font-size: var(--font-size-md);
}

.sub__pill {
  padding: 9px 18px;
  border-radius: var(--radius-pill);
  background: var(--gradient-purple-solid);
  color: var(--color-text);
  font-size: var(--font-size-base);
}

.sub__autopay {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
