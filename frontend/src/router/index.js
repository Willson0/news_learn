import { createRouter, createWebHistory } from 'vue-router'

import LoginView from '@/views/LoginView.vue'
import HomeView from '@/views/HomeView.vue'
import PasswordRecoveryView from '@/views/PasswordRecoveryView.vue'
import AnalyticsView from '@/views/AnalyticsView.vue'
import CommunityView from '@/views/CommunityView.vue'
import ProfileView from '@/views/ProfileView.vue'

const routes = [
  { path: '/', redirect: '/home' },

  // Экран «Вход и регистрация»
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { hideNav: true },
  },
  // Экран «Вход восстановления пароля»
  {
    path: '/password-recovery',
    name: 'password-recovery',
    component: PasswordRecoveryView,
    meta: { hideNav: true },
  },
  // Экран «Главная»
  {
    path: '/home',
    name: 'home',
    component: HomeView,
  },
  // Экран «Аналитика отчёты»
  {
    path: '/analytics',
    name: 'analytics',
    component: AnalyticsView,
  },
  // Экран «Сообщество»
  {
    path: '/community',
    name: 'community',
    component: CommunityView,
  },
  // Экран «Профиль»
  {
    path: '/profile',
    name: 'profile',
    component: ProfileView,
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router
