<template>
    <section>
        <header class="page-head">
            <div>
                <h1>Продажи</h1>
                <p class="muted">Оплаты подписки за выбранный период</p>
            </div>
            <div class="range">
                <input v-model="from" class="input" type="date" aria-label="С даты" @change="load(1)">
                <span class="muted">—</span>
                <input v-model="to" class="input" type="date" aria-label="По дату" @change="load(1)">
            </div>
        </header>
        <ErrorText :message="error" />

        <div class="summary card">
            <div>
                <div class="muted small">Итого</div>
                <div class="big">{{ money(summary.total) }}</div>
            </div>
            <div>
                <div class="muted small">Оплат</div>
                <div class="big">{{ summary.count }}</div>
            </div>
            <div>
                <div class="muted small">Средний чек</div>
                <div class="big">{{ money(summary.average) }}</div>
            </div>
        </div>

        <div class="bars wide-bars" v-if="series.length">
            <div v-for="s in series" :key="s.label" class="bar">
                <span class="small">{{ money(s.total) }}</span>
                <div class="bar-fill" :style="{ height: barHeight(s.total) + 'px' }" />
                <span class="muted small">{{ s.label }}</span>
            </div>
        </div>

        <table class="table">
            <thead>
                <tr><th>Дата</th><th>Пользователь</th><th>Тип</th><th>Срок</th><th>Сумма</th></tr>
            </thead>
            <tbody>
                <tr v-for="p in items" :key="p.id">
                    <td>{{ p.paid_at }}</td>
                    <td>{{ p.user }}</td>
                    <td>{{ p.kind }}</td>
                    <td>{{ p.months }} мес.</td>
                    <td>{{ money(p.amount) }}</td>
                </tr>
                <tr v-if="!loading && items.length === 0"><td colspan="5" class="muted">Оплат за период нет</td></tr>
            </tbody>
        </table>
        <Pager :page="meta.page" :last-page="meta.last_page" @change="load" />
    </section>
</template>

<script>
import { api, money } from '../api';
import ErrorText from '../components/ErrorText.vue';
import Pager from '../components/Pager.vue';

function isoDate(d) {
    return d.toISOString().slice(0, 10);
}

export default {
    name: 'SalesPage',
    components: { ErrorText, Pager },
    data() {
        const now = new Date();
        return {
            from: isoDate(new Date(now.getFullYear(), now.getMonth(), 1)),
            to: isoDate(now),
            summary: { total: 0, count: 0, average: 0 },
            series: [],
            items: [],
            meta: { page: 1, last_page: 1 },
            loading: false,
            error: '',
        };
    },
    computed: {
        maxSeries() {
            return Math.max(1, ...this.series.map((s) => s.total));
        },
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
                const params = new URLSearchParams({ from: this.from, to: this.to, page });
                const res = await api('GET', `/analytics/sales?${params}`);
                this.summary = res.summary;
                this.series = res.series;
                this.items = res.data;
                this.meta = res.meta;
            } catch (e) {
                this.error = e.message;
            } finally {
                this.loading = false;
            }
        },
        barHeight(total) {
            return Math.round((total / this.maxSeries) * 160);
        },
    },
};
</script>
