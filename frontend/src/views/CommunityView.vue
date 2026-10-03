<script>
import AppInput from '@/components/ui/AppInput.vue'
import IconPin from '@/components/icons/IconPin.vue'

export default {
  name: 'CommunityView',
  components: { AppInput, IconPin },
  data() {
    return {
      query: '',
      // Демо-данные. Реальные чаты придут с бэкенда.
      sections: [
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
            { id: 'gold', title: 'Отчет по золоту 26.02.26', sender: 'Артем', preview: 'Кто читал новый отчет по золоту?', time: '16:32', unread: 234 },
            { id: 'oil', title: 'Нефть 12.12.27', sender: 'Артем', preview: 'Кто читал новый отчет по золоту?', time: '16:32', unread: 234 },
            { id: 'plat1', title: 'Отчет по платине 26.02.26', sender: 'Артем', preview: 'Кто читал новый отчет по золоту?', time: '16:32', unread: 234 },
            { id: 'plat2', title: 'Отчет по платине 26.02.26', sender: 'Артем', preview: 'Кто читал новый отчет по золоту?', time: '16:32', unread: 234 },
            { id: 'plat3', title: 'Отчет по платине 26.02.26', sender: 'Артем', preview: 'Кто читал новый отчет по золоту?', time: '16:32', unread: 234 },
          ],
        },
      ],
    }
  },
  methods: {
    openChat(item) {
      this.$router.push({ name: 'chat', params: { id: item.id } })
    },
  },
}
</script>

<template>
  <section class="community">
    <AppInput v-model="query" placeholder="Нужный отчет или материал" class="community__search" />

    <div v-for="section in sections" :key="section.key" class="community__section">
      <h2 class="community__title screen-title">{{ section.title }}</h2>
      <ul class="community__list">
        <li
          v-for="item in section.items"
          :key="item.id"
          class="chat-row"
          @click="openChat(item)"
        >
          <span class="chat-row__avatar"></span>
          <div class="chat-row__body">
            <div class="chat-row__top">
              <span class="chat-row__name">{{ item.title }}</span>
              <span class="chat-row__time">
                <IconPin v-if="item.pinned" class="chat-row__pin" />
                {{ item.time }}
              </span>
            </div>
            <p class="chat-row__sender">{{ item.sender }}</p>
            <div class="chat-row__bottom">
              <p class="chat-row__preview">{{ item.preview }}</p>
              <span v-if="item.unread" class="chat-row__badge">{{ item.unread }}</span>
            </div>
          </div>
        </li>
      </ul>
    </div>
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
