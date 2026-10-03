<script>
import InstrumentChip from '@/components/ui/InstrumentChip.vue'
import AppButton from '@/components/ui/AppButton.vue'
import ReportCard from '@/components/home/ReportCard.vue'
import IconSettings from '@/components/icons/IconSettings.vue'
import IconSort from '@/components/icons/IconSort.vue'
import IconChevronRight from '@/components/icons/IconChevronRight.vue'
import { fetchProfile, syncInstruments, fetchReports } from '@/api/resources'
import { decorateReport } from '@/api/materialIcon'
import { session } from '@/api/session'

export default {
  name: 'ProfileView',
  components: {
    InstrumentChip,
    AppButton,
    ReportCard,
    IconSettings,
    IconSort,
    IconChevronRight,
  },
  data() {
    return {
      user: {
        tag: '@AlexeyRub',
      },
      subscription: {
        active: false,
        until: '',
        plan: '',
      },
      // Отслеживаемые инструменты (загружаются с бэкенда).
      instruments: [],
      settings: [
        { key: 'notifications', label: 'Push-уведомления', route: { name: 'notifications' } },
        { key: 'account', label: 'Данные аккаунта', route: { name: 'account-data' } },
        { key: 'agreement', label: 'Пользовательское соглашение', route: null },
      ],
      sortOpen: false,
      sort: 'new',
      sortOptions: [
        { value: 'new', label: 'Сначала новые' },
        { value: 'old', label: 'Сначала Старые' },
        { value: 'name', label: 'По названию' },
      ],
      // История материалов (загружается с бэкенда).
      history: [],
    }
  },
  computed: {
    // Пункт «Администраторы» виден только админам.
    settingsItems() {
      if (!(session.user && session.user.is_admin)) return this.settings
      return [{ key: 'admins', label: 'Администраторы', route: { name: 'admins' } }, ...this.settings]
    },
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      try {
        const profile = await fetchProfile()
        if (profile.user) {
          this.user = { ...this.user, tag: profile.user.username || this.user.tag }
        }
        if (profile.subscription) {
          this.subscription = {
            active: profile.subscription.active,
            until: profile.subscription.until,
            plan: profile.subscription.plan,
          }
        }
        if (Array.isArray(profile.instruments)) {
          this.instruments = profile.instruments
        }
      } catch {
        /* оставляем значения по умолчанию */
      }
      try {
        const reports = await fetchReports()
        this.history = reports.length
          ? [{ day: 'Недавние', items: reports.map(decorateReport) }]
          : []
      } catch {
        this.history = []
      }
    },
    async saveInstruments() {
      const keys = this.instruments.filter((i) => i.on).map((i) => i.key)
      try {
        await syncInstruments(keys)
      } catch {
        /* игнорируем — состояние переключателя уже обновлено локально */
      }
    },
    openSettings() {
      this.$router.push({ name: 'profile-edit' })
    },
    go(item) {
      if (item.route) this.$router.push(item.route)
    },
    toggleSort() {
      this.sortOpen = !this.sortOpen
    },
    pickSort(value) {
      this.sort = value
      this.sortOpen = false
    },
    openReport(report) {
      this.$router.push({ name: 'report-detail', params: { id: report.id } })
    },
  },
}
</script>

<template>
  <section class="profile">
    <!-- Шапка с аватаром -->
    <header class="profile__hero">
      <button type="button" class="profile__gear" aria-label="Настройки" @click="openSettings">
        <IconSettings />
      </button>
      <div class="profile__avatar"></div>
      <p class="profile__tag">{{ user.tag }}</p>
    </header>

    <!-- Подписка -->
    <div class="profile__card profile__subscription">
      <div class="profile__row">
        <span class="profile__row-label">Подписка</span>
        <span class="profile__row-value profile__row-value--active">
          {{ subscription.active ? 'Активна' : 'Неактивна' }}
          <small class="profile__until">до {{ subscription.until }}</small>
        </span>
      </div>
      <div class="profile__row">
        <span class="profile__row-label">Тарифный план</span>
        <span class="profile__row-value">{{ subscription.plan }}</span>
      </div>
      <AppButton variant="accent" @click="$router.push({ name: 'subscription' })">
        Управление подпиской
      </AppButton>
    </div>

    <!-- Отслеживаемые инструменты -->
    <h2 class="profile__section screen-title">Отслеживаемые инструменты</h2>
    <div class="profile__card">
      <div class="profile__chips">
        <InstrumentChip
          v-for="item in instruments"
          :key="item.key"
          v-model="item.on"
          :label="item.label"
          @update:model-value="saveInstruments"
        />
      </div>
    </div>

    <!-- Настройки -->
    <h2 class="profile__section screen-title">Настройки</h2>
    <ul class="profile__settings">
      <li
        v-for="item in settingsItems"
        :key="item.key"
        class="profile__setting"
        @click="go(item)"
      >
        <span>{{ item.label }}</span>
        <IconChevronRight class="profile__chevron" />
      </li>
    </ul>

    <!-- История -->
    <div class="profile__history-head">
      <h2 class="profile__section screen-title">История</h2>
      <div class="profile__sort">
        <button type="button" class="profile__sort-btn" aria-label="Сортировка" @click="toggleSort">
          <IconSort />
        </button>
        <ul v-if="sortOpen" class="profile__sort-menu">
          <li
            v-for="opt in sortOptions"
            :key="opt.value"
            class="profile__sort-item"
            :class="{ 'profile__sort-item--active': sort === opt.value }"
            @click="pickSort(opt.value)"
          >
            <span>{{ opt.label }}</span>
            <svg
              v-if="sort === opt.value"
              class="profile__check"
              viewBox="0 0 16 16"
              fill="none"
              aria-hidden="true"
            >
              <path d="M3 8.5 6.5 12 13 4" stroke="#fff" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </li>
        </ul>
      </div>
    </div>

    <div class="profile__history">
      <div v-for="group in history" :key="group.day" class="profile__history-group">
        <p class="profile__day">{{ group.day }}</p>
        <ReportCard
          v-for="report in group.items"
          :key="report.id"
          :report="report"
          @open="openReport"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
.profile {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: var(--app-height);
  padding: 0 var(--space-screen-x) calc(env(safe-area-inset-bottom, 0px) + 120px);
  background: var(--color-bg);
}

/* --- Шапка --- */
.profile__hero {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  margin: 0 calc(-1 * var(--space-screen-x));
  padding: calc(env(safe-area-inset-top, 0px) + 56px) var(--space-screen-x) 20px;
  background:
    radial-gradient(120% 90% at 50% 0%, rgba(60, 80, 40, 0.55), transparent 70%),
    linear-gradient(180deg, #3a3a32 0%, #232420 100%);
}

.profile__gear {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 16px);
  right: var(--space-screen-x);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--color-surface-muted);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: var(--color-text);
}
.profile__gear svg {
  width: 22px;
  height: 22px;
}

.profile__avatar {
  width: 104px;
  height: 104px;
  border-radius: 50%;
  background:
    radial-gradient(60% 60% at 35% 30%, #8a9a6a, transparent 70%),
    linear-gradient(135deg, #6b7350 0%, #3f4a2c 100%);
  border: 3px solid rgba(255, 255, 255, 0.12);
}

.profile__tag {
  font-size: var(--font-size-lg);
  font-weight: 700;
}

/* --- Карточки --- */
.profile__card {
  padding: 16px;
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.profile__subscription {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.profile__row-label {
  font-size: var(--font-size-md);
  font-weight: 600;
}

.profile__row-value {
  display: flex;
  align-items: baseline;
  gap: 8px;
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

.profile__row-value--active {
  color: #7bc043;
  font-weight: 600;
}

.profile__until {
  color: var(--color-text-secondary);
  font-weight: 400;
}

.profile__section {
  margin: 0;
}

.profile__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* --- Настройки --- */
.profile__settings {
  display: flex;
  flex-direction: column;
}

.profile__setting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 4px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  font-size: var(--font-size-md);
  cursor: pointer;
}
.profile__setting:last-child {
  border-bottom: none;
}

.profile__chevron {
  width: 18px;
  height: 18px;
  color: var(--color-text-secondary);
}

/* --- История --- */
.profile__history-head {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.profile__sort-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--gradient-accent);
  color: var(--color-text);
}
.profile__sort-btn svg {
  width: 22px;
  height: 22px;
}

.profile__sort-menu {
  position: absolute;
  top: 52px;
  right: 0;
  z-index: 20;
  min-width: 190px;
  padding: 6px;
  border-radius: 16px;
  background: #2c2d30;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.45);
}

.profile__sort-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: var(--font-size-base);
  cursor: pointer;
}
.profile__sort-item--active {
  color: var(--color-text);
}
.profile__check {
  width: 16px;
  height: 16px;
}

.profile__history {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile__history-group {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.profile__day {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}
</style>
