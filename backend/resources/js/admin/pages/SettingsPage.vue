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
            <button class="btn btn-primary" :disabled="busy">Сохранить настройки</button>
        </form>

        <form class="card form" @submit.prevent="saveMail">
            <h3>Почта для кодов</h3>
            <p class="muted small">
                Коды подтверждения регистрации и восстановления пароля уходят письмом.
                Сейчас действует: {{ mail.active || '—' }}.
            </p>
            <label class="field"><span>Способ отправки</span>
                <select v-model="mail.mailer" class="input">
                    <option :value="null">Как в файле .env (MAIL_MAILER)</option>
                    <option value="smtp">SMTP-сервер (Яндекс, Mail.ru, Gmail, свой)</option>
                    <option value="resend">Resend (по API-ключу)</option>
                    <option value="log">Не отправлять (коды только в лог сервера)</option>
                </select>
            </label>

            <template v-if="mail.mailer === 'smtp'">
                <div class="row">
                    <label class="field"><span>SMTP-сервер</span>
                        <input v-model.trim="mail.host" class="input" placeholder="smtp.yandex.ru" autocomplete="off">
                    </label>
                    <label class="field"><span>Порт</span>
                        <input v-model.number="mail.port" class="input" type="number" min="1" max="65535" placeholder="465">
                    </label>
                    <label class="field"><span>Шифрование</span>
                        <select v-model="mail.encryption" class="input">
                            <option value="ssl">SSL (обычно порт 465)</option>
                            <option value="tls">STARTTLS (обычно порт 587)</option>
                            <option value="none">Без шифрования</option>
                        </select>
                    </label>
                </div>
                <div class="row">
                    <label class="field"><span>Логин</span>
                        <input v-model.trim="mail.username" class="input" autocomplete="off" placeholder="robot@example.ru">
                    </label>
                    <label class="field"><span>Пароль {{ mail.password_set ? '(сохранён)' : '' }}</span>
                        <input v-model="mailPassword" class="input" type="password" autocomplete="new-password"
                               :placeholder="mail.password_set ? 'Оставьте пустым, чтобы не менять' : 'Пароль приложения от почты'">
                    </label>
                </div>
                <p class="muted small">
                    Для Яндекса и Mail.ru нужен «пароль приложения» из настроек безопасности почты, обычный пароль не подойдёт.
                </p>
            </template>

            <label v-if="mail.mailer === 'resend' || mail.mailer === null" class="field">
                <span>API-ключ Resend {{ settings.email_api_key ? '(сохранён: ' + settings.email_api_key + ')' : '' }}</span>
                <input v-model="emailKey" class="input" autocomplete="off" placeholder="Оставьте пустым, чтобы не менять">
            </label>

            <div v-if="mail.mailer === 'smtp' || mail.mailer === 'resend'" class="row">
                <label class="field"><span>Адрес отправителя</span>
                    <input v-model.trim="mail.from_address" class="input" type="email" placeholder="no-reply@example.ru">
                </label>
                <label class="field"><span>Имя отправителя</span>
                    <input v-model.trim="mail.from_name" class="input" placeholder="Карта ликвидности">
                </label>
            </div>
            <p v-if="mail.mailer === 'resend'" class="muted small">
                Адрес отправителя должен быть на домене, подтверждённом в Resend.
            </p>

            <button class="btn btn-primary" :disabled="busy">Сохранить почту</button>

            <div class="row test-row">
                <input v-model.trim="testEmail" class="input" type="email" placeholder="Куда отправить тестовое письмо">
                <button type="button" class="btn btn-secondary" :disabled="busy || !testEmail" @click="testMail">Отправить тест</button>
            </div>
            <p v-if="mailTest" :class="mailTest.ok ? 'notice' : 'error'">{{ mailTest.message }}</p>
        </form>

        <form class="card form" @submit.prevent="saveFcm">
            <h3>Push-уведомления</h3>
            <p class="muted small">
                Уведомления на Android и iPhone отправляются через Firebase Cloud Messaging.
                <template v-if="settings.fcm_configured">
                    Подключено: проект {{ settings.fcm_project_id }}
                    ({{ settings.fcm_source === 'admin' ? 'ключ загружен здесь' : 'ключ из .env' }}).
                </template>
                <template v-else>Сейчас не настроено — уведомления не уходят.</template>
            </p>
            <label class="field"><span>Ключ сервисного аккаунта Firebase (.json)</span>
                <input ref="fcmFile" class="input" type="file" accept=".json,application/json" @change="pickFcm">
            </label>
            <p class="muted small">
                Firebase → Настройки проекта → Сервисные аккаунты → «Создать закрытый ключ».
                Для iPhone в Firebase (Cloud Messaging → Apple) дополнительно загружается APNs-ключ Apple.
            </p>
            <div class="row">
                <button class="btn btn-primary" :disabled="busy || !fcmJson">Загрузить ключ</button>
                <button v-if="settings.fcm_source === 'admin'" type="button" class="btn btn-ghost" :disabled="busy" @click="clearFcm">Удалить ключ</button>
            </div>
            <button class="btn btn-primary" :disabled="busy">Сохранить настройки</button>
        </form>

        <form class="card form" @submit.prevent="saveAccount">
            <h3>Доступ в админку</h3>
            <p class="muted small">Вход в эту панель: логин и пароль. Для смены нужен текущий пароль.</p>
            <label class="field"><span>Логин</span>
                <input v-model="account.login" class="input" required autocomplete="username">
            </label>
            <div class="row">
                <label class="field"><span>Новый пароль (от 10 символов, буквы и цифры)</span>
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
            mail: { mailer: null, host: '', port: null, encryption: 'ssl', username: '', from_address: '', from_name: '', password_set: false, active: '' },
            tradingKey: '',
            emailKey: '',
            mailPassword: '',
            testEmail: '',
            mailTest: null,
            fcmJson: '',
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
        apply(data) {
            this.settings = data;
            if (data.mail) this.mail = { ...data.mail, encryption: data.mail.encryption || 'ssl' };
        },
        async load() {
            try {
                this.apply((await api('GET', '/settings')).data);
            } catch (e) {
                this.error = e.message;
            }
        },
        async run(payload, after) {
            this.busy = true;
            this.saved = false;
            this.error = '';
            try {
                this.apply((await api('PUT', '/settings', payload)).data);
                if (after) after();
                this.saved = true;
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
        saveMail() {
            const mail = {
                mailer: this.mail.mailer,
                host: this.mail.host || null,
                port: this.mail.port || null,
                encryption: this.mail.encryption,
                username: this.mail.username || null,
                from_address: this.mail.from_address || null,
                from_name: this.mail.from_name || null,
            };
            if (this.mailPassword) mail.password = this.mailPassword;
            const payload = { mail };
            if (this.emailKey) payload.email_api_key = this.emailKey;
            this.mailTest = null;
            return this.run(payload, () => {
                this.mailPassword = '';
                this.emailKey = '';
            });
        },
        async testMail() {
            this.busy = true;
            this.mailTest = null;
            try {
                this.mailTest = await api('POST', '/settings/mail/test', { email: this.testEmail });
            } catch (e) {
                this.mailTest = { ok: false, message: e.message };
            } finally {
                this.busy = false;
            }
        },
        pickFcm(event) {
            const file = event.target.files && event.target.files[0];
            this.fcmJson = '';
            if (!file) return;
            const reader = new FileReader();
            reader.onload = () => { this.fcmJson = String(reader.result || ''); };
            reader.readAsText(file);
        },
        saveFcm() {
            return this.run({ fcm_credentials: this.fcmJson }, () => {
                this.fcmJson = '';
                if (this.$refs.fcmFile) this.$refs.fcmFile.value = '';
            });
        },
        clearFcm() {
            if (!window.confirm('Удалить ключ Firebase? Push-уведомления перестанут отправляться.')) return;
            this.run({ fcm_clear: true });
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
                this.apply((await api('PUT', '/settings', payload)).data);
                this.tradingKey = '';
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
