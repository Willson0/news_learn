<template>
    <section>
        <header class="page-head">
            <div>
                <h1>Пользователи</h1>
                <p class="muted">Тестовые данные</p>
            </div>
            <input v-model="q" class="input search" type="search" placeholder="Поиск по имени, email, телефону" aria-label="Поиск" @input="onSearch">
        </header>

        <div class="chips">
            <button v-for="f in filters" :key="f.id" class="btn" :class="filter === f.id ? 'btn-primary' : 'btn-secondary'" @click="setFilter(f.id)">
                {{ f.label }}
            </button>
        </div>

        <ErrorText :message="error" />
        <table class="table">
            <thead>
                <tr>
                    <th>Пользователь</th>
                    <th>Статус</th>
                    <th>Подписка</th>
                    <th>Действует до</th>
                    <th>Отчётов</th>
                    <th />
                </tr>
            </thead>
            <tbody>
                <tr v-for="u in users" :key="u.id">
                    <td>
                        <div class="strong">{{ u.name }}</div>
                        <div class="muted small">{{ u.email || '—' }}</div>
                    </td>
                    <td><span class="tag" :class="tagClass(u.group)">{{ u.status }}</span></td>
                    <td>{{ u.plan }}</td>
                    <td>{{ u.until || '—' }}</td>
                    <td>{{ u.reports }}</td>
                    <td class="actions">
                        <button class="btn btn-secondary" @click="openDetail(u.id)">Подробнее</button>
                    </td>
                </tr>
                <tr v-if="!loading && users.length === 0">
                    <td colspan="6" class="muted">Никого не нашли</td>
                </tr>
            </tbody>
        </table>

        <Pager :page="meta.page" :last-page="meta.last_page" @change="load" />

        <UserDrawer v-if="detailId" :user-id="detailId" @close="detailId = null" @changed="load(meta.page)" @invoice="invoiceFor" />
        <InvoiceDialog v-if="invoiceUser" :user="invoiceUser" @close="invoiceUser = null" @done="load(meta.page)" />
    </section>
</template>

<script>
import { api } from '../api';
import ErrorText from '../components/ErrorText.vue';
import Pager from '../components/Pager.vue';
import UserDrawer from '../components/UserDrawer.vue';
import InvoiceDialog from '../components/InvoiceDialog.vue';

export default {
    name: 'UsersPage',
    components: { ErrorText, Pager, UserDrawer, InvoiceDialog },
    data() {
        return {
            users: [],
            meta: { page: 1, last_page: 1, total: 0 },
            q: '',
            filter: 'all',
            filters: [
                { id: 'all', label: 'Все' },
                { id: 'free', label: 'Без подписки' },
                { id: 'active', label: 'Активные' },
                { id: 'expired', label: 'Истекшие' },
            ],
            loading: false,
            error: '',
            detailId: null,
            invoiceUser: null,
            searchTimer: null,
        };
    },
    mounted() {
        this.load(1);
    },
    beforeUnmount() {
        clearTimeout(this.searchTimer);
    },
    methods: {
        async load(page = 1) {
            this.loading = true;
            this.error = '';
            const params = new URLSearchParams({ page, filter: this.filter, q: this.q });
            try {
                const res = await api('GET', `/users?${params}`);
                this.users = res.data;
                this.meta = res.meta;
            } catch (e) {
                this.error = e.message;
            } finally {
                this.loading = false;
            }
        },
        setFilter(id) {
            this.filter = id;
            this.load(1);
        },
        onSearch() {
            clearTimeout(this.searchTimer);
            this.searchTimer = setTimeout(() => this.load(1), 300);
        },
        openDetail(id) {
            this.detailId = id;
        },
        invoiceFor(user) {
            this.detailId = null;
            this.invoiceUser = user;
        },
        tagClass(group) {
            return { active: 'tag-accent', expired: 'tag-outline', free: 'tag-neutral' }[group];
        },
    },
};
</script>
