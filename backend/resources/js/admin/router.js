import { createRouter, createWebHistory } from 'vue-router';
import { api, session } from './api';
import LoginPage from './pages/LoginPage.vue';
import UsersPage from './pages/UsersPage.vue';
import StatsPage from './pages/StatsPage.vue';
import SalesPage from './pages/SalesPage.vue';
import InvoicesPage from './pages/InvoicesPage.vue';
import ReportsPage from './pages/ReportsPage.vue';
import ChatsPage from './pages/ChatsPage.vue';
import PushPage from './pages/PushPage.vue';
import AdminsPage from './pages/AdminsPage.vue';
import SettingsPage from './pages/SettingsPage.vue';

const routes = [
    { path: '/login', name: 'login', component: LoginPage, meta: { public: true } },
    { path: '/', redirect: '/users' },
    { path: '/users', name: 'users', component: UsersPage },
    { path: '/stats', name: 'stats', component: StatsPage },
    { path: '/sales', name: 'sales', component: SalesPage },
    { path: '/invoices', name: 'invoices', component: InvoicesPage },
    { path: '/reports', name: 'reports', component: ReportsPage },
    { path: '/chats', name: 'chats', component: ChatsPage },
    { path: '/push', name: 'push', component: PushPage },
    { path: '/admins', name: 'admins', component: AdminsPage },
    { path: '/settings', name: 'settings', component: SettingsPage },
];

const router = createRouter({
    history: createWebHistory('/admin'),
    routes,
});

router.beforeEach(async (to) => {
    if (!session.checked) {
        try {
            session.login = (await api('GET', '/me')).login;
        } catch {
            session.login = null;
        }
        session.checked = true;
    }

    if (!to.meta.public && !session.login) return { name: 'login' };
    if (to.meta.public && session.login) return { name: 'users' };
    return true;
});

export default router;
