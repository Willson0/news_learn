<script>
import AppButton from '@/components/ui/AppButton.vue'
import ConfirmDialog from '@/components/admin/ConfirmDialog.vue'
import IconTrashX from '@/components/icons/IconTrashX.vue'
import IconPlus from '@/components/icons/IconPlus.vue'
import IconFileHtml from '@/components/icons/IconFileHtml.vue'
import {
  fetchInstruments,
  fetchReport,
  createReport,
  updateReport,
  deleteReport,
} from '@/api/resources'
import { fileUrl } from '@/api/client'
import { haptic } from '@/telegram/webapp'

const DESCRIPTION_MAX = 250

// Короткие подписи чипов, как в макете.
const SHORT_LABELS = { gas: 'Нат. газ' }

export default {
  name: 'ReportFormView',
  components: { AppButton, ConfirmDialog, IconTrashX, IconPlus, IconFileHtml },
  data() {
    return {
      title: '',
      description: '',
      chartUrl: '',
      instrument: null,
      instruments: [],
      // Файлы: новый выбранный File или уже загруженный на сервер (имя + ссылка).
      html: null,
      htmlName: '',
      cover: null,
      coverName: '',
      coverPreview: null,
      removeHtml: false,
      removeCover: false,
      errors: {},
      saving: false,
      confirmOpen: false,
      deleting: false,
      descriptionMax: DESCRIPTION_MAX,
    }
  },
  computed: {
    isEdit() {
      return this.$route.name === 'report-edit'
    },
    reportId() {
      return this.$route.params.id
    },
    backTo() {
      return this.isEdit
        ? { name: 'report-detail', params: { id: this.reportId } }
        : { name: 'analytics' }
    },
  },
  watch: {
    description(value) {
      if (value.length > DESCRIPTION_MAX) this.description = value.slice(0, DESCRIPTION_MAX)
    },
  },
  mounted() {
    this.load()
  },
  beforeUnmount() {
    this.revokePreview()
  },
  methods: {
    async load() {
      try {
        const list = await fetchInstruments()
        this.instruments = list.map((i) => ({ key: i.key, label: SHORT_LABELS[i.key] || i.label }))
      } catch {
        this.instruments = []
      }
      if (!this.isEdit) return
      try {
        const r = await fetchReport(this.reportId)
        this.title = r.title || ''
        this.description = r.description || ''
        this.chartUrl = r.chart_url || ''
        this.instrument = r.material || null
        this.htmlName = r.html_name || ''
        this.coverName = r.cover_name || ''
        this.coverPreview = fileUrl(r.cover_url)
      } catch {
        this.$router.replace({ name: 'analytics' })
      }
    },
    pickInstrument(key) {
      this.instrument = this.instrument === key ? null : key
      this.clearError('instrument')
      haptic()
    },
    clearError(key) {
      if (this.errors[key]) this.errors = { ...this.errors, [key]: null }
    },
    chooseFile(kind) {
      this.$refs[kind === 'html' ? 'htmlInput' : 'coverInput'].click()
    },
    onHtml(e) {
      const file = e.target.files && e.target.files[0]
      e.target.value = ''
      if (!file) return
      this.html = file
      this.htmlName = file.name
      this.removeHtml = false
      this.clearError('html')
    },
    onCover(e) {
      const file = e.target.files && e.target.files[0]
      e.target.value = ''
      if (!file) return
      this.revokePreview()
      this.cover = file
      this.coverName = file.name
      this.coverPreview = URL.createObjectURL(file)
      this.removeCover = false
      this.clearError('cover')
    },
    dropHtml() {
      this.html = null
      this.htmlName = ''
      this.removeHtml = this.isEdit
    },
    dropCover() {
      this.revokePreview()
      this.cover = null
      this.coverName = ''
      this.coverPreview = null
      this.removeCover = this.isEdit
    },
    revokePreview() {
      if (this.coverPreview && this.coverPreview.startsWith('blob:')) {
        URL.revokeObjectURL(this.coverPreview)
      }
    },
    validate() {
      const errors = {}
      if (!this.title.trim()) errors.title = 'Напишите название'
      if (!this.description.trim()) errors.description = 'Напишите описание'
      if (!this.instrument) errors.instrument = 'Выберите инструмент'
      if (!this.coverName) errors.cover = 'Загрузите обложку'
      this.errors = errors
      return Object.keys(errors).length === 0
    },
    async submit() {
      if (!this.validate()) {
        haptic('heavy')
        return
      }
      const payload = {
        title: this.title.trim(),
        description: this.description.trim(),
        chartUrl: this.chartUrl.trim(),
        instrument: this.instrument,
        html: this.html,
        cover: this.cover,
        removeHtml: this.removeHtml,
        removeCover: this.removeCover,
      }
      this.saving = true
      try {
        const report = this.isEdit
          ? await updateReport(this.reportId, payload)
          : await createReport(payload)
        haptic('medium')
        this.$router.replace({ name: 'report-detail', params: { id: report.id } })
      } catch (err) {
        const fieldErrors = {}
        Object.entries((err && err.errors) || {}).forEach(([key, messages]) => {
          fieldErrors[key] = Array.isArray(messages) ? messages[0] : messages
        })
        if (!Object.keys(fieldErrors).length) fieldErrors.form = (err && err.message) || 'Не удалось сохранить'
        this.errors = fieldErrors
        haptic('heavy')
      } finally {
        this.saving = false
      }
    },
    async remove() {
      this.deleting = true
      try {
        await deleteReport(this.reportId)
        haptic('medium')
        this.$router.replace({ name: 'analytics' })
      } catch (err) {
        this.errors = { form: (err && err.message) || 'Не удалось удалить' }
        this.confirmOpen = false
      } finally {
        this.deleting = false
      }
    },
  },
}
</script>

<template>
  <section class="rform">
    <div class="rform__top">
      <button
        v-if="isEdit"
        type="button"
        class="rform__trash"
        aria-label="Удалить отчёт"
        @click="confirmOpen = true"
      >
        <IconTrashX />
      </button>
    </div>

    <h1 class="rform__title screen-title">{{ isEdit ? 'Редактирование отчета' : 'Создание отчета' }}</h1>

    <div class="rform__group">
      <label class="rfield" :class="{ 'rfield--error': errors.title }">
        <span class="rfield__label">Заголовок</span>
        <input
          v-model="title"
          class="rfield__input"
          type="text"
          placeholder="Введите текст"
          @input="clearError('title')"
        />
      </label>
      <p v-if="errors.title" class="rform__error">{{ errors.title }}</p>
    </div>

    <div class="rform__group">
      <label class="rfield rfield--area" :class="{ 'rfield--error': errors.description }">
        <span class="rfield__label">
          Описание
          <span class="rfield__counter">{{ description.length }}/{{ descriptionMax }} символов</span>
        </span>
        <textarea
          v-model="description"
          class="rfield__input rfield__textarea"
          rows="3"
          placeholder="Введите текст"
          :maxlength="descriptionMax"
          @input="clearError('description')"
        ></textarea>
      </label>
      <p v-if="errors.description" class="rform__error">{{ errors.description }}</p>
    </div>

    <div class="rform__group">
      <label class="rfield" :class="{ 'rfield--error': errors.chart_url }">
        <span class="rfield__label">График</span>
        <input
          v-model="chartUrl"
          class="rfield__input"
          type="url"
          inputmode="url"
          placeholder="Ссылка на график"
          @input="clearError('chart_url')"
        />
      </label>
      <p v-if="errors.chart_url" class="rform__error">{{ errors.chart_url }}</p>
    </div>

    <div class="rform__group">
      <div class="rform__chips">
        <button
          v-for="item in instruments"
          :key="item.key"
          type="button"
          class="rchip"
          :class="{ 'rchip--on': instrument === item.key }"
          :aria-pressed="String(instrument === item.key)"
          @click="pickInstrument(item.key)"
        >
          {{ item.label }}
        </button>
      </div>
      <p v-if="errors.instrument" class="rform__error">{{ errors.instrument }}</p>
    </div>

    <div class="rform__group">
      <div class="rfile">
        <span v-if="htmlName" class="rfile__icon rfile__icon--html"><IconFileHtml /></span>
        <span v-else class="rfile__icon rfile__icon--add"><IconPlus /></span>
        <span class="rfile__name">{{ htmlName || 'Отчет html' }}</span>
        <button v-if="htmlName" type="button" class="rfile__btn" @click="dropHtml">Удалить</button>
        <button v-else type="button" class="rfile__btn" @click="chooseFile('html')">Добавить</button>
      </div>
      <p v-if="errors.html" class="rform__error">{{ errors.html }}</p>
      <input ref="htmlInput" class="rform__hidden" type="file" accept=".html,.htm,text/html" @change="onHtml" />
    </div>

    <div class="rform__group">
      <div class="rfile">
        <span v-if="coverName" class="rfile__icon rfile__icon--image">
          <img v-if="coverPreview" :src="coverPreview" alt="" />
        </span>
        <span v-else class="rfile__icon rfile__icon--add"><IconPlus /></span>
        <span class="rfile__name">{{ coverName || 'Обложка' }}</span>
        <button v-if="coverName" type="button" class="rfile__btn" @click="dropCover">Удалить</button>
        <button v-else type="button" class="rfile__btn" @click="chooseFile('cover')">Добавить</button>
      </div>
      <p v-if="errors.cover" class="rform__error">{{ errors.cover }}</p>
      <input ref="coverInput" class="rform__hidden" type="file" accept="image/*" @change="onCover" />
    </div>

    <div class="rform__footer">
      <p v-if="errors.form" class="rform__error rform__error--form">{{ errors.form }}</p>
      <AppButton variant="accent" :disabled="saving" @click="submit">
        {{ isEdit ? 'Сохранить' : 'Создать отчет' }}
      </AppButton>
    </div>

    <ConfirmDialog
      :open="confirmOpen"
      title="Удалить отчет?"
      text="Удалив отчет он пропадет у всех пользователей, включая чат"
      :busy="deleting"
      @confirm="remove"
      @cancel="confirmOpen = false"
    />
  </section>
</template>

<style scoped>
.rform {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 16px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 32px);
  background:
    radial-gradient(120% 40% at 100% 0%, rgba(70, 40, 60, 0.35), transparent 70%),
    var(--color-bg);
}

.rform__top {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  min-height: 42px;
}

.rform__trash {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: #e0262b;
}
.rform__trash svg {
  width: 22px;
  height: 22px;
}

.rform__title {
  margin: 12px 0 4px;
}

.rform__group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.rfield {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 16px;
  border-radius: 20px;
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.rfield__label {
  display: flex;
  justify-content: flex-end;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.rfield__counter {
  color: var(--color-text-secondary);
}

.rfield__input {
  width: 100%;
  background: transparent;
  border: none;
  outline: none;
  color: var(--color-text);
  font-size: var(--font-size-base);
  font-family: inherit;
  line-height: 1.4;
  padding: 0;
}
.rfield__input::placeholder {
  color: var(--color-text-muted);
}

.rfield__textarea {
  min-height: 60px;
  resize: none;
}

.rfield--error .rfield__input::placeholder {
  color: var(--color-text-muted);
}

.rform__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 12px;
  border-radius: 20px;
  background: var(--color-surface);
}

.rchip {
  height: 40px;
  padding: 0 16px;
  flex-grow: 1;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: var(--color-text);
  font-size: var(--font-size-base);
  white-space: nowrap;
}

.rchip--on {
  background: var(--gradient-accent);
  border-color: rgba(255, 255, 255, 0.25);
}

.rfile {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
  padding: 0 16px;
  border-radius: 20px;
  background: var(--color-surface);
}

.rfile__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
}
.rfile__icon--add {
  background: #fff;
  color: #6fbf1f;
}
.rfile__icon--add svg {
  width: 22px;
  height: 22px;
}
.rfile__icon--html svg {
  width: 32px;
  height: 32px;
}
.rfile__icon--image {
  background: #fff;
}
.rfile__icon--image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.rfile__name {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-md);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rfile__btn {
  height: 32px;
  padding: 0 12px;
  border-radius: var(--radius-pill);
  background: var(--gradient-purple-solid);
  color: var(--color-text);
  font-size: var(--font-size-base);
  flex-shrink: 0;
}

.rform__error {
  font-size: var(--font-size-sm);
  color: var(--color-error);
}
.rform__error--form {
  text-align: center;
}

.rform__hidden {
  display: none;
}

.rform__footer {
  margin-top: auto;
  padding-top: 28px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
