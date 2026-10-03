<script>
import BackButton from '@/components/ui/BackButton.vue'
import SegmentedControl from '@/components/ui/SegmentedControl.vue'
import IconBell from '@/components/icons/IconBell.vue'
import IconSearch from '@/components/icons/IconSearch.vue'
import IconLeave from '@/components/icons/IconLeave.vue'
import IconContact from '@/components/icons/IconContact.vue'

export default {
  name: 'ChatInfoView',
  components: { BackButton, SegmentedControl, IconBell, IconSearch, IconLeave, IconContact },
  data() {
    return {
      chat: {
        title: 'Нефть 12.12.27',
        members: '15 участников',
      },
      tab: 'media',
      tabs: [
        { value: 'members', label: 'Участники' },
        { value: 'media', label: 'Медиа' },
        { value: 'voice', label: 'Голосовые' },
        { value: 'files', label: 'Файлы' },
      ],
      mediaCount: 9,
    }
  },
  computed: {
    backTo() {
      return { name: 'chat', params: { id: this.$route.params.id } }
    },
  },
}
</script>

<template>
  <section class="info">
    <!-- Шапка с аватаром -->
    <header class="info__hero">
      <BackButton :to="backTo" class="info__back" />
      <button type="button" class="info__contact" aria-label="Контакт">
        <IconContact />
      </button>
      <div class="info__avatar"></div>
      <h1 class="info__title">{{ chat.title }}</h1>
      <p class="info__members">{{ chat.members }}</p>
    </header>

    <!-- Действия -->
    <div class="info__actions">
      <button type="button" class="info__action" aria-label="Уведомления"><IconBell /></button>
      <button type="button" class="info__action" aria-label="Поиск"><IconSearch /></button>
      <button type="button" class="info__action" aria-label="Выйти из чата"><IconLeave /></button>
    </div>

    <!-- Вкладки -->
    <SegmentedControl v-model="tab" :options="tabs" class="info__tabs" />

    <!-- Контент вкладки -->
    <div v-if="tab === 'media'" class="info__grid">
      <span v-for="i in mediaCount" :key="i" class="info__cell"></span>
    </div>
    <p v-else class="info__empty">Здесь пока пусто</p>
  </section>
</template>

<style scoped>
.info {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: var(--app-height);
  padding-bottom: calc(env(safe-area-inset-bottom, 0px) + 24px);
  background: var(--color-bg);
}

/* --- Шапка --- */
.info__hero {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: calc(env(safe-area-inset-top, 0px) + 16px) var(--space-screen-x) 20px;
  background:
    radial-gradient(90% 80% at 50% -10%, rgba(120, 110, 120, 0.4), transparent 70%),
    linear-gradient(180deg, #343235 0%, #1a1b1d 100%);
}

.info__back {
  position: relative;
  z-index: 2;
}

.info__contact {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 16px);
  right: var(--space-screen-x);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--color-surface-muted);
  color: var(--color-text);
}
.info__contact svg {
  width: 22px;
  height: 22px;
}

.info__avatar {
  align-self: center;
  width: 150px;
  height: 150px;
  margin-top: -36px;
  border-radius: 50%;
  background:
    linear-gradient(135deg, #d7cfe0 0%, #a9adbd 35%, #cdbfc9 60%, #b7c2bf 80%, #c4c7d2 100%);
}

.info__title {
  margin: 10px 0 0;
  font-size: var(--font-size-title);
  font-weight: 700;
}

.info__members {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
}

/* --- Действия --- */
.info__actions {
  display: flex;
  gap: 12px;
  padding: 0 var(--space-screen-x);
}

.info__action {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 52px;
  border-radius: var(--radius-card);
  background: var(--color-surface);
  color: var(--color-text);
}
.info__action svg {
  width: 22px;
  height: 22px;
}

/* --- Вкладки --- */
.info__tabs {
  margin: 0 var(--space-screen-x);
}

/* --- Сетка медиа --- */
.info__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2px;
}

.info__cell {
  aspect-ratio: 1 / 1;
  background: #d4d4d4;
}

.info__empty {
  padding: 40px var(--space-screen-x);
  text-align: center;
  color: var(--color-text-secondary);
}
</style>
