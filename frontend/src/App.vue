<script>
import BottomNav from '@/components/BottomNav.vue'

export default {
  name: 'App',
  components: { BottomNav },
  computed: {
    // На экранах входа/регистрации и восстановления пароля нижней навигации нет
    showNav() {
      return !this.$route.meta.hideNav
    },
  },
}
</script>

<template>
  <!-- Мобильный кадр телефона: вся вёрстка живёт внутри 393×856 -->
  <div class="app-frame">
    <main class="app-content">
      <router-view />
    </main>
    <BottomNav v-if="showNav" />
  </div>
</template>

<style scoped>
.app-frame {
  position: relative;
  width: var(--app-width);
  min-height: var(--app-height);
  margin: 0 auto;
  background: var(--color-bg);
  overflow: hidden;
}

.app-content {
  min-height: var(--app-height);
}

/* На узких экранах кадр растягивается на всю ширину окна */
@media (max-width: 420px) {
  .app-frame {
    width: 100%;
  }
}
</style>
