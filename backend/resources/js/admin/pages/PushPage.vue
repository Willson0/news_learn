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
            <p v-if="result" class="notice">Отправлено: {{ result.sent }} из {{ result.devices }} устройств.</p>
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
        };
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
