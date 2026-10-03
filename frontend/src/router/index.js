import { createRouter, createWebHistory } from 'vue-router'

import LoginView from '@/views/LoginView.vue'
import HomeView from '@/views/HomeView.vue'
import PasswordRecoveryView from '@/views/PasswordRecoveryView.vue'
import AnalyticsView from '@/views/AnalyticsView.vue'
import ReportDetailView from '@/views/ReportDetailView.vue'
import CommunityView from '@/views/CommunityView.vue'
import ProfileView from '@/views/ProfileView.vue'
import ProfileEditView from '@/views/ProfileEditView.vue'
import NotificationsView from '@/views/NotificationsView.vue'
import AccountDataView from '@/views/AccountDataView.vue'
import AccountChangeView from '@/views/AccountChangeView.vue'
import AccountSuccessView from '@/views/AccountSuccessView.vue'
import SubscriptionView from '@/views/SubscriptionView.vue'
import ChatView from '@/views/ChatView.vue'
import ChatInfoView from '@/views/ChatInfoView.vue'
import ChatEditView from '@/views/ChatEditView.vue'
import UserProfileView from '@/views/UserProfileView.vue'

const routes = [
  { path: '/', redirect: '/login' },

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
  // Детальный отчёт с графиком
  {
    path: '/analytics/report/:id?',
    name: 'report-detail',
    component: ReportDetailView,
    meta: { hideNav: true },
  },
  // Экран «Сообщество»
  {
    path: '/community',
    name: 'community',
    component: CommunityView,
  },
  // Экран чата
  {
    path: '/community/chat/:id',
    name: 'chat',
    component: ChatView,
    meta: { hideNav: true },
  },
  // Информация о чате
  {
    path: '/community/chat/:id/info',
    name: 'chat-info',
    component: ChatInfoView,
    meta: { hideNav: true },
  },
  // Настройки чата (админ)
  {
    path: '/community/chat/:id/edit',
    name: 'chat-edit',
    component: ChatEditView,
    meta: { hideNav: true },
  },
  // Профиль пользователя
  {
    path: '/community/chat/:id/user/:uid',
    name: 'chat-user',
    component: UserProfileView,
    meta: { hideNav: true },
  },
  // Экран «Профиль»
  {
    path: '/profile',
    name: 'profile',
    component: ProfileView,
  },
  // Редактирование профиля
  {
    path: '/profile/edit',
    name: 'profile-edit',
    component: ProfileEditView,
    meta: { hideNav: true },
  },
  // Push-уведомления
  {
    path: '/profile/notifications',
    name: 'notifications',
    component: NotificationsView,
    meta: { hideNav: true },
  },
  // Данные аккаунта
  {
    path: '/profile/account',
    name: 'account-data',
    component: AccountDataView,
    meta: { hideNav: true },
  },
  // Смена почты / номера / пароля
  {
    path: '/profile/account/change/:field',
    name: 'account-change',
    component: AccountChangeView,
    meta: { hideNav: true },
  },
  // Экран успешной смены
  {
    path: '/profile/account/success/:field',
    name: 'account-success',
    component: AccountSuccessView,
    meta: { hideNav: true },
  },
  // Управление подпиской
  {
    path: '/profile/subscription',
    name: 'subscription',
    component: SubscriptionView,
    meta: { hideNav: true },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

export default router
