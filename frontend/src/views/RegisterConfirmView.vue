<script>
import AppInput from '@/components/ui/AppInput.vue'
import AppButton from '@/components/ui/AppButton.vue'
import { haptic } from '@/telegram/webapp'
import { confirmRegistration, resendRegistrationCode } from '@/api/auth'
import { ApiError } from '@/api/client'

export default {
  name: 'RegisterConfirmView',
  components: { AppInput, AppButton },
  data() {
    return {
      email: this.$route.query.email || '',
      code: '',
      error: '',
      submitting: false,
      resendIn: 0, // секунды до повторной отправки
      timer: null,
    }
  },
  computed: {
    maskedEmail() {
      const [name, domain] = this.email.split('@')
      if (!domain) return this.email
      const head = name.length <= 2 ? name : name.slice(0, 2) + '***'
      return `${head}@${domain}`
    },
  },
  mounted() {
    // Без email подтверждать нечего — возвращаем на регистрацию.
    if (!this.email) {
      this.$router.replace({ name: 'login' })
      return
    }
    this.startCooldown()
  },
  beforeUnmount() {
    if (this.timer) clearInterval(this.timer)
  },
  methods: {
    onCodeInput(val) {
      // Оставляем только цифры, максимум 6.
      this.code = String(val).replace(/\D/g, '').slice(0, 6)
      this.error = ''
    },

    startCooldown() {
      this.resendIn = 60
      if (this.timer) clearInterval(this.timer)
      this.timer = setInterval(() => {
        this.resendIn -= 1
        if (this.resendIn <= 0) clearInterval(this.timer)
      }, 1000)
    },

    async confirm() {
      if (this.code.length < 6) {
        this.error = 'Введите код из 6 цифр'
        haptic('rigid')
        return
      }
      haptic('medium')
      this.submitting = true
      try {
        await confirmRegistration({ email: this.email, code: this.code })
        this.$router.replace({ name: 'home' })
      } catch (e) {
        haptic('rigid')
        this.error = e instanceof ApiError ? this.firstError(e) : 'Не удалось подтвердить код'
      } finally {
        this.submitting = false
      }
    },

    async resend() {
      if (this.resendIn > 0 || this.submitting) return
      haptic('light')
      try {
        await resendRegistrationCode(this.email)
        this.code = ''
        this.error = ''
        this.startCooldown()
      } catch (e) {
        this.error = e instanceof ApiError ? this.firstError(e) : 'Не удалось отправить код'
      }
    },

    firstError(e) {
      const errs = e.errors || {}
      const first = Object.values(errs)[0]
      if (first) return Array.isArray(first) ? first[0] : String(first)
      return e.message
    },
  },
}
</script>

<template>
  <section class="confirm">
    <div class="confirm__logo" aria-hidden="true"></div>

    <h1 class="confirm__title screen-title">Подтверждение почты</h1>
    <p class="confirm__subtitle">
      Мы отправили код подтверждения на <b>{{ maskedEmail }}</b>. Введите его ниже.
    </p>

    <div class="confirm__field">
      <AppInput
        :model-value="code"
        placeholder="Код из письма"
        inputmode="numeric"
        autocomplete="one-time-code"
        :error="!!error"
        @update:model-value="onCodeInput"
      />
      <p v-if="error" class="confirm__error">{{ error }}</p>
    </div>

    <button
      type="button"
      class="confirm__resend"
      :disabled="resendIn > 0"
      @click="resend"
    >
      {{ resendIn > 0 ? `Отправить код повторно (${resendIn})` : 'Отправить код повторно' }}
    </button>

    <div class="confirm__actions">
      <AppButton variant="accent" :disabled="submitting" @click="confirm">
        Подтвердить
      </AppButton>
    </div>
  </section>
</template>

<style scoped>
.confirm {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 40px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 96px);
  background:
    radial-gradient(120% 60% at 85% 6%, rgba(120, 40, 90, 0.35) 0%, rgba(26, 27, 29, 0) 55%),
    radial-gradient(90% 50% at 20% 4%, rgba(90, 70, 40, 0.25) 0%, rgba(26, 27, 29, 0) 50%),
    linear-gradient(180deg, #1a1b1d 0%, #1e1f21 48%, #161719 100%);
}

.confirm__logo {
  width: 72px;
  height: 72px;
  margin: 8px auto 28px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.confirm__title {
  text-align: center;
  margin-bottom: 12px;
}

.confirm__subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  text-align: center;
  line-height: 1.5;
  margin-bottom: 28px;
}

.confirm__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.confirm__error {
  font-size: var(--font-size-sm);
  color: #ff4d4d;
}

.confirm__resend {
  margin-top: 16px;
  align-self: flex-start;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
.confirm__resend:disabled {
  opacity: 0.5;
}

/* Кнопка прижата к низу экрана по ширине мобильного кадра */
.confirm__actions {
  position: fixed;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: var(--app-width);
  max-width: 100%;
  z-index: 5;
  padding: 16px var(--space-screen-x) calc(env(safe-area-inset-bottom, 0px) + 16px);
  background: linear-gradient(180deg, rgba(22, 23, 25, 0) 0%, #161719 22%);
}

@media (max-width: 420px) {
  .confirm__actions {
    width: 100%;
  }
}
</style>
