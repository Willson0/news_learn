<script>
import IconHome from '@/components/icons/IconHome.vue'
import IconAnalytics from '@/components/icons/IconAnalytics.vue'
import IconCommunity from '@/components/icons/IconCommunity.vue'
import IconProfile from '@/components/icons/IconProfile.vue'

export default {
  name: 'BottomNav',
  components: { IconHome, IconAnalytics, IconCommunity, IconProfile },
  data() {
    return {
      tabs: [
        { name: 'home', label: 'Главная', icon: 'IconHome' },
        { name: 'analytics', label: 'Отчёты', icon: 'IconAnalytics' },
        { name: 'community', label: 'Сообщество', icon: 'IconCommunity' },
        { name: 'profile', label: 'Профиль', icon: 'IconProfile' },
      ],
    }
  },
}
</script>

<template>
  <nav class="bottom-nav" aria-label="Основная навигация">
    <router-link
      v-for="tab in tabs"
      :key="tab.name"
      :to="{ name: tab.name }"
      class="bottom-nav__tab"
      active-class="bottom-nav__tab--active"
      :aria-label="tab.label"
    >
      <component :is="tab.icon" class="bottom-nav__icon" />
    </router-link>
  </nav>
</template>

<style scoped>
.bottom-nav {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 15px;
  padding: 12px;
  border-radius: var(--radius-pill);
  background: var(--color-navbar-bg);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  z-index: 100;
}

.bottom-nav__tab {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-pill);
  color: var(--color-text);
  opacity: 0.6;
  transition: opacity 0.15s ease;
}

.bottom-nav__tab--active {
  opacity: 1;
  background: var(--gradient-accent-soft);
}

.bottom-nav__icon {
  width: 24px;
  height: 24px;
}
</style>
