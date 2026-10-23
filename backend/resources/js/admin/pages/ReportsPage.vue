<template>
    <section>
        <header class="page-head">
            <div>
                <h1>Отчёты</h1>
                <p class="muted">Черновики видны только здесь, опубликованные — пользователям</p>
            </div>
            <button class="btn btn-primary" @click="openForm(null)">Новый отчёт</button>
        </header>
        <ErrorText :message="error" />

        <table class="table">
            <thead>
                <tr><th>Отчёт</th><th>Инструмент</th><th>Статус</th><th>Обновлён</th><th /></tr>
            </thead>
            <tbody>
                <tr v-for="r in reports" :key="r.id">
                    <td>
                        <div class="strong">{{ r.title }}</div>
                        <div class="muted small">{{ r.description }}</div>
                    </td>
                    <td>{{ r.instrument?.label || '—' }}</td>
                    <td>
                        <button class="tag tag-button" :class="r.status === 'draft' ? 'tag-neutral' : 'tag-accent'" :disabled="busyId === r.id" @click="toggleStatus(r)">
                            {{ r.status === 'draft' ? 'Черновик' : 'Опубликован' }}
                        </button>
                    </td>
                    <td>{{ r.updated_at }}</td>
                    <td class="actions">
                        <button class="btn btn-secondary" @click="openForm(r)">Изменить</button>
                        <button class="btn btn-ghost" @click="remove(r)">Удалить</button>
                    </td>
                </tr>
                <tr v-if="!loading && reports.length === 0"><td colspan="5" class="muted">Отчётов пока нет</td></tr>
            </tbody>
        </table>

        <ReportDialog v-if="form" :report="form.report" :instruments="instruments" @close="form = null" @saved="onSaved" />
    </section>
</template>

<script>
import { api } from '../api';
import ErrorText from '../components/ErrorText.vue';
import ReportDialog from '../components/ReportDialog.vue';

export default {
    name: 'ReportsPage',
    components: { ErrorText, ReportDialog },
    data() {
        return {
            reports: [],
            instruments: [],
            loading: false,
            busyId: null,
            form: null,
            error: '',
        };
    },
    mounted() {
        this.load();
        api('GET', '/instruments').then((res) => {
            this.instruments = res.data || res;
        }).catch(() => {});
    },
    methods: {
        async load() {
            this.loading = true;
            try {
                this.reports = (await api('GET', '/reports')).data;
            } catch (e) {
                this.error = e.message;
            } finally {
                this.loading = false;
            }
        },
        openForm(report) {
            this.form = { report };
        },
        onSaved() {
            this.form = null;
            this.load();
        },
        async toggleStatus(r) {
            this.busyId = r.id;
            try {
                await api('POST', `/reports/${r.id}/status`, { status: r.status === 'draft' ? 'actual' : 'draft' });
                await this.load();
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busyId = null;
            }
        },
        async remove(r) {
            if (!window.confirm(`Удалить отчёт «${r.title}» у всех пользователей вместе с чатом?`)) return;
            try {
                await api('DELETE', `/reports/${r.id}`);
                await this.load();
            } catch (e) {
                this.error = e.message;
            }
        },
    },
};
</script>
