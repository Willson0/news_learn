<template>
    <aside class="drawer" role="dialog" aria-label="Профиль пользователя">
        <div class="drawer-scroll">
            <header class="drawer-head">
                <div>
                    <h2>{{ user?.name || '…' }}</h2>
                    <div class="muted">{{ user?.email || 'без почты' }}</div>
                </div>
                <button class="btn btn-ghost" aria-label="Закрыть" @click="$emit('close')">×</button>
            </header>

            <ErrorText :message="error" />
            <template v-if="user">
                <dl class="facts">
                    <dt>Телефон</dt><dd>{{ user.phone || '—' }}</dd>
                    <dt>Telegram ID</dt><dd>{{ user.telegram_id || '—' }}</dd>
                    <dt>Регистрация</dt><dd>{{ user.registered_at }}</dd>
                    <dt>Последний вход</dt><dd>{{ user.last_seen_at || '—' }}</dd>
                    <dt>Карта</dt><dd>{{ user.subscription.card || 'не привязана' }}</dd>
                    <dt>Отчётов</dt><dd>{{ user.reports }}</dd>
                </dl>

                <section class="block">
                    <h3>Условия подписки</h3>
                    <p class="muted small">
                        Сейчас: {{ money(user.subscription.amount) }} за {{ user.subscription.months }} мес.,
                        {{ user.subscription.active ? 'активна' : 'не активна' }}
                        <span v-if="user.subscription.until"> до {{ formatDate(user.subscription.until) }}</span>.
                    </p>
                    <form class="form" @submit.prevent="saveTerms">
                        <div class="row">
                            <label class="field"><span>Сумма, ₽</span>
                                <input v-model.number="terms.amount" class="input" type="number" min="1">
                            </label>
                            <label class="field"><span>Срок периода, мес.</span>
                                <input v-model.number="terms.months" class="input" type="number" min="1" max="12">
                            </label>
                        </div>
                        <label class="field"><span>Действует до</span>
                            <input v-model="terms.until" class="input" type="date">
                        </label>
                        <label class="check"><input v-model="terms.auto_pay" type="checkbox"> Автопродление</label>
                        <label class="check"><input v-model="terms.active" type="checkbox"> Доступ по подписке открыт</label>
                        <button class="btn btn-primary" :disabled="busy">Сохранить условия</button>
                        <p v-if="saved" class="ok small">Условия сохранены, пользователю отправлено уведомление.</p>
                    </form>
                </section>

                <section class="block">
                    <h3>Бесплатный доступ</h3>
                    <p class="muted small">Открывает подписку без счёта до выбранной даты. Автопродления не будет, после даты доступ закроется.</p>
                    <form class="form" @submit.prevent="grant">
                        <label class="field"><span>Действует до</span>
                            <input v-model="grantUntil" class="input" type="date" :min="tomorrow()" required>
                        </label>
                        <button class="btn btn-primary" :disabled="busy || !grantUntil">Выдать бесплатно</button>
                        <p v-if="granted" class="ok small">Доступ выдан до {{ formatDate(granted) }}, пользователю отправлено уведомление.</p>
                    </form>
                </section>

                <section class="block">
                    <h3>Последние счета</h3>
                    <p v-if="!user.invoices.length" class="muted small">Счетов пока нет</p>
                    <ul class="list">
                        <li v-for="i in user.invoices" :key="i.id">
                            <span>{{ money(i.amount) }} · {{ i.months }} мес.</span>
                            <span class="muted small">{{ i.date }} · {{ invoiceStatus(i.status) }}</span>
                        </li>
                    </ul>
                </section>

                <div class="drawer-foot">
                    <button class="btn btn-secondary" @click="$emit('close')">Закрыть</button>
                </div>
            </template>
        </div>
    </aside>
</template>

<script>
import { api, money } from '../api';
import ErrorText from './ErrorText.vue';

export default {
    name: 'UserDrawer',
    components: { ErrorText },
    props: {
        userId: { type: Number, required: true },
    },
    emits: ['close', 'changed', 'invoice'],
    data() {
        return { user: null, terms: {}, error: '', busy: false, saved: false, grantUntil: '', granted: '' };
    },
    watch: {
        userId: 'load',
    },
    mounted() {
        this.load();
    },
    methods: {
        money,
        async load() {
            this.error = '';
            this.saved = false;
            try {
                const res = await api('GET', `/users/${this.userId}`);
                this.user = res.data;
                const s = res.data.subscription;
                this.terms = {
                    amount: s.amount,
                    months: s.months,
                    until: s.until || '',
                    auto_pay: s.auto_pay,
                    active: s.active,
                };
            } catch (e) {
                this.error = e.message;
            }
        },
        async saveTerms() {
            this.busy = true;
            this.saved = false;
            this.error = '';
            try {
                const payload = { ...this.terms, until: this.terms.until || null };
                const res = await api('PUT', `/users/${this.userId}/subscription`, payload);
                this.user = res.data;
                this.saved = true;
                this.$emit('changed');
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
        tomorrow() {
            const d = new Date();
            d.setDate(d.getDate() + 1);
            return d.toISOString().slice(0, 10);
        },
        async grant() {
            this.busy = true;
            this.granted = '';
            this.error = '';
            try {
                const res = await api('POST', `/users/${this.userId}/subscription/grant`, { until: this.grantUntil });
                this.user = res.data;
                this.granted = this.grantUntil;
                this.grantUntil = '';
                this.$emit('changed');
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
        formatDate(iso) {
            const [y, m, d] = iso.split('-');
            return `${d}.${m}.${y}`;
        },
        invoiceStatus(status) {
            return { pending: 'ожидает оплаты', paid: 'оплачен', canceled: 'отменён' }[status] || status;
        },
    },
};
</script>
