<template>
    <Modal title="Выставить счёт" @close="$emit('close')">
        <p class="muted">{{ user.name }}<span v-if="user.email">, {{ user.email }}</span></p>

        <template v-if="!result">
            <div class="block">
                <div class="muted small">Стоимость</div>
                <div class="seg">
                    <button class="btn" :class="mode === 'default' ? 'btn-primary' : 'btn-secondary'" @click="mode = 'default'">По умолчанию</button>
                    <button class="btn" :class="mode === 'manual' ? 'btn-primary' : 'btn-secondary'" @click="mode = 'manual'">Вручную</button>
                </div>
                <p v-if="mode === 'default'" class="strong">{{ money(defaultPrice) }} в месяц</p>
                <input v-else v-model.number="manual" class="input" type="number" min="1" placeholder="Сумма, ₽">
            </div>

            <div class="block">
                <div class="muted small">Срок действия</div>
                <div class="chips">
                    <button v-for="m in monthOptions" :key="m" class="btn" :class="months === m ? 'btn-primary' : 'btn-secondary'" @click="months = m">
                        {{ monthLabel(m) }}
                    </button>
                </div>
            </div>

            <p class="muted small">Подписка продлевается автоматически. Пользователю придёт push со ссылкой на оплату.</p>
            <div class="total">
                <span class="muted">Итого</span>
                <span class="strong total-value">{{ total > 0 ? money(total) : '—' }}</span>
            </div>
            <ErrorText :message="error" />
            <div class="dialog-actions">
                <button class="btn btn-secondary" @click="$emit('close')">Отмена</button>
                <button class="btn btn-primary" :disabled="total <= 0 || busy" @click="issue">Выставить счёт</button>
            </div>
        </template>

        <template v-else>
            <div class="notice">
                Счёт на {{ money(result.amount) }} выставлен: {{ user.name }}, {{ monthLabel(result.months) }}.
                Ссылка на оплату отправлена пользователю.
            </div>
            <p v-if="result.confirmation_url" class="small">
                Ссылка: <a :href="result.confirmation_url" target="_blank" rel="noopener">открыть страницу оплаты</a>
            </p>
            <div class="dialog-actions">
                <button class="btn btn-primary" @click="finish">Готово</button>
            </div>
        </template>
    </Modal>
</template>

<script>
import { api, money } from '../api';
import Modal from './Modal.vue';
import ErrorText from './ErrorText.vue';

export default {
    name: 'InvoiceDialog',
    components: { Modal, ErrorText },
    props: {
        user: { type: Object, required: true },
    },
    emits: ['close', 'done'],
    data() {
        return {
            defaultPrice: 1490,
            mode: 'default',
            manual: null,
            months: 1,
            monthOptions: [1, 3, 6, 12],
            result: null,
            error: '',
            busy: false,
        };
    },
    computed: {
        total() {
            const price = this.mode === 'default' ? this.defaultPrice : Number(this.manual) || 0;
            return price * this.months;
        },
    },
    mounted() {
        api('GET', '/settings').then((res) => {
            this.defaultPrice = res.data.default_price;
        }).catch(() => {});
    },
    methods: {
        money,
        monthLabel(m) {
            if (m === 1) return '1 месяц';
            if (m >= 2 && m <= 4) return `${m} месяца`;
            return `${m} месяцев`;
        },
        async issue() {
            this.busy = true;
            this.error = '';
            try {
                const amount = this.mode === 'manual' ? Number(this.manual) : null;
                this.result = (await api('POST', `/users/${this.user.id}/invoices`, { amount, months: this.months })).data;
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
        finish() {
            this.$emit('done');
            this.$emit('close');
        },
    },
};
</script>
