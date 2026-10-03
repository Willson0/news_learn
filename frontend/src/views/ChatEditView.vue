<script>
import BackButton from '@/components/ui/BackButton.vue'
import { fetchMessages, updateChat } from '@/api/resources'
import { fileUrl } from '@/api/client'
import { haptic } from '@/telegram/webapp'

export default {
  name: 'ChatEditView',
  components: { BackButton },
  data() {
    return {
      name: '',
      avatar: null, // новый выбранный файл
      preview: null,
      error: '',
      saving: false,
    }
  },
  computed: {
    slug() {
      return this.$route.params.id
    },
    backTo() {
      return { name: 'chat-info', params: { id: this.slug } }
    },
  },
  mounted() {
    this.load()
  },
  beforeUnmount() {
    this.revoke()
  },
  methods: {
    async load() {
      try {
        const resp = await fetchMessages(this.slug)
        this.name = (resp.chat && resp.chat.title) || ''
        this.preview = fileUrl(resp.chat && resp.chat.avatar_url)
      } catch {
        this.name = ''
      }
    },
    pickPhoto() {
      this.$refs.file.click()
    },
    onFile(e) {
      const file = e.target.files && e.target.files[0]
      e.target.value = ''
      if (!file) return
      this.revoke()
      this.avatar = file
      this.preview = URL.createObjectURL(file)
    },
    revoke() {
      if (this.preview && this.preview.startsWith('blob:')) URL.revokeObjectURL(this.preview)
    },
    async save() {
      if (!this.name.trim()) {
        this.error = 'Напишите название'
        return
      }
      this.saving = true
      this.error = ''
      try {
        await updateChat(this.slug, { title: this.name.trim(), avatar: this.avatar })
        haptic('medium')
        this.$router.push(this.backTo)
      } catch (err) {
        const errors = (err && err.errors) || {}
        this.error = (errors.title && errors.title[0]) || (errors.avatar && errors.avatar[0]) || (err && err.message) || 'Не удалось сохранить'
      } finally {
        this.saving = false
      }
    },
  },
}
</script>

<template>
  <section class="cedit">
    <div class="cedit__top">
      <BackButton :to="backTo" />
      <button type="button" class="cedit__done" :disabled="saving" @click="save">Готово</button>
    </div>

    <div class="cedit__avatar-block">
      <img v-if="preview" class="cedit__avatar" :src="preview" alt="" @click="pickPhoto" />
      <div v-else class="cedit__avatar" @click="pickPhoto"></div>
      <button type="button" class="cedit__change" @click="pickPhoto">Изменить фотографию</button>
      <input ref="file" class="cedit__file" type="file" accept="image/*" @change="onFile" />
    </div>

    <label class="cedit__field">
      <span class="cedit__label">Название</span>
      <input v-model="name" class="cedit__input" type="text" @input="error = ''" />
    </label>
    <p v-if="error" class="cedit__error">{{ error }}</p>
  </section>
</template>

<style scoped>
.cedit {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 16px) var(--space-screen-x) 40px;
  background: var(--color-bg);
}
.cedit__top { display: flex; align-items: center; justify-content: space-between; }
.cedit__done {
  padding: 11px 14px;
  border-radius: var(--radius-pill);
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: var(--color-text);
  font-size: var(--font-size-base);
}
.cedit__done:disabled { opacity: 0.6; }
.cedit__avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  margin-top: -66px;
}
.cedit__avatar {
  width: 150px;
  height: 150px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
  background: linear-gradient(135deg, #d7cfe0 0%, #a9adbd 35%, #cdbfc9 60%, #b7c2bf 80%, #c4c7d2 100%);
}
.cedit__change { color: var(--color-text); font-size: var(--font-size-md); }
.cedit__file { display: none; }
.cedit__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  border-radius: var(--radius-input);
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.12);
}
.cedit__label { font-size: var(--font-size-sm); color: var(--color-text-secondary); }
.cedit__input {
  width: 100%;
  background: transparent;
  border: none;
  outline: none;
  color: var(--color-text);
  font-size: var(--font-size-md);
}
.cedit__error { margin-top: -16px; font-size: var(--font-size-sm); color: var(--color-error); }
</style>
