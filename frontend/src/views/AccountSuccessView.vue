<script>
import BackButton from '@/components/ui/BackButton.vue'

const MESSAGES = {
  email: 'Вы успешно сменили почту',
  phone: 'Вы успешно сменили номер',
  password: 'Вы успешно сменили пароль',
}

export default {
  name: 'AccountSuccessView',
  components: { BackButton },
  computed: {
    message() {
      return MESSAGES[this.$route.params.field] || 'Готово'
    },
  },
}
</script>

<template>
  <section class="success">
    <div class="success__top">
      <BackButton :to="{ name: 'account-data' }" />
    </div>
    <p class="success__text">{{ message }}</p>
  </section>
</template>

<style scoped>
.success {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: var(--app-height);
  padding: var(--space-screen-x);
  background: var(--color-bg);
}

.success__top {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 16px);
  left: var(--space-screen-x);
}

.success__text {
  font-size: var(--font-size-lg);
  text-align: center;
}
</style>
