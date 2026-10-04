<script>
import AppButton from '@/components/ui/AppButton.vue'
import IconPencil from '@/components/icons/IconPencil.vue'
import IconEye from '@/components/icons/IconEye.vue'
import IconEyeClosed from '@/components/icons/IconEyeClosed.vue'
import { fetchProfile } from '@/api/resources'
import { logout } from '@/api/auth'

export default {
  name: 'AccountDataView',
  components: { AppButton, IconPencil, IconEye, IconEyeClosed },
  data() {
    return {
      email: '',
      phone: '',
      passwordRevealed: false,
      passwordMask: '●●●●●●●',
      password: 'password',
    }
  },
  async mounted() {
    try {
      const profile = await fetchProfile()
      this.email = profile.user?.email || ''
      this.phone = profile.user?.phone || ''
    } catch {
      /* оставляем пустые значения */
    }
  },
  methods: {
    changeEmail() {
      this.$router.push({ name: 'account-change', params: { field: 'email' } })
    },
    changePhone() {
      this.$router.push({ name: 'account-change', params: { field: 'phone' } })
    },
    changePassword() {
      this.$router.push({ name: 'account-change', params: { field: 'password' } })
    },
    async logout() {
      await logout()
      this.$router.push({ name: 'login' })
    },
  },
}
</script>

<template>
  <section class="account">

    <h1 class="account__title screen-title">Данные пользователя</h1>

    <div class="account__card">
      <div class="account__row">
        <span class="account__text">
          <span class="account__label">Email:</span> {{ email }}
        </span>
        <button type="button" class="account__action" aria-label="Изменить email" @click="changeEmail">
          <IconPencil />
        </button>
      </div>
      <div class="account__divider"></div>

      <div class="account__row">
        <span class="account__text">
          <span class="account__label">Номер телефона:</span> {{ phone }}
        </span>
        <button type="button" class="account__action" aria-label="Изменить номер" @click="changePhone">
          <IconPencil />
        </button>
      </div>
      <div class="account__divider"></div>

      <div class="account__row">
        <span class="account__text">
          <span class="account__label">Пароль:</span>
          {{ passwordRevealed ? password : passwordMask }}
        </span>
        <button
          type="button"
          class="account__action"
          :aria-label="passwordRevealed ? 'Скрыть пароль' : 'Показать пароль'"
          @click="passwordRevealed = !passwordRevealed"
        >
          <IconEye v-if="passwordRevealed" />
          <IconEyeClosed v-else />
        </button>
      </div>
    </div>

    <button type="button" class="account__link" @click="changePassword">Сменить пароль</button>

    <div class="account__buttons">
      <AppButton variant="accent" @click="changePassword">Сменить пароль</AppButton>
      <AppButton variant="danger" @click="logout">Выйти из аккаунта</AppButton>
    </div>
  </section>
</template>

<style scoped>
.account {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 16px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 40px);
  background: var(--color-bg);
}

.account__top {
  display: flex;
  align-items: center;
}

.account__title {
  margin: 4px 0 4px;
}

.account__card {
  padding: 8px 16px;
  border-radius: var(--radius-card);
  background: var(--color-surface);
}

.account__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0;
}

.account__text {
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.account__label {
  color: var(--color-text-secondary);
}

.account__divider {
  height: 1px;
  background: rgba(255, 255, 255, 0.08);
}

.account__action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--color-text);
  flex-shrink: 0;
}
.account__action svg {
  width: 22px;
  height: 22px;
}

.account__link {
  align-self: flex-end;
  color: var(--color-text-muted);
  font-size: var(--font-size-base);
}

.account__buttons {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 4px;
}
</style>
