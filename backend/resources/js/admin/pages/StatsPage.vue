<template>
    <section>
        <header class="page-head">
            <div>
                <h1>Статистика</h1>
                <p class="muted">Пользователей всего: {{ stats?.users_total ?? '—' }}</p>
            </div>
        </header>
        <ErrorText :message="error" />

        <div class="grid-2" v-if="stats">
            <div class="card">
                <h3>Регистрации по неделям</h3>
                <div class="bars">
                    <div v-for="b in stats.registrations" :key="b.label" class="bar">
                        <span class="small">{{ b.value }}</span>
                        <div class="bar-fill" :style="{ height: barHeight(b.value, maxReg) + 'px' }" />
                        <span class="muted small">{{ b.label }}</span>
                    </div>
                </div>
            </div>

            <div class="card">
                <h3>Пользователи по подпискам</h3>
                <div class="split">
                    <div
                        v-for="s in stats.subscriptions"
                        :key="s.key"
                        class="split-part"
                        :class="'split-' + s.key"
                        :style="{ width: share(s.count) + '%' }"
                    />
                </div>
                <ul class="legend">
                    <li v-for="s in stats.subscriptions" :key="s.key">
                        <span class="swatch" :class="'split-' + s.key" />{{ s.label }}: {{ s.count }}
                    </li>
                </ul>
            </div>

            <div class="card wide">
                <h3>Оплаты по месяцам</h3>
                <div class="bars">
                    <div v-for="b in stats.revenue" :key="b.label" class="bar">
                        <span class="small">{{ money(b.value) }}</span>
                        <div class="bar-fill bar-fill-alt" :style="{ height: barHeight(b.value, maxRev) + 'px' }" />
                        <span class="muted small">{{ b.label }}</span>
                    </div>
                </div>
            </div>
        </div>
    </section>
</template>

<script>
import { api, money } from '../api';
import ErrorText from '../components/ErrorText.vue';

export default {
    name: 'StatsPage',
    components: { ErrorText },
    data() {
        return { stats: null, error: '' };
    },
    computed: {
        maxReg() {
            return Math.max(1, ...this.stats.registrations.map((b) => b.value));
        },
        maxRev() {
            return Math.max(1, ...this.stats.revenue.map((b) => b.value));
        },
    },
    mounted() {
        api('GET', '/analytics/stats').then((res) => {
            this.stats = res;
        }).catch((e) => {
            this.error = e.message;
        });
    },
    methods: {
        money,
        barHeight(value, max) {
            return Math.round((value / max) * 160);
        },
        share(count) {
            const total = this.stats.subscriptions.reduce((sum, s) => sum + s.count, 0);
            return total ? Math.round((count / total) * 100) : 0;
        },
    },
};
</script>
