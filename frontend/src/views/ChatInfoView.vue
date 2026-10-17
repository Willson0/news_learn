<script>
import IconBell from '@/components/icons/IconBell.vue'
import IconSearch from '@/components/icons/IconSearch.vue'
import IconLeave from '@/components/icons/IconLeave.vue'
import IconContact from '@/components/icons/IconContact.vue'
import IconPlay from '@/components/icons/IconPlay.vue'
import IconDownloadCloud from '@/components/icons/IconDownloadCloud.vue'
import IconGear from '@/components/icons/IconGear.vue'
import { fetchMessages, fetchParticipants, fetchChatAttachments } from '@/api/resources'
import { fileUrl } from '@/api/client'
import { session } from '@/api/session'

export default {
  name: 'ChatInfoView',
  components: { IconBell, IconSearch, IconLeave, IconContact, IconPlay, IconDownloadCloud, IconGear },
  data() {
    return {
      chat: { title: '', members: '', avatar: null },
      tab: 'members',
      tabs: [
        { value: 'members', label: 'Участники' },
        { value: 'media', label: 'Медиа' },
        { value: 'voice', label: 'Голосовые' },
        { value: 'files', label: 'Файлы' },
        { value: 'links', label: 'Ссылки' },
      ],
      members: [],
      media: [],
      voices: [],
      files: [],
      links: [],
      loaded: {}, // кэш загруженных вкладок
      audio: null,
      playingId: null,
    }
  },
  computed: {
    isAdmin() {
      return Boolean(session.user && session.user.is_admin)
    },
    backTo() {
      return { name: 'chat', params: { id: this.$route.params.id } }
    },
    linkGroups() {
      const groups = []
      const byMonth = {}
      for (const l of this.links) {
        const key = l.month || ''
        if (!byMonth[key]) {
          byMonth[key] = { month: key, items: [] }
          groups.push(byMonth[key])
        }
        byMonth[key].items.push(l)
      }
      return groups
    },
  },
  watch: {
    tab(value) {
      this.loadTab(value)
    },
  },
  mounted() {
    this.load()
  },
  beforeUnmount() {
    this.stopAudio()
  },
  methods: {
    slug() {
      return this.$route.params.id
    },
    async load() {
      try {
        const resp = await fetchMessages(this.slug())
        const c = resp.chat || {}
        this.chat = {
          title: c.title || '',
          members: c.subtitle || '',
          avatar: fileUrl(c.avatar_url),
        }
      } catch { /* оставляем пустым */ }
      this.loadTab(this.tab)
    },
    async loadTab(tab) {
      if (this.loaded[tab]) return
      try {
        if (tab === 'members') {
          this.members = await fetchParticipants(this.slug())
          if (!this.chat.members && this.members.length) {
            this.chat.members = `${this.members.length} участников`
          }
        } else if (tab === 'media') {
          this.media = await fetchChatAttachments(this.slug(), 'image')
        } else if (tab === 'voice') {
          this.voices = await fetchChatAttachments(this.slug(), 'voice')
        } else if (tab === 'files') {
          this.files = await fetchChatAttachments(this.slug(), 'file')
        } else if (tab === 'links') {
          this.links = await fetchChatAttachments(this.slug(), 'link')
        }
        this.loaded = { ...this.loaded, [tab]: true }
      } catch { /* показываем пустую вкладку */ }
    },
    fileUrl,
    openEdit() {
      this.$router.push({ name: 'chat-edit', params: { id: this.$route.params.id } })
    },
    toggleVoice(v) {
      if (this.playingId === v.id) {
        this.stopAudio()
        return
      }
      this.stopAudio()
      this.audio = new Audio(fileUrl(v.url))
      this.audio.addEventListener('ended', () => { this.playingId = null })
      this.audio.play().then(() => { this.playingId = v.id }).catch(() => { this.playingId = null })
    },
    stopAudio() {
      if (this.audio) { this.audio.pause(); this.audio = null }
      this.playingId = null
    },
  },
}
</script>

<template>
  <section class="info">
    <!-- Шапка с аватаром -->
    <header class="info__hero">
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
        <li v-if="!members.length" class="info__empty">Пока нет участников</li>
        <li v-for="(m, i) in members" :key="i" class="info__member">
          <img v-if="m.avatar_url" class="info__av" :src="m.avatar_url" alt="" />
          <span v-else class="info__av"></span>
          <div class="info__member-body">
            <span class="info__member-name">{{ m.name }}</span>
            <span class="info__member-seen">{{ m.seen }}</span>
          </div>
          <span class="info__member-role">{{ m.role }}</span>
        </li>
      </ul>

      <!-- Медиа -->
      <div v-else-if="tab === 'media'" class="info__grid">
        <p v-if="!media.length" class="info__empty">Пока нет медиа</p>
        <a
          v-for="(a, i) in media"
          :key="i"
          class="info__cell"
          :href="fileUrl(a.url)"
          target="_blank"
          rel="noopener"
          :style="{ backgroundImage: `url(${fileUrl(a.url)})` }"
        ></a>
      </div>

      <!-- Голосовые -->
      <ul v-else-if="tab === 'voice'" class="info__list">
        <li v-if="!voices.length" class="info__empty">Пока нет голосовых</li>
        <li v-for="(v, i) in voices" :key="i" class="info__voice">
          <button type="button" class="info__play" :aria-label="playingId === v.id ? 'Пауза' : 'Играть'" @click="toggleVoice(v)">
            <IconPlay />
          </button>
          <div class="info__member-body">
            <span class="info__member-name">{{ v.author || 'Голосовое' }}</span>
            <span class="info__member-seen">{{ v.meta }}</span>
          </div>
        </li>
      </ul>

      <!-- Файлы -->
      <ul v-else-if="tab === 'files'" class="info__list">
        <li v-if="!files.length" class="info__empty">Пока нет файлов</li>
        <li v-for="(f, i) in files" :key="i" class="info__file">
          <span class="info__file-badge">{{ f.ext }}</span>
          <div class="info__member-body">
            <span class="info__member-name">{{ f.name }}</span>
            <span class="info__member-seen">{{ f.meta }}</span>
          </div>
          <a :href="fileUrl(f.url)" :download="f.name" target="_blank" rel="noopener" aria-label="Скачать">
            <IconDownloadCloud class="info__file-dl" />
          </a>
        </li>
      </ul>

      <!-- Ссылки -->
      <div v-else class="info__links">
        <p v-if="!links.length" class="info__empty">Пока нет ссылок</p>
        <template v-for="(g, gi) in linkGroups" :key="gi">
          <p v-if="g.month" class="info__link-month">{{ g.month }}</p>
          <a
            v-for="(l, li) in g.items"
            :key="li"
            class="info__link"
            :href="l.url"
            target="_blank"
            rel="noopener"
          >
            <span class="info__av info__av--sq"></span>
            <div class="info__link-body">
              <span class="info__member-name">{{ l.title }}</span>
              <span class="info__link-text">{{ l.text }}</span>
              <span class="info__link-url">{{ l.url }}</span>
            </div>
          </a>
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

/* Доп. правила продукта */
.info__empty { color: var(--color-text-secondary); font-size: var(--font-size-base); padding: 20px 0; }
.info__av { object-fit: cover; }
.info__cell { display: block; background-size: cover; background-position: center; }
.info__play { cursor: pointer; }
.info__links a.info__link { text-decoration: none; color: inherit; }
</style>
