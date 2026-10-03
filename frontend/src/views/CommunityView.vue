<script>
import AppInput from '@/components/ui/AppInput.vue'
import IconPin from '@/components/icons/IconPin.vue'
import SegmentedControl from '@/components/ui/SegmentedControl.vue'
import { fetchChats } from '@/api/resources'
import { fileUrl } from '@/api/client'
import { session } from '@/api/session'

export default {
  name: 'CommunityView',
  components: { AppInput, IconPin, SegmentedControl },
  data() {
    return {
      query: '',
      // Вкладки админа: все чаты / личные чаты пользователей с админом.
      tab: 'all',
      tabs: [
        { value: 'all', label: 'Все чаты' },
        { value: 'direct', label: 'Личные чаты' },
      ],
      sections: [
        {
          key: 'admin',
          title: 'Чат с админом',
          items: [
            {
              id: 'admin',
              title: 'Игорь Гломозда',
              sender: 'Игор',
              preview: 'Что интересует?',
              time: '16:32',
              unread: 1,
            },
          ],
        },
        {
          key: 'main',
          title: 'Чаты',
          items: [
            {
              id: 'general',
              title: 'Общий чат',
              sender: 'Артем',
              preview: 'Кто читал новый отчет по золоту?',
              time: '16:32',
              unread: 234,
            },
          ],
        },
        {
          key: 'reports',
          title: 'Чаты по отчетам',
          items: [
            { id: 'gold', title: 'Отчет по золоту 26.02.26', sender: 'Вы', preview: 'Я вот что не понял это хедж и к...', time: '16:32', unread: 12, pinned: true },
            { id: 'oil', title: 'Нефть 12.12.27', sender: 'Артем', preview: 'Кто читал новый отчет по золоту?', time: '16:32', unread: 234 },
            { id: 'plat1', title: 'Отчет по платине 26.02.26', sender: 'Артем', preview: 'Кто читал новый отчет по золоту?', time: '16:32', unread: 234 },
            { id: 'plat2', title: 'Отчет по платине 26.02.26', sender: 'Артем', preview: 'Кто читал новый отчет по золоту?', time: '16:32', unread: 234 },
            { id: 'plat3', title: 'Отчет по платине 26.02.26', sender: 'Артем', preview: 'Кто читал новый отчет по золоту?', time: '16:32', unread: 234 },
          ],
        },
      ],
    }
  },
  computed: {
    isAdmin() {
      return Boolean(session.user && session.user.is_admin)
    },
    visibleSections() {
      let sections = this.sections
      if (this.isAdmin) {
        sections = sections.filter((s) => (this.tab === 'direct' ? s.key === 'direct' : s.key !== 'direct'))
      }
      const q = this.query.trim().toLowerCase()
      if (!q) return sections
      return sections
        .map((s) => ({
          ...s,
          items: s.items.filter((i) => `${i.title} ${i.preview || ''}`.toLowerCase().includes(q)),
        }))
        .filter((s) => s.items.length)
    },
    searchPlaceholder() {
      return this.isAdmin && this.tab === 'direct' ? 'Нужный чат' : 'Нужный отчет или материал'
    },
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      try {
        const sections = await fetchChats()
        if (Array.isArray(sections) && sections.length) this.sections = sections
      } catch {
        /* оставляем демо-данные, если список не загрузился */
      }
    },
    avatar(item) {
      return fileUrl(item.avatar_url)
    },
    openChat(item) {
      this.$router.push({ name: 'chat', params: { id: item.id } })
    },
  },
}
</script>

<template>
  <section class="community">
    <AppInput v-model="query" :placeholder="searchPlaceholder" class="community__search" />

    <SegmentedControl v-if="isAdmin" v-model="tab" :options="tabs" class="community__tabs" />

    <div v-for="section in visibleSections" :key="section.key" class="community__section">
      <h2 class="community__title screen-title">{{ section.title }}</h2>
      <ul class="community__list">
        <li
          v-for="item in section.items"
          :key="item.id"
          class="chat-row"
          @click="openChat(item)"
        >
          <img v-if="avatar(item)" class="chat-row__avatar" :src="avatar(item)" alt="" />
          <span v-else class="chat-row__avatar"></span>
          <div class="chat-row__body">
            <div class="chat-row__top">
              <span class="chat-row__name">{{ item.title }}</span>
              <span class="chat-row__time">
                <IconPin v-if="item.pinned" class="chat-row__pin" />
                {{ item.time }}
              </span>
            </div>
            <p v-if="item.sender" class="chat-row__sender">{{ item.sender }}</p>
            <div class="chat-row__bottom">
              <p class="chat-row__preview">{{ item.preview }}</p>
              <span v-if="item.unread" class="chat-row__badge">{{ item.unread }}</span>
            </div>
          </div>
        </li>
      </ul>
    </div>

    <p v-if="!visibleSections.length" class="community__empty">
      {{ isAdmin && tab === 'direct' ? 'Личных сообщений пока нет' : 'Ничего не найдено' }}
    </p>
  </section>
</template>

<style scoped>
.community {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 56px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 120px);
  background: var(--color-bg);
}

.community__tabs {
  padding: 4px;
  border-radius: var(--radius-pill);
  border: 1px solid rgba(255, 255, 255, 0.14);
}
.community__tabs :deep(.segmented__item) {
  height: 48px;
  border-radius: var(--radius-pill);
}

.community__empty {
  padding: 40px 0;
  text-align: center;
  color: var(--color-text-secondary);
}

.community__section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.community__title {
  margin: 0;
}

.community__list {
  display: flex;
  flex-direction: column;
}

.chat-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  cursor: pointer;
}
.chat-row:last-child {
  border-bottom: none;
}

.chat-row__avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #8a8a8a;
  object-fit: cover;
  flex-shrink: 0;
}

.chat-row__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.chat-row__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.chat-row__name {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.chat-row__time {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
.chat-row__pin {
  width: 14px;
  height: 14px;
  color: var(--color-text-secondary);
}

.chat-row__sender {
  font-size: var(--font-size-base);
  color: var(--color-text);
}

.chat-row__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.chat-row__preview {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.chat-row__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 20px;
  padding: 0 8px;
  border-radius: var(--radius-pill);
  border: 1px solid rgba(255, 255, 255, 0.2);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  flex-shrink: 0;
}
</style>
