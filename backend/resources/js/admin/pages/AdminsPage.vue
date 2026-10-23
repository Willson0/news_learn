<template>
    <section class="narrow">
        <header class="page-head">
            <div>
                <h1>Администраторы</h1>
                <p class="muted">Админы приложения назначаются по тегу пользователя (@ник из профиля). Главного админа удалить нельзя.</p>
            </div>
        </header>
        <form class="card form inline" @submit.prevent="add">
            <input v-model="tag" class="input" placeholder="Тег, например @ivan" autocapitalize="off" autocorrect="off" required>
            <input v-model="name" class="input" placeholder="Имя (необязательно)">
            <button class="btn btn-primary" :disabled="busy">Добавить</button>
        </form>
        <ErrorText :message="error" />
        <table class="table">
            <thead><tr><th>Имя</th><th>Тег</th><th>Роль</th><th /></tr></thead>
            <tbody>
                <tr v-for="a in admins" :key="a.telegram_id">
                    <td>{{ a.name || a.username || '—' }}</td>
                    <td>{{ a.username || '—' }}</td>
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
        return { admins: [], tag: '', name: '', error: '', busy: false };
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
                await api('POST', '/admins', { tag: this.tag.trim(), name: this.name || null });
                this.tag = '';
                this.name = '';
                await this.load();
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
        async remove(a) {
            if (!window.confirm(`Убрать права админа у ${a.username || a.name || a.telegram_id}?`)) return;
            try {
                await api('DELETE', `/admins/${a.telegram_id}`);
                await this.load();
            } catch (e) {
                this.error = e.message;
            }
        },
    },
};
</script>
