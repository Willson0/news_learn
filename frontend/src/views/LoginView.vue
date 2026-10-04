<script>
import AppInput from '@/components/ui/AppInput.vue'
import AppButton from '@/components/ui/AppButton.vue'
import { haptic, getTelegramUser } from '@/telegram/webapp'
import { loginWithPassword, register, loginWithTelegram } from '@/api/auth'
import { ApiError } from '@/api/client'

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const PHONE_RE = /^\+?[0-9\s\-()]{7,}$/

export default {
  name: 'LoginView',
  components: { AppInput, AppButton },
  data() {
    const tg = getTelegramUser()
    return {
      mode: 'login', // 'login' | 'register'
      // Данные Telegram-аккаунта для аватара над формой.
      tgUser: tg,
      avatarError: false,
      form: {
        identifier: '', // вход: email или телефон
        email: '',
        phone: '',
        password: '',
        passwordConfirm: '',
      },
      errors: {},
      submitting: false,
    }
  },
  computed: {
    // Ссылка на аватар из Telegram (если открыто в Telegram и загрузилась).
    avatarUrl() {
      return this.tgUser && this.tgUser.photo_url && !this.avatarError ? this.tgUser.photo_url : null
    },
    // Буква-заглушка, если фото нет.
    avatarInitial() {
      const n = (this.tgUser && (this.tgUser.first_name || this.tgUser.username)) || ''
      return n ? n.trim().charAt(0).toUpperCase() : ''
    },
  },
  async mounted() {
    this.measureActions()
    window.addEventListener('resize', this.measureActions)
    // Если приложение открыто внутри Telegram — входим автоматически по initData.
    try {
      const user = await loginWithTelegram()
      if (user) this.$router.replace({ name: 'home' })
    } catch {
      // Проверка не прошла — оставляем форму входа.
    }
  },
  updated() {
    this.measureActions()
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.measureActions)
  },
  methods: {
    setMode(mode) {
      if (this.mode === mode) return
      this.mode = mode
      this.errors = {}
      haptic('light')
    },

    validateLogin() {
      const errors = {}
      const id = this.form.identifier.trim()
      if (!id) {
        errors.identifier = 'Введите email или номер телефона'
      } else if (!EMAIL_RE.test(id) && !PHONE_RE.test(id)) {
        errors.identifier = 'Неверный формат email или телефона'
      }
      if (!this.form.password) {
        errors.password = 'Введите пароль'
      }
      this.errors = errors
      return Object.keys(errors).length === 0
    },

    validateRegister() {
      const errors = {}
      if (!EMAIL_RE.test(this.form.email.trim())) {
        errors.email = 'Неверный формат email'
      }
      if (!PHONE_RE.test(this.form.phone.trim())) {
        errors.phone = 'Неверный формат телефона'
      }
      if (this.form.password.length < 8) {
        errors.password = 'Пароль должен содержать от 8 символов'
      }
      if (this.form.passwordConfirm !== this.form.password) {
        errors.passwordConfirm = 'Пароль не совпадает'
      }
      this.errors = errors
      return Object.keys(errors).length === 0
    },

    async submit() {
      const ok = this.mode === 'login' ? this.validateLogin() : this.validateRegister()
      if (!ok) {
        haptic('rigid')
        return
      }
      haptic('medium')
      this.submitting = true
      try {
        if (this.mode === 'login') {
          await loginWithPassword(this.form.identifier.trim(), this.form.password)
          this.$router.push({ name: 'home' })
        } else {
          const email = this.form.email.trim()
          await register({
            email,
            phone: this.form.phone.trim(),
            password: this.form.password,
          })
          // Код ушёл на почту — переходим к экрану подтверждения.
          this.$router.push({ name: 'register-confirm', query: { email } })
        }
      } catch (e) {
        haptic('rigid')
        if (e instanceof ApiError) {
          // Поля ошибок от Laravel ({ identifier: [..], email: [..] }).
          const mapped = {}
          for (const [key, msgs] of Object.entries(e.errors || {})) {
            mapped[key] = Array.isArray(msgs) ? msgs[0] : String(msgs)
          }
          this.errors = Object.keys(mapped).length
            ? mapped
            : { identifier: e.message, email: e.message }
        } else {
          this.errors = { identifier: 'Не удалось выполнить запрос' }
        }
      } finally {
        this.submitting = false
      }
    },

    // Высота блока кнопок → нижний отступ контента, чтобы кнопки его не перекрывали.
    measureActions() {
      this.$nextTick(() => {
        const el = this.$refs.actions
        if (el) this.$el.style.setProperty('--auth-actions-h', `${el.offsetHeight}px`)
      })
    },

    loginWithYandex() {
      haptic('light')
      // TODO: OAuth через Yandex (на бэкенде пока не реализован).
    },

    goToRecovery() {
      this.$router.push({ name: 'password-recovery' })
    },
  },
}
</script>

<template>
  <section class="auth">
    <span class="auth__age">16+</span>

    <!-- Аватар из Telegram-аккаунта (с заглушкой, если фото недоступно) -->
    <div class="auth__logo">
      <img
        v-if="avatarUrl"
        class="auth__logo-img"
        :src="avatarUrl"
        alt=""
        referrerpolicy="no-referrer"
        @error="avatarError = true"
      />
      <span v-else-if="avatarInitial" class="auth__logo-initial">{{ avatarInitial }}</span>
    </div>

    <!-- Переключатель Вход / Регистрация -->
    <h1 class="auth__toggle screen-title">
      <button
        type="button"
        class="auth__toggle-part"
        :class="{ 'is-active': mode === 'login' }"
        @click="setMode('login')"
      >
        Вход
      </button>
      <span class="auth__toggle-sep"> / </span>
      <button
        type="button"
        class="auth__toggle-part"
        :class="{ 'is-active': mode === 'register' }"
        @click="setMode('register')"
      >
        Регистрация
      </button>
    </h1>

    <!-- Форма -->
    <form class="auth__form" novalidate @submit.prevent="submit">
      <!-- Вход -->
      <template v-if="mode === 'login'">
        <div class="auth__field">
          <AppInput
            v-model="form.identifier"
            placeholder="Email или номер телефона"
            autocomplete="username"
            :error="!!errors.identifier"
          />
          <p v-if="errors.identifier" class="auth__error">{{ errors.identifier }}</p>
        </div>

        <div class="auth__field">
          <AppInput
            v-model="form.password"
            type="password"
            placeholder="Пароль"
            autocomplete="current-password"
            :error="!!errors.password"
          />
          <div class="auth__row">
            <p v-if="errors.password" class="auth__error">{{ errors.password }}</p>
            <button type="button" class="auth__link" @click="goToRecovery">
              Забыли пароль?
            </button>
          </div>
        </div>
      </template>

      <!-- Регистрация -->
      <template v-else>
        <div class="auth__field">
          <AppInput
            v-model="form.email"
            type="email"
            placeholder="Email"
            inputmode="email"
            autocomplete="email"
            :error="!!errors.email"
          />
          <p v-if="errors.email" class="auth__error">{{ errors.email }}</p>
        </div>

        <div class="auth__field">
          <AppInput
            v-model="form.phone"
            type="tel"
            placeholder="Номер телефона"
            inputmode="tel"
            autocomplete="tel"
            :error="!!errors.phone"
          />
          <p v-if="errors.phone" class="auth__error">{{ errors.phone }}</p>
        </div>

        <div class="auth__field">
          <AppInput
            v-model="form.password"
            type="password"
            placeholder="Пароль"
            autocomplete="new-password"
            :error="!!errors.password"
          />
          <p class="auth__hint" :class="{ 'auth__error': errors.password }">
            Пароль должен содержать от 8 символов
          </p>
        </div>

        <div class="auth__field">
          <AppInput
            v-model="form.passwordConfirm"
            type="password"
            placeholder="Подтвердите пароль"
            autocomplete="new-password"
            :error="!!errors.passwordConfirm"
          />
          <p v-if="errors.passwordConfirm" class="auth__error">
            {{ errors.passwordConfirm }}
          </p>
        </div>
      </template>
    </form>

    <!-- Кнопки всегда прижаты к низу экрана -->
    <div ref="actions" class="auth__actions">
      <AppButton variant="secondary" @click="loginWithYandex">Войти с Yandex</AppButton>
      <AppButton variant="accent" :disabled="submitting" @click="submit">
        {{ mode === 'login' ? 'Войти' : 'Далее' }}
      </AppButton>
    </div>
  </section>
</template>

<style scoped>
.auth {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: var(--app-height);
  /* снизу — место под зафиксированные кнопки, чтобы они не перекрывали контент */
  padding: calc(env(safe-area-inset-top, 0px) + 24px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + var(--auth-actions-h, 140px) + 16px);
  /* Фоновое свечение из макета (декоративное пятно сверху) */
  background:
    radial-gradient(120% 60% at 85% 6%, rgba(120, 40, 90, 0.35) 0%, rgba(26, 27, 29, 0) 55%),
    radial-gradient(90% 50% at 20% 4%, rgba(90, 70, 40, 0.25) 0%, rgba(26, 27, 29, 0) 50%),
    linear-gradient(180deg, #1a1b1d 0%, #1e1f21 48%, #161719 100%);
}

.auth__age {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 24px);
  right: var(--space-screen-x);
  font-family: var(--font-heading);
  font-size: var(--font-size-title);
  color: var(--color-text-muted);
}

.auth__logo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100px;
  height: 100px;
  margin: 24px auto 0;
  border-radius: 50%;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.auth__logo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.auth__logo-initial {
  font-family: var(--font-heading);
  font-size: 40px;
  color: var(--color-text);
}

.auth__toggle {
  margin: 28px 0 24px;
  text-align: center;
}

.auth__toggle-part {
  font-family: var(--font-heading);
  font-size: var(--font-size-title);
  color: var(--color-text-muted);
  transition: color 0.15s ease;
}
.auth__toggle-part.is-active {
  color: var(--color-text);
}
.auth__toggle-sep {
  color: var(--color-text-muted);
}

.auth__form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.auth__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.auth__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
/* Если ошибки нет — «Забыли пароль?» прижат вправо */
.auth__row .auth__link {
  margin-left: auto;
}

.auth__error {
  font-size: var(--font-size-sm);
  color: #ff4d4d;
}

.auth__hint {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
.auth__hint.auth__error {
  color: #ff4d4d;
}

.auth__link {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

/* Кнопки зафиксированы у нижней кромки экрана, по ширине мобильного кадра */
.auth__actions {
  position: fixed;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: var(--app-width);
  max-width: 100%;
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px var(--space-screen-x) calc(env(safe-area-inset-bottom, 0px) + 16px);
  background: linear-gradient(180deg, rgba(22, 23, 25, 0) 0%, #161719 22%);
}

@media (max-width: 420px) {
  .auth__actions {
    width: 100%;
  }
}
</style>
