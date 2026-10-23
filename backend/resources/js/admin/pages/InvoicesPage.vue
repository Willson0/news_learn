<template>
    <section>
        <header class="page-head">
            <div>
                <h1>Счета</h1>
                <p class="muted">Все выставленные счета</p>
            </div>
        </header>
        <div class="chips">
            <button v-for="s in statuses" :key="s.id" class="btn" :class="status === s.id ? 'btn-primary' : 'btn-secondary'" @click="setStatus(s.id)">
                {{ s.label }}
            </button>
        </div>
        <ErrorText :message="error" />
        <table class="table">
            <thead>
                <tr><th>Пользователь</th><th>Сумма</th><th>Срок</th><th>Создан</th><th>Оплачен</th><th>Статус</th><th>Кто выставил</th></tr>
            </thead>
            <tbody>
                <tr v-for="i in invoices" :key="i.id">
                    <td>{{ i.user.name }}</td>
                    <td>{{ money(i.amount) }}</td>
                    <td>{{ i.months }} мес.</td>
                    <td>{{ i.created_at }}</td>
                    <td>{{ i.paid_at || '—' }}</td>
                    <td><span class="tag" :class="tagClass(i.status)">{{ label(i.status) }}</span></td>
                    <td>{{ i.created_by || '—' }}</td>
                </tr>
                <tr v-if="!loading && invoices.length === 0"><td colspan="7" class="muted">Счетов нет</td></tr>
            </tbody>
        </table>
        <Pager :page="meta.page" :last-page="meta.last_page" @change="load" />
    </section>
</template>

<script>
import { api, money } from '../api';
import ErrorText from '../components/ErrorText.vue';
import Pager from '../components/Pager.vue';

export default {
    name: 'InvoicesPage',
    components: { ErrorText, Pager },
    data() {
        return {
            invoices: [],
            meta: { page: 1, last_page: 1 },
            status: '',
            statuses: [
                { id: '', label: 'Все' },
                { id: 'pending', label: 'Ожидают оплаты' },
                { id: 'paid', label: 'Оплачены' },
                { id: 'canceled', label: 'Отменены' },
            ],
            loading: false,
            error: '',
        };
    },
    mounted() {
        this.load(1);
    },
    methods: {
        money,
        async load(page = 1) {
            this.loading = true;
            this.error = '';
            try {
                const params = new URLSearchParams({ page, status: this.status });
                const res = await api('GET', `/invoices?${params}`);
                this.invoices = res.data;
                this.meta = res.meta;
            } catch (e) {
                this.error = e.message;
            } finally {
                this.loading = false;
            }
        },
        setStatus(id) {
            this.status = id;
            this.load(1);
        },
        label(status) {
            return { pending: 'Ожидает оплаты', paid: 'Оплачен', canceled: 'Отменён' }[status] || status;
        },
        tagClass(status) {
            return { pending: 'tag-neutral', paid: 'tag-accent', canceled: 'tag-outline' }[status];
        },
    },
};
</script>
