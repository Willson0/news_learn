<template>
    <section class="narrow">
        <header class="page-head">
            <div>
                <h1>Рассылка</h1>
                <p class="muted">Push-уведомление на устройства пользователей приложения</p>
            </div>
        </header>
        <form class="card form" @submit.prevent="send">
            <label class="field"><span>Заголовок</span>
                <input v-model="form.title" class="input" maxlength="120" required>
            </label>
            <label class="field"><span>Текст</span>
                <textarea v-model="form.body" class="input" rows="4" maxlength="500" required />
            </label>
            <label class="field"><span>Кому</span>
                <select v-model="form.audience" class="input">
                    <option value="all">Всем</option>
                    <option value="subscribed">С активной подпиской</option>
                    <option value="free">Без подписки</option>
                </select>
            </label>
            <ErrorText :message="error" />
            <p v-if="!configured" class="warning">
                Push не настроен: не загружен ключ Firebase. Его можно загрузить в
                <router-link to="/settings">Настройках</router-link> (раздел «Push-уведомления»).
            </p>
            <template v-if="result">
                <p class="notice">
                    Отправлено: {{ result.sent }} из {{ result.devices }} устройств
                    <span v-if="result.android || result.ios">
                        (Android: {{ result.android.sent }} из {{ result.android.devices }},
                        iPhone: {{ result.ios.sent }} из {{ result.ios.devices }})</span>.
                </p>
                <p v-for="w in result.warnings || []" :key="w" class="warning">{{ w }}</p>
                <p v-for="e in result.errors || []" :key="e" class="error small">{{ e }}</p>
            </template>
            <button class="btn btn-primary" :disabled="busy">Отправить рассылку</button>
        </form>
    </section>
</template>

<script>
import { api } from '../api';
import ErrorText from '../components/ErrorText.vue';

export default {
    name: 'PushPage',
    components: { ErrorText },
    data() {
        return {
            form: { title: '', body: '', audience: 'all' },
            result: null,
            error: '',
            busy: false,
            configured: true,
        };
    },
    async mounted() {
        try {
            this.configured = (await api('GET', '/settings')).data.fcm_configured;
        } catch {
            // Не критично: предупреждение просто не покажем заранее.
        }
    },
    methods: {
        async send() {
            if (!window.confirm('Отправить рассылку этой аудитории?')) return;
            this.busy = true;
            this.error = '';
            this.result = null;
            try {
                this.result = await api('POST', '/push', this.form);
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
    },
};
</script>
