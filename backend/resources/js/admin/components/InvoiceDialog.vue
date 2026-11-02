<template>
    <Modal title="Выставить счёт" @close="$emit('close')">
        <p class="muted">{{ user.name }}<span v-if="user.email">, {{ user.email }}</span></p>

        <template v-if="!result">
            <div class="block">
                <div class="muted small">Цена подписки, ₽</div>
                <input v-model.number="price" class="input" type="number" min="1" placeholder="Например, 1490" @keyup.enter="issue">
            </div>

            <p class="muted small">
                После этого у пользователя в приложении появится кнопка оплаты и придёт уведомление.
                Подписка продлевается автоматически по этой цене.
            </p>
            <ErrorText :message="error" />
            <div class="dialog-actions">
                <button class="btn btn-secondary" @click="$emit('close')">Отмена</button>
                <button class="btn btn-primary" :disabled="!valid || busy" @click="issue">Выставить счёт</button>
            </div>
        </template>

        <template v-else>
            <div class="notice">
                Цена подписки для {{ user.name }}: {{ money(result.amount) }} за {{ monthLabel(result.months) }}.
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
 * «Выставить счёт»: админ вписывает цену подписки пользователя. Без неё оплатить подписку
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
            result: null,
            error: '',
            busy: false,
        };
    },
    computed: {
        valid() {
            const p = Number(this.price);
            return Number.isInteger(p) && p > 0;
        },
    },
    methods: {
        money,
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
                this.result = (await api('POST', `/users/${this.user.id}/invoices`, { amount: Number(this.price) })).data;
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
