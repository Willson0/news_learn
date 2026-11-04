<template>
    <Modal title="Выставить счёт" @close="$emit('close')">
        <p class="muted">{{ user.name }}<span v-if="user.email">, {{ user.email }}</span></p>

        <template v-if="!result">
            <div class="block">
                <div class="muted small">Срок подписки</div>
                <div class="chips">
                    <button v-for="m in monthOptions" :key="m" class="btn" :class="!customDays && months === m ? 'btn-primary' : 'btn-secondary'" @click="pickMonths(m)">
                        {{ monthLabel(m) }}
                    </button>
                </div>
                <label class="custom-days">
                    <span class="muted small">или свой срок, дней</span>
                    <input
                        v-model.number="days"
                        class="input"
                        :class="{ active: customDays }"
                        type="number"
                        min="1"
                        :max="maxDays"
                        step="1"
                        inputmode="numeric"
                        placeholder="Например, 45"
                    >
                </label>
                <p v-if="daysError" class="error small">{{ daysError }}</p>
            </div>

            <div class="block">
                <div class="muted small">Стоимость за {{ periodLabel }}, ₽</div>
                <input v-model.number="price" class="input" type="number" min="1" placeholder="Например, 1490" @keyup.enter="issue">
            </div>

            <p class="muted small">
                Пользователь оплачивает {{ valid ? money(Number(price)) : 'эту сумму' }} за {{ periodLabel }},
                а по окончании срока подписка автоматически продлевается ещё на {{ periodLabel }} за ту же сумму.
                В приложении появится кнопка оплаты и придёт уведомление.
            </p>
            <ErrorText :message="error" />
            <div class="dialog-actions">
                <button class="btn btn-secondary" @click="$emit('close')">Отмена</button>
                <button class="btn btn-primary" :disabled="!valid || busy" @click="issue">Выставить счёт</button>
            </div>
        </template>

        <template v-else>
            <div class="notice">
                Цена подписки для {{ user.name }}: {{ money(result.amount) }} за {{ result.period || monthLabel(result.months) }}.
                Пользователь может оплатить её в приложении.
            </div>
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

/**
 * «Выставить счёт»: админ выбирает срок подписки (1/3/6/12 месяцев или свой срок в днях) и вписывает её цену за этот срок. Без неё оплатить подписку
 * в приложении нельзя (кнопки нет); после — кнопка появляется, пользователь платит сам.
 */
export default {
    name: 'InvoiceDialog',
    components: { Modal, ErrorText },
    props: {
        user: { type: Object, required: true },
    },
    emits: ['close', 'done'],
    data() {
        return {
            price: null,
            months: 1,
            monthOptions: [1, 3, 6, 12],
            // Свой срок в днях; если заполнен, он главнее выбранных месяцев.
            days: null,
            maxDays: 730,
            result: null,
            error: '',
            busy: false,
        };
    },
    computed: {
        customDays() {
            return this.days !== null && this.days !== '';
        },
        daysValid() {
            const d = Number(this.days);
            return Number.isInteger(d) && d >= 1 && d <= this.maxDays;
        },
        daysError() {
            if (!this.customDays || this.daysValid) return '';
            return `Срок — целое число дней от 1 до ${this.maxDays}`;
        },
        periodLabel() {
            return this.customDays && this.daysValid ? this.dayLabel(Number(this.days)) : this.monthLabel(this.months);
        },
        valid() {
            const p = Number(this.price);
            return Number.isInteger(p) && p > 0 && (!this.customDays || this.daysValid);
        },
    },
    methods: {
        money,
        pickMonths(m) {
            this.months = m;
            this.days = null;
        },
        dayLabel(d) {
            const n10 = d % 10;
            const n100 = d % 100;
            if (n100 >= 11 && n100 <= 14) return `${d} дней`;
            if (n10 === 1) return `${d} день`;
            if (n10 >= 2 && n10 <= 4) return `${d} дня`;
            return `${d} дней`;
        },
        monthLabel(m) {
            if (m === 1) return '1 месяц';
            if (m >= 2 && m <= 4) return `${m} месяца`;
            return `${m} месяцев`;
        },
        async issue() {
            if (!this.valid || this.busy) return;
            this.busy = true;
            this.error = '';
            try {
                this.result = (await api('POST', `/users/${this.user.id}/invoices`, this.customDays
                    ? { amount: Number(this.price), days: Number(this.days) }
                    : { amount: Number(this.price), months: this.months })).data;
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

<style scoped>
.custom-days {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 12px;
}
.custom-days .input {
    max-width: 220px;
}
.custom-days .input.active {
    border-color: var(--color-accent);
    box-shadow: inset 0 0 0 1px var(--color-accent);
}
</style>
