<template>
    <div class="login">
        <form class="card login-card" @submit.prevent="submit">
            <h1>Вход в админку</h1>
            <label class="field">
                <span>Логин</span>
                <input v-model="login" class="input" autocomplete="username" required>
            </label>
            <label class="field">
                <span>Пароль</span>
                <input v-model="password" class="input" type="password" autocomplete="current-password" required>
            </label>
            <ErrorText :message="error" />
            <button class="btn btn-primary btn-block" :disabled="busy">Войти</button>
        </form>
    </div>
</template>

<script>
import { api, session } from '../api';
import ErrorText from '../components/ErrorText.vue';

export default {
    name: 'LoginPage',
    components: { ErrorText },
    data() {
        return { login: '', password: '', error: '', busy: false };
    },
    methods: {
        async submit() {
            this.busy = true;
            this.error = '';
            try {
                const me = await api('POST', '/login', { login: this.login, password: this.password });
                session.login = me.login;
                session.checked = true;
                this.$router.push({ name: 'users' });
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
    },
};
</script>
