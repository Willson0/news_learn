<script>
import BackButton from '@/components/ui/BackButton.vue'
import IconBell from '@/components/icons/IconBell.vue'
import IconSearch from '@/components/icons/IconSearch.vue'
import IconLeave from '@/components/icons/IconLeave.vue'
import IconContact from '@/components/icons/IconContact.vue'
import IconPlay from '@/components/icons/IconPlay.vue'
import IconDownloadCloud from '@/components/icons/IconDownloadCloud.vue'
import IconGear from '@/components/icons/IconGear.vue'
import { fetchMessages } from '@/api/resources'
import { fileUrl } from '@/api/client'
import { session } from '@/api/session'

export default {
  name: 'ChatInfoView',
  components: { BackButton, IconBell, IconSearch, IconLeave, IconContact, IconPlay, IconDownloadCloud, IconGear },
  data() {
    return {
      chat: { title: 'Нефть 12.12.27', members: '15 участников', avatar: null },
      tab: 'media',
      tabs: [
        { value: 'members', label: 'Участники' },
        { value: 'media', label: 'Медиа' },
        { value: 'voice', label: 'Голосовые' },
        { value: 'files', label: 'Файлы' },
        { value: 'links', label: 'Ссылки' },
      ],
      members: [
        { name: 'Владислав', seen: 'Был в сети 58 минут назад', role: 'Автор' },
        { name: 'Алексей', seen: 'Был в сети 15 минут назад', role: 'Участник' },
        { name: 'Борат', seen: 'Был в сети 1 час назад', role: 'Участник' },
        { name: 'Владимир', seen: 'Был в сети 2 часа назад', role: 'Участник' },
        { name: 'Артем в2', seen: 'Был в сети 50 минут назад', role: 'Участник' },
      ],
      voices: Array.from({ length: 6 }, () => ({ name: 'Владислав', meta: '20:42 / 6 сент. 2026 в 8:32' })),
      files: [
        { ext: 'pdf', color: '#e8503a', name: 'pdf file for windows', meta: '1.9 мб / 6 сент. 2026 в 8:32' },
        { ext: 'exe', color: '#3a7be8', name: 'pdf file for windows', meta: '1.9 мб / 6 сент. 2026 в 8:32' },
        { ext: 'zip', color: '#7fbf1f', name: 'pdf file for windows', meta: '1.9 мб / 6 сент. 2026 в 8:32' },
        { ext: '', color: '#cfcfcf', name: 'Image0340', meta: '1.9 мб / 6 сент. 2026 в 8:32' },
      ],
      linkGroups: [
        {
          month: '',
          items: [
            { title: 'Telegram', text: 'Новые функции уже в телеграм, работают не только с премиум, но и без премиума, переходите чтобы узнать больше!', url: 'rbk.ru/news' },
          ],
        },
        {
          month: 'Август 2026',
          items: [
            { title: 'Telegram', text: 'Новые функции уже в телеграм, работают не только с премиум, но и без премиума, переходите чтобы узнать больше!', url: 'rbk.ru/news' },
            { title: 'Telegram', text: 'Новые функции уже в телеграм, работают не только с премиум, но и без премиума, переходите чтобы узнать больше!', url: 'rbk.ru/news' },
          ],
        },
      ],
      mediaCount: 9,
    }
  },
  computed: {
    isAdmin() {
      return Boolean(session.user && session.user.is_admin)
    },
    backTo() {
      return { name: 'chat', params: { id: this.$route.params.id } }
    },
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      try {
        const resp = await fetchMessages(this.$route.params.id)
        const c = resp.chat || {}
        this.chat = {
          title: c.title || this.chat.title,
          members: c.subtitle || '',
          avatar: fileUrl(c.avatar_url),
        }
      } catch {
        /* оставляем демо-данные */
      }
    },
    openEdit() {
      this.$router.push({ name: 'chat-edit', params: { id: this.$route.params.id } })
    },
  },
}
</script>

<template>
  <section class="info">
    <!-- Шапка с аватаром -->
    <header class="info__hero">
      <BackButton :to="backTo" class="info__back" />
      <!-- Админ: шестерёнка ведёт в настройки чата, «контакт» уходит к названию -->
      <button
        v-if="isAdmin"
        type="button"
        class="info__contact"
        aria-label="Настройки чата"
        @click="openEdit"
      >
        <IconGear />
      </button>
      <button
        type="button"
        class="info__contact"
        :class="{ 'info__contact--title': isAdmin }"
        aria-label="Отчёт чата"
      >
        <IconContact />
      </button>
      <img v-if="chat.avatar" class="info__avatar" :src="chat.avatar" alt="" />
      <div v-else class="info__avatar"></div>
      <h1 class="info__title">{{ chat.title }}</h1>
      <p class="info__members">{{ chat.members }}</p>
    </header>

    <!-- Действия -->
    <div class="info__actions">
      <button type="button" class="info__action" aria-label="Уведомления"><IconBell /></button>
      <button type="button" class="info__action" aria-label="Поиск"><IconSearch /></button>
      <button type="button" class="info__action" aria-label="Выйти из чата"><IconLeave /></button>
    </div>

    <!-- Вкладки (прокручиваемые) -->
    <div class="info__tabs">
      <button
        v-for="t in tabs"
        :key="t.value"
        type="button"
        class="info__tab"
        :class="{ 'info__tab--active': tab === t.value }"
        @click="tab = t.value"
      >
        {{ t.label }}
      </button>
    </div>

    <!-- Контент вкладки -->
    <div class="info__content">
      <!-- Участники -->
      <ul v-if="tab === 'members'" class="info__list">
        <li v-for="(m, i) in members" :key="i" class="info__member">
          <span class="info__av"></span>
          <div class="info__member-body">
            <span class="info__member-name">{{ m.name }}</span>
            <span class="info__member-seen">{{ m.seen }}</span>
          </div>
          <span class="info__member-role">{{ m.role }}</span>
        </li>
      </ul>

      <!-- Медиа -->
      <div v-else-if="tab === 'media'" class="info__grid">
        <span v-for="i in mediaCount" :key="i" class="info__cell"></span>
      </div>

      <!-- Голосовые -->
      <ul v-else-if="tab === 'voice'" class="info__list">
        <li v-for="(v, i) in voices" :key="i" class="info__voice">
          <span class="info__play"><IconPlay /></span>
          <div class="info__member-body">
            <span class="info__member-name">{{ v.name }}</span>
            <span class="info__member-seen">{{ v.meta }}</span>
          </div>
        </li>
      </ul>

      <!-- Файлы -->
      <ul v-else-if="tab === 'files'" class="info__list">
        <li v-for="(f, i) in files" :key="i" class="info__file">
          <span class="info__file-badge" :style="{ background: f.color }">{{ f.ext }}</span>
          <div class="info__member-body">
            <span class="info__member-name">{{ f.name }}</span>
            <span class="info__member-seen">{{ f.meta }}</span>
          </div>
          <IconDownloadCloud class="info__file-dl" />
        </li>
      </ul>

      <!-- Ссылки -->
      <div v-else class="info__links">
        <template v-for="(g, gi) in linkGroups" :key="gi">
          <p v-if="g.month" class="info__link-month">{{ g.month }}</p>
          <div v-for="(l, li) in g.items" :key="li" class="info__link">
            <span class="info__av info__av--sq"></span>
            <div class="info__link-body">
              <span class="info__member-name">{{ l.title }}</span>
              <span class="info__link-text">{{ l.text }}</span>
              <span class="info__link-url">{{ l.url }}</span>
            </div>
          </div>
        </template>
      </div>
    </div>
  </section>
</template>

<style scoped>
.info {
  display: flex;
  flex-direction: column;
  min-height: var(--app-height);
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
.info__back { position: relative; z-index: 2; }
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
.info__contact svg { width: 22px; height: 22px; }
.info__contact--title {
  top: auto;
  bottom: 24px;
}
.info__avatar {
  align-self: center;
  width: 150px;
  height: 150px;
  margin-top: -36px;
  border-radius: 50%;
  object-fit: cover;
  background: linear-gradient(135deg, #d7cfe0 0%, #a9adbd 35%, #cdbfc9 60%, #b7c2bf 80%, #c4c7d2 100%);
}
.info__title { margin: 10px 0 0; font-size: var(--font-size-title); font-weight: 700; }
.info__members { font-size: var(--font-size-base); color: var(--color-text-secondary); }

/* --- Действия --- */
.info__actions { display: flex; gap: 12px; padding: 16px var(--space-screen-x) 0; }
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
.info__action svg { width: 22px; height: 22px; }

/* --- Вкладки --- */
.info__tabs {
  display: flex;
  gap: 4px;
  margin: 16px var(--space-screen-x);
  padding: 6px;
  border-radius: var(--radius-card);
  background: var(--color-surface-muted);
  overflow-x: auto;
  scrollbar-width: none;
}
.info__tabs::-webkit-scrollbar { display: none; }
.info__tab {
  flex: 0 0 auto;
  height: 40px;
  padding: 0 18px;
  border-radius: var(--radius-chip);
  color: var(--color-text);
  font-size: var(--font-size-base);
  white-space: nowrap;
}
.info__tab--active { background: var(--gradient-accent-purple); }

/* --- Контент --- */
.info__content { padding: 0 var(--space-screen-x) calc(env(safe-area-inset-bottom, 0px) + 24px); }

.info__list { display: flex; flex-direction: column; }
.info__member, .info__voice, .info__file {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.info__av { width: 44px; height: 44px; border-radius: 50%; background: #8a8a8a; flex-shrink: 0; }
.info__av--sq { border-radius: 10px; }
.info__member-body { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.info__member-name { font-size: var(--font-size-md); font-weight: 700; }
.info__member-seen {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.info__member-role { font-size: var(--font-size-base); color: var(--color-text-secondary); flex-shrink: 0; }

.info__play {
  display: flex; align-items: center; justify-content: center;
  width: 44px; height: 44px; border-radius: 50%;
  background: #4e8f10; color: #fff; flex-shrink: 0;
}
.info__play svg { width: 18px; height: 18px; margin-left: 2px; }

.info__file-badge {
  display: flex; align-items: center; justify-content: center;
  width: 44px; height: 44px; border-radius: 10px;
  color: #fff; font-size: var(--font-size-sm); font-weight: 700; flex-shrink: 0;
  text-transform: lowercase;
}
.info__file-dl { width: 24px; height: 24px; color: var(--color-text-secondary); flex-shrink: 0; }

/* --- Сетка медиа --- */
.info__grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 2px;
  margin: 0 calc(-1 * var(--space-screen-x));
}
.info__cell { aspect-ratio: 1 / 1; background: #d4d4d4; }

/* --- Ссылки --- */
.info__links { display: flex; flex-direction: column; gap: 10px; }
.info__link-month { font-size: var(--font-size-base); color: var(--color-text-secondary); margin: 8px 0 2px; }
.info__link {
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: 14px;
  background: var(--color-surface);
}
.info__link-body { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.info__link-text { font-size: var(--font-size-base); color: var(--color-text-secondary); line-height: 1.3; }
.info__link-url { font-size: var(--font-size-base); color: #6ea8e0; }
</style>
