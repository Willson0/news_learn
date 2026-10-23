<template>
    <div v-if="session.login" class="shell">
        <aside class="sidebar">
            <div class="brand">Карта Ликвидности</div>
            <nav class="nav">
                <router-link v-for="item in nav" :key="item.to" :to="item.to" class="nav-link">
                    {{ item.label }}
                </router-link>
            </nav>
            <div class="sidebar-foot">
                <span class="muted">{{ session.login }}</span>
                <button class="btn btn-ghost" @click="logout">Выйти</button>
            </div>
        </aside>
        <main class="content">
            <router-view />
        </main>
    </div>
    <router-view v-else />
</template>

<script>
import { api, session } from './api';

export default {
    name: 'App',
    data() {
        return {
            session,
            nav: [
                { to: '/users', label: 'Пользователи' },
                { to: '/stats', label: 'Статистика' },
                { to: '/sales', label: 'Продажи' },
                { to: '/invoices', label: 'Счета' },
                { to: '/reports', label: 'Отчёты' },
                { to: '/chats', label: 'Чаты' },
                { to: '/push', label: 'Рассылка' },
                { to: '/admins', label: 'Администраторы' },
                { to: '/settings', label: 'Настройки' },
            ],
        };
    },
    methods: {
        async logout() {
            try {
                await api('POST', '/logout');
            } finally {
                session.login = null;
                session.checked = true;
                this.$router.push({ name: 'login' });
            }
        },
    },
};
</script>
