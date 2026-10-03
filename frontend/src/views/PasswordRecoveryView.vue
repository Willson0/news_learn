<script>
import AppInput from '@/components/ui/AppInput.vue'
import AppButton from '@/components/ui/AppButton.vue'
import BackButton from '@/components/ui/BackButton.vue'
import { haptic } from '@/telegram/webapp'
import { requestRecovery, resetPassword } from '@/api/auth'
import { ApiError } from '@/api/client'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const RESEND_SECONDS = 5 * 60

export default {
  name: 'PasswordRecoveryView',
  components: { AppInput, AppButton, BackButton },
  data() {
    return {
      email: '',
      newPassword: '',
      errors: {},
      secondsLeft: RESEND_SECONDS,
      timer: null,
    }
  },
  computed: {
    canResend() {
      return this.secondsLeft <= 0
    },
    resendLabel() {
      if (this.canResend) return 'Отправить повторно'
      const m = Math.floor(this.secondsLeft / 60)
      const s = String(this.secondsLeft % 60).padStart(2, '0')
      return `Повторное сообщение через ${m}:${s}`
    },
  },
  mounted() {
    this.startTimer()
  },
  beforeUnmount() {
    this.stopTimer()
  },
  methods: {
    startTimer() {
      this.stopTimer()
      this.timer = setInterval(() => {
        if (this.secondsLeft > 0) this.secondsLeft -= 1
        else this.stopTimer()
      }, 1000)
    },
    stopTimer() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
    },
    async resend() {
      if (!this.canResend && this.secondsLeft < RESEND_SECONDS) return
      if (!EMAIL_RE.test(this.email.trim())) {
        this.errors = { email: 'Введите email, чтобы отправить код' }
        haptic('rigid')
        return
      }
      haptic('light')
      try {
        await requestRecovery(this.email.trim())
      } catch {
        /* показываем таймер в любом случае */
      }
      this.secondsLeft = RESEND_SECONDS
      this.startTimer()
    },
    async submit() {
      const errors = {}
      if (!EMAIL_RE.test(this.email.trim())) {
        errors.email = 'Неверный формат email'
      }
      if (this.newPassword.length < 8) {
        errors.newPassword = 'Пароль должен содержать от 8 символов'
      }
      this.errors = errors
      if (Object.keys(errors).length) {
        haptic('rigid')
        return
      }
      haptic('medium')
      try {
        // Если код ещё не запрашивали — делаем это перед сбросом.
        await requestRecovery(this.email.trim())
        await resetPassword(this.email.trim(), this.newPassword)
        this.$router.push({ name: 'home' })
      } catch (e) {
        haptic('rigid')
        const msg = e instanceof ApiError ? e.message : 'Не удалось сменить пароль'
        this.errors = { email: msg }
      }
    },
  },
}
</script>

<template>
  <section class="recovery">
    <header class="recovery__top">
      <BackButton :to="{ name: 'login' }" />
    </header>

    <h1 class="recovery__title screen-title">Восстановление пароля</h1>
    <p class="recovery__subtitle">
      Временный пароль придёт на указанную почту, позднее пароль можно сменить в
      профиле
    </p>

    <form class="recovery__form" novalidate @submit.prevent="submit">
      <div class="recovery__field">
        <AppInput
          v-model="email"
          type="email"
          inputmode="email"
          placeholder="Email"
          autocomplete="email"
          :error="!!errors.email"
        />
        <p v-if="errors.email" class="recovery__error">{{ errors.email }}</p>
      </div>

      <div class="recovery__field">
        <AppInput
          v-model="newPassword"
          type="password"
          placeholder="Новый пароль"
          autocomplete="new-password"
          :error="!!errors.newPassword"
        />
        <p v-if="errors.newPassword" class="recovery__error">{{ errors.newPassword }}</p>
      </div>
    </form>

    <div class="recovery__actions">
      <AppButton variant="secondary" :disabled="!canResend" @click="resend">
        {{ resendLabel }}
      </AppButton>
      <AppButton variant="accent" @click="submit">Далее</AppButton>
    </div>
  </section>
</template>

<style scoped>
.recovery {
  display: flex;
  flex-direction: column;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 24px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 24px);
  background: linear-gradient(180deg, #1a1b1d 0%, #1e1f21 48%, #161719 100%);
}

.recovery__top {
  margin-bottom: 76px;
}

.recovery__title {
  text-align: center;
  margin-bottom: 12px;
}

.recovery__subtitle {
  max-width: 300px;
  margin: 0 auto 28px;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.35;
}

.recovery__form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recovery__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.recovery__error {
  font-size: var(--font-size-sm);
  color: #ff4d4d;
}

.recovery__actions {
  margin-top: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 24px;
}
</style>
