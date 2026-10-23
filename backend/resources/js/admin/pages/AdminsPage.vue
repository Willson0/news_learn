<template>
    <section class="narrow">
        <header class="page-head">
            <div>
                <h1>Администраторы</h1>
                <p class="muted">Админы приложения назначаются по почте пользователя. Главного админа удалить нельзя.</p>
            </div>
        </header>
        <form class="card form inline" @submit.prevent="add">
            <input v-model="email" class="input" type="email" placeholder="Почта пользователя" autocapitalize="off" autocorrect="off" required>
            <input v-model="name" class="input" placeholder="Имя (необязательно)">
            <button class="btn btn-primary" :disabled="busy">Добавить</button>
        </form>
        <ErrorText :message="error" />
        <table class="table">
            <thead><tr><th>Имя</th><th>Почта</th><th>Роль</th><th /></tr></thead>
            <tbody>
                <tr v-for="a in admins" :key="a.id ?? a.telegram_id">
                    <td>{{ a.name || a.username || '—' }}</td>
                    <td>{{ a.email || '—' }}</td>
                    <td><span class="tag" :class="a.root ? 'tag-accent' : 'tag-neutral'">{{ a.root ? 'Главный' : 'Админ' }}</span></td>
                    <td class="actions">
                        <button v-if="!a.root" class="btn btn-ghost" @click="remove(a)">Удалить</button>
                    </td>
                </tr>
            </tbody>
        </table>
    </section>
</template>

<script>
import { api } from '../api';
import ErrorText from '../components/ErrorText.vue';

export default {
    name: 'AdminsPage',
    components: { ErrorText },
    data() {
        return { admins: [], email: '', name: '', error: '', busy: false };
    },
    mounted() {
        this.load();
    },
    methods: {
        async load() {
            try {
                this.admins = (await api('GET', '/admins')).data;
            } catch (e) {
                this.error = e.message;
            }
        },
        async add() {
            this.busy = true;
            this.error = '';
            try {
                await api('POST', '/admins', { email: this.email.trim(), name: this.name || null });
                this.email = '';
                this.name = '';
                await this.load();
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
        async remove(a) {
            if (!window.confirm(`Убрать права админа у ${a.email || a.name || a.telegram_id}?`)) return;
            try {
                await api('DELETE', `/admins/${a.id}`);
                await this.load();
            } catch (e) {
                this.error = e.message;
            }
        },
    },
};
</script>
