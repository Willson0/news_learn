<script>
import { haptic } from '@/telegram/webapp'

export default {
  name: 'ProfileEditView',
  components: {},
  data() {
    return {
      name: 'Артем',
      tag: '@Artemis',
    }
  },
  methods: {
    save() {
      haptic('medium')
      // TODO: отправить на бэкенд. Пока просто возвращаемся в профиль.
      this.$router.push({ name: 'profile' })
    },
    changePhoto() {
      // TODO: выбор фотографии
    },
  },
}
</script>

<template>
  <section class="edit">
    <div class="edit__top">
      <button type="button" class="edit__done" @click="save">Готово</button>
    </div>

    <div class="edit__avatar-block">
      <div class="edit__avatar"></div>
      <button type="button" class="edit__change" @click="changePhoto">
        Изменить фотографию
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
  width: 148px;
  height: 148px;
  border-radius: 50%;
  background: #ffffff;
}

.edit__change {
  color: var(--color-text);
  font-size: var(--font-size-md);
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
