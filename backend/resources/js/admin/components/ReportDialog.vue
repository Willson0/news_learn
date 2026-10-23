<template>
    <Modal :title="report ? 'Изменить отчёт' : 'Новый отчёт'" @close="$emit('close')">
        <form class="form" @submit.prevent="save">
            <label class="field"><span>Название</span>
                <input v-model="form.title" class="input" required maxlength="255">
            </label>
            <label class="field"><span>Описание (до 250 символов)</span>
                <textarea v-model="form.description" class="input" rows="3" maxlength="250" required />
            </label>
            <label class="field"><span>Инструмент</span>
                <select v-model="form.instrument" class="input" required>
                    <option value="" disabled>Выберите инструмент</option>
                    <option v-for="i in instruments" :key="i.key" :value="i.key">{{ i.label }}</option>
                </select>
            </label>
            <label class="field"><span>Ссылка на график (необязательно)</span>
                <input v-model="form.chart_url" class="input" type="url">
            </label>
            <label class="field"><span>Обложка {{ report ? '(оставьте пустым, чтобы не менять)' : '' }}</span>
                <input class="input" type="file" accept="image/*" :required="!report" @change="pick('cover', $event)">
            </label>
            <label class="field"><span>HTML-отчёт (необязательно)</span>
                <input class="input" type="file" accept=".html,.htm" @change="pick('html', $event)">
            </label>
            <p v-if="report?.html_name" class="muted small">Сейчас: {{ report.html_name }}</p>
            <ErrorText :message="error" />
            <div class="dialog-actions">
                <button type="button" class="btn btn-secondary" @click="$emit('close')">Отмена</button>
                <button class="btn btn-primary" :disabled="busy">Сохранить</button>
            </div>
        </form>
    </Modal>
</template>

<script>
import { api } from '../api';
import Modal from './Modal.vue';
import ErrorText from './ErrorText.vue';

export default {
    name: 'ReportDialog',
    components: { Modal, ErrorText },
    props: {
        report: { type: Object, default: null },
        instruments: { type: Array, default: () => [] },
    },
    emits: ['close', 'saved'],
    data() {
        return {
            form: {
                title: this.report?.title || '',
                description: this.report?.description || '',
                instrument: this.report?.instrument?.key || '',
                chart_url: this.report?.chart_url || '',
            },
            files: {},
            error: '',
            busy: false,
        };
    },
    methods: {
        pick(field, event) {
            this.files[field] = event.target.files[0] || null;
        },
        async save() {
            this.busy = true;
            this.error = '';
            const body = new FormData();
            Object.entries(this.form).forEach(([k, v]) => body.append(k, v ?? ''));
            Object.entries(this.files).forEach(([k, file]) => {
                if (file) body.append(k, file);
            });
            const url = this.report ? `/reports/${this.report.id}` : '/reports';
            try {
                await api('POST', url, body);
                this.$emit('saved');
            } catch (e) {
                this.error = e.message;
            } finally {
                this.busy = false;
            }
        },
    },
};
</script>
