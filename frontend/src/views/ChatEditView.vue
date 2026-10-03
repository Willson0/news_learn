<script>
import BackButton from '@/components/ui/BackButton.vue'

export default {
  name: 'ChatEditView',
  components: { BackButton },
  data() {
    return { name: 'Артем' }
  },
  computed: {
    backTo() {
      return { name: 'chat-info', params: { id: this.$route.params.id } }
    },
  },
}
</script>

<template>
  <section class="cedit">
    <div class="cedit__top">
      <BackButton :to="backTo" />
      <button type="button" class="cedit__done">Искать</button>
    </div>

    <div class="cedit__avatar-block">
      <div class="cedit__avatar"></div>
      <button type="button" class="cedit__change">Изменить фотографию</button>
    </div>

    <label class="cedit__field">
      <span class="cedit__label">Имя</span>
      <input v-model="name" class="cedit__input" type="text" />
    </label>
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
  padding: 11px 22px;
  border-radius: var(--radius-pill);
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.15);
  color: var(--color-text);
  font-size: var(--font-size-base);
}
.cedit__avatar-block { display: flex; flex-direction: column; align-items: center; gap: 14px; }
.cedit__avatar {
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: linear-gradient(135deg, #d7cfe0 0%, #a9adbd 35%, #cdbfc9 60%, #b7c2bf 80%, #c4c7d2 100%);
}
.cedit__change { color: var(--color-text); font-size: var(--font-size-md); }
.cedit__field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 16px;
  border-radius: var(--radius-input);
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.08);
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
</style>
