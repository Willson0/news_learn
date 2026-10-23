<template>
    <section class="narrow">
        <header class="page-head">
            <div>
                <h1>Настройки</h1>
                <p class="muted">Изменения сохраняются сразу и действуют в приложении</p>
            </div>
        </header>
        <ErrorText :message="error" />
        <p v-if="saved" class="notice">Сохранено.</p>

        <form class="card form" @submit.prevent="saveMain">
            <h3>Подписка по умолчанию</h3>
            <div class="row">
                <label class="field"><span>Цена за период, ₽</span>
                    <input v-model.number="settings.default_price" class="input" type="number" min="1">
                </label>
                <label class="field"><span>Срок периода, мес.</span>
                    <input v-model.number="settings.default_months" class="input" type="number" min="1" max="12">
                </label>
            </div>

            <h3>Документы</h3>
            <p class="muted small">Открываются в приложении по ссылке (соглашение, политика и т. п.).</p>
            <div v-for="(doc, i) in settings.documents" :key="i" class="row doc-row">
                <input v-model="doc.title" class="input" placeholder="Название">
                <input v-model="doc.url" class="input" placeholder="https://…">
                <button type="button" class="btn btn-ghost" @click="settings.documents.splice(i, 1)">Удалить</button>
            </div>
            <button type="button" class="btn btn-secondary" @click="settings.documents.push({ title: '', url: '' })">Добавить документ</button>

            <h3>Ключи сервисов</h3>
            <label class="field"><span>API-ключ TradingView {{ settings.tradingview_api_key ? '(сохранён: ' + settings.tradingview_api_key + ')' : '' }}</span>
                <input v-model="tradingKey" class="input" autocomplete="off" placeholder="Оставьте пустым, чтобы не менять">
            </label>
            <label class="field"><span>API-ключ почты для кодов (Resend) {{ settings.email_api_key ? '(сохранён: ' + settings.email_api_key + ')' : '' }}</span>
                <input v-model="emailKey" class="input" autocomplete="off" placeholder="Оставьте пустым, чтобы не менять">
            </label>
            <p class="muted small">
                Коды восстановления и подтверждения уходят письмом через почтовый сервис, выбранный в MAIL_MAILER.
                Ключ отсюда подставляется в настройки сервиса.
            </p>
            <p class="muted small">Push: {{ settings.fcm_configured ? 'FCM подключён' : 'FCM не настроен (FCM_PROJECT_ID, FCM_CREDENTIALS)' }}</p>

            <button class="btn btn-primary" :disabled="busy">Сохранить настройки</button>
        </form>

        <form class="card form" @submit.prevent="saveAccount">
            <h3>Доступ в админку</h3>
            <p class="muted small">Вход в эту панель: логин и пароль. Для смены нужен текущий пароль.</p>
            <label class="field"><span>Логин</span>
                <input v-model="account.login" class="input" required autocomplete="username">
            </label>
            <div class="row">
                <label class="field"><span>Новый пароль (от 8 символов)</span>
                    <input v-model="account.password" class="input" type="password" autocomplete="new-password">
                </label>
                <label class="field"><span>Повторите пароль</span>
                    <input v-model="account.password_confirmation" class="input" type="password" autocomplete="new-password">
                </label>
            </div>
            <label class="field"><span>Текущий пароль</span>
                <input v-model="account.current_password" class="input" type="password" required autocomplete="current-password">
            </label>
            <p v-if="accountSaved" class="notice">Логин и пароль обновлены.</p>
            <button class="btn btn-primary" :disabled="busy">Сохранить доступ</button>
        </form>
    </section>
</template>

<script>
import { api, session } from '../api';
import ErrorText from '../components/ErrorText.vue';

export default {
    name: 'SettingsPage',
    components: { ErrorText },
    data() {
        return {
            settings: { default_price: 0, default_months: 1, documents: [], fcm_configured: false },
            tradingKey: '',
            emailKey: '',
            account: { login: session.login || '', password: '', password_confirmation: '', current_password: '' },
            saved: false,
            accountSaved: false,
            error: '',
            busy: false,
        };
    },
    mounted() {
        this.load();
    },
    methods: {
        async load() {
            try {
                this.settings = (await api('GET', '/settings')).data;
            } catch (e) {
                this.error = e.message;
            }
        },
        async saveMain() {
            this.busy = true;
            this.saved = false;
            this.error = '';
            try {
                const payload = {
                    default_price: this.settings.default_price,
                    default_months: this.settings.default_months,
                    documents: this.settings.documents,
                };
                if (this.tradingKey) payload.tradingview_api_key = this.tradingKey;
                if (this.emailKey) payload.email_api_key = this.emailKey;
                this.settings = (await api('PUT', '/settings', payload)).data;
                this.tradingKey = '';
                this.emailKey = '';
                this.saved = true;
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
        async saveAccount() {
            this.busy = true;
            this.accountSaved = false;
            this.error = '';
            try {
                const me = await api('PUT', '/account', this.account);
                session.login = me.login;
                this.account.password = '';
                this.account.password_confirmation = '';
                this.account.current_password = '';
                this.accountSaved = true;
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
    },
};
</script>
