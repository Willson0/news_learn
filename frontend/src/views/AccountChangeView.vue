<script>
import BackButton from '@/components/ui/BackButton.vue'
import AppButton from '@/components/ui/AppButton.vue'
import AppInput from '@/components/ui/AppInput.vue'
import { changeEmail, changePhone, changePassword } from '@/api/resources'
import { ApiError } from '@/api/client'

const CONFIG = {
  email: {
    title: 'Смена почты',
    fields: [
      { key: 'old', placeholder: 'Прежняя почта', type: 'email' },
      { key: 'new', placeholder: 'Новая почта', type: 'email' },
    ],
    button: 'Сменить почту',
  },
  phone: {
    title: 'Смена номера телефона',
    fields: [
      { key: 'old', placeholder: 'Прежний номер телефона', type: 'tel' },
      { key: 'new', placeholder: 'Новый номер телефона', type: 'tel' },
    ],
    button: 'Сменить номер',
  },
  password: {
    title: 'Смена пароля',
    fields: [
      { key: 'new', placeholder: 'Новый пароль', type: 'password' },
      { key: 'confirm', placeholder: 'Подтвердите пароль', type: 'password' },
    ],
    button: 'Сменить пароль',
  },
}

export default {
  name: 'AccountChangeView',
  components: { BackButton, AppButton, AppInput },
  data() {
    return {
      values: { old: '', new: '', confirm: '' },
      error: '',
    }
  },
  computed: {
    field() {
      return this.$route.params.field || 'email'
    },
    config() {
      return CONFIG[this.field] || CONFIG.email
    },
  },
  methods: {
    async submit() {
      this.error = ''
      try {
        if (this.field === 'email') {
          await changeEmail(this.values.new.trim())
        } else if (this.field === 'phone') {
          await changePhone(this.values.new.trim())
        } else {
          await changePassword(this.values.new, this.values.confirm, this.values.old)
        }
        this.$router.push({ name: 'account-success', params: { field: this.field } })
      } catch (e) {
        this.error = e instanceof ApiError ? e.message : 'Не удалось сохранить изменения'
      }
    },
  },
}
</script>

<template>
  <section class="change">
    <div class="change__top">
      <BackButton :to="{ name: 'account-data' }" />
    </div>

    <div class="change__content">
      <h1 class="change__title">{{ config.title }}</h1>
      <div class="change__fields">
        <AppInput
          v-for="f in config.fields"
          :key="f.key"
          v-model="values[f.key]"
          :type="f.type"
          :placeholder="f.placeholder"
        />
      </div>
      <p v-if="error" class="change__error">{{ error }}</p>
    </div>

    <AppButton class="change__submit" variant="accent" @click="submit">
      {{ config.button }}
    </AppButton>
  </section>
</template>

<style scoped>
.change {
  display: flex;
  flex-direction: column;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 16px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 24px);
  background: var(--color-bg);
}

.change__top {
  display: flex;
  align-items: center;
}

.change__content {
  margin-top: 26%;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.change__title {
  margin: 0;
  text-align: center;
  font-size: var(--font-size-title);
  font-weight: 700;
}

.change__fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.change__submit {
  margin-top: auto;
}
.change__error {
  margin-top: 12px;
  font-size: var(--font-size-sm);
  color: #ff4d4d;
}
</style>
