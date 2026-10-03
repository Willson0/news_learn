import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from '@/api/client'
import { loadSession, isAdmin } from '@/api/session'

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
import ReportFormView from '@/views/admin/ReportFormView.vue'
import AdminsView from '@/views/admin/AdminsView.vue'

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
  // Админка: создание отчёта
  {
    path: '/analytics/report/new',
    name: 'report-create',
    component: ReportFormView,
    meta: { hideNav: true, admin: true },
  },
  // Админка: редактирование / удаление отчёта
  {
    path: '/analytics/report/:id/edit',
    name: 'report-edit',
    component: ReportFormView,
    meta: { hideNav: true, admin: true },
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
    meta: { hideNav: true, admin: true },
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
  // Админка: список администраторов
  {
    path: '/profile/admins',
    name: 'admins',
    component: AdminsView,
    meta: { hideNav: true, admin: true },
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

// Экраны входа и восстановления пароля доступны без авторизации (hideNav).
// Остальные требуют токен — иначе уводим на вход.
const PUBLIC_ROUTES = ['login', 'password-recovery']

router.beforeEach(async (to) => {
  if (PUBLIC_ROUTES.includes(to.name)) return true
  if (!isAuthenticated()) return { name: 'login' }
  // Права админа проверяет бэкенд; здесь только не пускаем на экраны админки остальных.
  await loadSession()
  if (to.meta.admin && !isAdmin()) return { name: 'home' }
  return true
})

export default router
