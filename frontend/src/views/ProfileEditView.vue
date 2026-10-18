<script>
import { haptic } from '@/telegram/webapp'
import { fileUrl } from '@/api/client'
import { fetchProfile, updateProfile, uploadAvatar, removeAvatar } from '@/api/resources'
import { session, setSessionUser } from '@/api/session'

export default {
  name: 'ProfileEditView',
  components: {},
  data() {
    return {
      name: session.user?.name || '',
      tag: session.user?.username || '',
      avatarUrl: session.user?.avatar_url || null,
      preview: null,
      saving: false,
      uploading: false,
    }
  },
  computed: {
    avatarSrc() {
      return this.preview || fileUrl(this.avatarUrl)
    },
    initial() {
      const t = (this.tag || this.name || '').replace(/^@/, '')
      return t ? t.charAt(0).toUpperCase() : ''
    },
  },
  async mounted() {
    try {
      const profile = await fetchProfile()
      if (profile.user) {
        this.name = profile.user.name || this.name
        this.tag = profile.user.username || this.tag
        this.avatarUrl = profile.user.avatar_url || this.avatarUrl
      }
    } catch {
      // оставляем данные из сессии
    }
  },
  methods: {
    syncSession(user) {
      this.avatarUrl = user.avatar_url || null
      setSessionUser({ ...(session.user || {}), ...user })
    },
    pickPhoto() {
      this.$refs.file.click()
    },
    async onFile(e) {
      const file = e.target.files && e.target.files[0]
      e.target.value = ''
      if (!file) return
      if (this.preview) URL.revokeObjectURL(this.preview)
      this.preview = URL.createObjectURL(file)
      this.uploading = true
      haptic('light')
      try {
        const user = await uploadAvatar(file)
        this.syncSession(user)
      } catch {
        haptic('rigid')
      } finally {
        if (this.preview) {
          URL.revokeObjectURL(this.preview)
          this.preview = null
        }
        this.uploading = false
      }
    },
    async deletePhoto() {
      if (this.uploading || !this.avatarUrl) return
      this.uploading = true
      haptic('light')
      try {
        const user = await removeAvatar()
        this.syncSession(user)
      } catch {
        haptic('rigid')
      } finally {
        this.uploading = false
      }
    },
    async save() {
      if (this.saving) return
      this.saving = true
      haptic('medium')
      try {
        await updateProfile({ name: this.name.trim(), username: this.tag.trim() || null })
        setSessionUser({ ...(session.user || {}), name: this.name.trim(), username: this.tag.trim() || null })
      } catch {
        haptic('rigid')
      } finally {
        this.saving = false
        this.$router.push({ name: 'profile' })
      }
    },
  },
}
</script>

<template>
  <section class="edit">
    <div class="edit__top">
      <button type="button" class="edit__done" @click="save">
        {{ saving ? 'Сохранение…' : 'Готово' }}
      </button>
    </div>

    <div class="edit__avatar-block">
      <div class="edit__avatar">
        <img v-if="avatarSrc" class="edit__avatar-img" :src="avatarSrc" alt="" />
        <span v-else-if="initial" class="edit__avatar-initial">{{ initial }}</span>
      </div>
      <input ref="file" type="file" accept="image/*" class="edit__file" @change="onFile" />
      <button type="button" class="edit__change" :disabled="uploading" @click="pickPhoto">
        {{ uploading ? 'Загрузка…' : 'Изменить фотографию' }}
      </button>
      <button
        v-if="avatarUrl"
        type="button"
        class="edit__remove"
        :disabled="uploading"
        @click="deletePhoto"
      >
        Удалить фото
      </button>
    </div>

    <div class="edit__fields">
      <label class="edit__field">
        <span class="edit__label">Имя</span>
        <input v-model="name" class="edit__input" type="text" />
      </label>
      <label class="edit__field">
        <span class="edit__label">Тег</span>
        <input v-model="tag" class="edit__input" type="text" />
      </label>
    </div>
  </section>
</template>

<style scoped>
.edit {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 16px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 40px);
  background: var(--color-bg);
}

.edit__top {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.edit__done {
  padding: 11px 22px;
  border-radius: var(--radius-pill);
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: var(--color-text);
  font-size: var(--font-size-base);
}

.edit__avatar-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 8px 0;
}

.edit__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 148px;
  height: 148px;
  border-radius: 50%;
  overflow: hidden;
  background: linear-gradient(135deg, #6b7350 0%, #3f4a2c 100%);
}

.edit__avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.edit__avatar-initial {
  font-size: 56px;
  font-weight: 700;
  color: var(--color-text);
}

.edit__file {
  display: none;
}

.edit__change {
  color: var(--color-text);
  font-size: var(--font-size-md);
}

.edit__change:disabled {
  opacity: 0.5;
}

.edit__remove {
  color: #ff4d4d;
  font-size: var(--font-size-sm);
}

.edit__remove:disabled {
  opacity: 0.5;
}

.edit__fields {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.edit__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  border-radius: var(--radius-input);
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.edit__label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.edit__input {
  width: 100%;
  background: transparent;
  border: none;
  outline: none;
  color: var(--color-text);
  font-size: var(--font-size-md);
}
</style>
