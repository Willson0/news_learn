<script>
import BackButton from '@/components/ui/BackButton.vue'
import MessageBubble from '@/components/community/MessageBubble.vue'
import IconPlus from '@/components/icons/IconPlus.vue'
import IconMic from '@/components/icons/IconMic.vue'
import IconReply from '@/components/icons/IconReply.vue'
import IconCopy from '@/components/icons/IconCopy.vue'
import IconPin from '@/components/icons/IconPin.vue'
import IconLink from '@/components/icons/IconLink.vue'
import IconReport from '@/components/icons/IconReport.vue'
import IconTrash from '@/components/icons/IconTrash.vue'
import IconForward from '@/components/icons/IconForward.vue'
import IconCheckSquare from '@/components/icons/IconCheckSquare.vue'
import IconClose from '@/components/icons/IconClose.vue'
import IconBack from '@/components/icons/IconBack.vue'

export default {
  name: 'ChatView',
  components: {
    BackButton, MessageBubble, IconPlus, IconMic, IconReply, IconCopy,
    IconPin, IconLink, IconReport, IconTrash, IconForward, IconCheckSquare, IconClose, IconBack,
  },
  data() {
    return {
      draft: '',
      replyTo: null,
      menuFor: null,
      selectMode: false,
      selected: [],
      reactions: ['👍', '❤️', '🔥', '😂', '😮', '😢', '🙏', '👏'],
      pinned: { title: 'Закрепленное сообщение', text: 'Здравствуте! Помогите разобраться...' },
      attachOpen: false,
      recState: 'idle', // idle | recording | locked | paused
      recTime: '01:00:39',
      photoViewer: false,
      mentionList: [
        { name: 'Владислав', handle: '@Submetal' },
        { name: 'Артем', handle: '@Submetalllliiist' },
        { name: 'Влад', handle: '@Submetaliiist' },
      ],
      groupMessages: [
        { id: 1, mine: false, author: 'Артем', role: 'Участник', text: 'Здравствуте! Помогите разобраться с графиком', time: '20:41' },
        { id: 2, mine: true, text: 'А то никак понять не могу', time: '20:42', read: true },
        {
          id: 3, mine: false, author: 'Владимир', role: 'Участник',
          reply: { author: 'Алексей', text: 'Я сам немного не оче...' },
          text: 'Ага, сам не понял, но видно, что очень хорошо сделан отчет и много чего было все равно было объяснено русским языком',
          time: '20:42',
          reactions: [{ emoji: '👍', count: 2 }],
        },
        { id: 4, mine: true, voice: { duration: '10:42' }, time: '20:42', read: true },
        { id: 5, mine: false, author: 'Артем', role: 'Участник', voice: { duration: '00:42' }, time: '20:42' },
        {
          id: 6, mine: false, author: 'Артем', role: 'Участник',
          text: 'Здравствуте! Помогите разобраться с графиком',
          file: { ext: 'zip', name: 'zip file for windows', size: '1.9 мб' },
          time: '20:42',
        },
        { id: 7, mine: true, image: true, time: '20:42', read: true },
        { id: 8, mine: true, toAuthor: true, text: '«Как долго вы делали этот отчет?»', time: '20:42', read: true },
      ],
      // Личный чат с админом (1:1) — без подписей автора и ролей.
      directMessages: [
        { id: 1, mine: false, text: 'Здравствуйте! Чем могу помочь?', time: '16:30' },
        { id: 2, mine: true, text: 'Здравствуйте! Не получается оплатить подписку', time: '16:31', read: true },
        { id: 3, mine: false, text: 'Подскажите, пожалуйста, какой способ оплаты выбираете?', time: '16:31' },
        { id: 4, mine: true, text: 'СБП', time: '16:32', read: true },
        { id: 5, mine: false, text: 'Что интересует?', time: '16:32' },
      ],
    }
  },
  computed: {
    isDirect() {
      return this.$route.params.id === 'admin'
    },
    chat() {
      return this.isDirect
        ? { title: 'Игорь Гломозда', subtitle: 'Был в сети 1 час назад' }
        : { title: 'Нефть 12.12.27', subtitle: '15 участников' }
    },
    messages() {
      return this.isDirect ? this.directMessages : this.groupMessages
    },
    menuMessage() {
      return this.messages.find((m) => m.id === this.menuFor) || null
    },
    showSend() {
      return this.draft.trim().length > 0 || !!this.replyTo
    },
    showMentions() {
      return this.draft.includes('@')
    },
  },
  methods: {
    openInfo() {
      this.$router.push({ name: 'chat-info', params: { id: this.$route.params.id } })
    },
    goUser() {
      this.$router.push({ name: 'chat-user', params: { id: this.$route.params.id, uid: 'artem' } })
    },
    onBubble(m) {
      if (this.selectMode) {
        this.toggleSelect(m.id)
      } else if (m.image) {
        this.photoViewer = true
      } else {
        this.menuFor = m.id
      }
    },
    pickMention(u) {
      this.draft = this.draft.replace(/@\S*$/, u.handle + ' ')
    },
    startRec() {
      this.recState = 'recording'
    },
    lockRec() {
      this.recState = 'locked'
    },
    pauseRec() {
      this.recState = 'paused'
    },
    cancelRec() {
      this.recState = 'idle'
    },
    sendRec() {
      this.recState = 'idle'
    },
    closeMenu() {
      this.menuFor = null
    },
    doReply() {
      this.replyTo = this.menuMessage
      this.closeMenu()
    },
    cancelReply() {
      this.replyTo = null
    },
    enterSelect() {
      this.selectMode = true
      if (this.menuFor) this.selected = [this.menuFor]
      this.closeMenu()
    },
    exitSelect() {
      this.selectMode = false
      this.selected = []
    },
    toggleSelect(id) {
      const i = this.selected.indexOf(id)
      if (i === -1) this.selected.push(id)
      else this.selected.splice(i, 1)
    },
    isSelected(id) {
      return this.selected.includes(id)
    },
    send() {
      if (!this.showSend) return
      this.draft = ''
      this.replyTo = null
    },
  },
}
</script>

<template>
  <section class="chat">
    <!-- Шапка: обычная или режим выбора -->
    <header v-if="!selectMode" class="chat__header">
      <BackButton :to="{ name: 'community' }" />
      <button type="button" class="chat__title-pill" @click="openInfo">
        <span class="chat__title">{{ chat.title }}</span>
        <span class="chat__subtitle">{{ chat.subtitle }}</span>
      </button>
      <button type="button" class="chat__avatar" aria-label="Информация о чате" @click="openInfo"></button>
    </header>
    <header v-else class="chat__select-bar">
      <span class="chat__pill">Выбрано {{ selected.length }}</span>
      <button type="button" class="chat__pill chat__pill--btn" @click="exitSelect">Отмена</button>
    </header>

    <!-- Закреплённое сообщение -->
    <div v-if="pinned && !selectMode && !isDirect" class="chat__pinned">
      <div class="chat__pinned-body">
        <span class="chat__pinned-title">{{ pinned.title }}</span>
        <span class="chat__pinned-text">{{ pinned.text }}</span>
      </div>
      <button type="button" class="chat__pinned-x" aria-label="Открепить" @click="pinned = null"><IconClose /></button>
    </div>

    <!-- Лента сообщений -->
    <div class="chat__scroll">
      <div class="chat__day"><span class="chat__day-pill">Сегодня</span></div>

      <div
        v-for="m in messages"
        :key="m.id"
        class="chat__row"
        :class="{ 'chat__row--mine': m.mine, 'chat__row--select': selectMode }"
      >
        <span
          v-if="selectMode"
          class="chat__check"
          :class="{ 'chat__check--on': isSelected(m.id) }"
          @click="toggleSelect(m.id)"
        ></span>
        <span v-if="!m.mine && !selectMode && !isDirect" class="chat__msg-avatar" @click="goUser"></span>
        <MessageBubble :message="m" @click="onBubble(m)" />
      </div>
    </div>

    <!-- Нижняя панель режима выбора -->
    <div v-if="selectMode" class="chat__select-actions">
      <button type="button" class="chat__round chat__round--danger" aria-label="Удалить"><IconTrash /></button>
      <button type="button" class="chat__round" aria-label="Переслать"><IconForward /></button>
    </div>

    <!-- Запись голосового -->
    <div v-else-if="recState !== 'idle'" class="chat__composer">
      <!-- всплывающий контрол над кнопкой -->
      <div class="chat__rec-side" :class="`chat__rec-side--${recState}`">
        <button v-if="recState === 'recording'" type="button" class="chat__rec-pill-btn" aria-label="Закрепить" @click="lockRec">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><rect x="5" y="11" width="14" height="9" rx="2" stroke-width="1.6"/><path d="M8 11V8a4 4 0 0 1 8 0" stroke-width="1.6" stroke-linecap="round"/></svg>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true"><path d="M12 19V5M6 11l6-6 6 6" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
        <button v-else-if="recState === 'locked'" type="button" class="chat__rec-pill-btn chat__rec-pill-btn--single" aria-label="Пауза" @click="pauseRec">
          <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="7" y="5" width="3.5" height="14" rx="1"/><rect x="13.5" y="5" width="3.5" height="14" rx="1"/></svg>
        </button>
        <button v-else type="button" class="chat__rec-pill-btn chat__rec-pill-btn--single" aria-label="Запись" @click="lockRec"><IconMic /></button>
      </div>

      <div class="chat__rec-bar">
        <template v-if="recState === 'paused'">
          <button type="button" class="chat__round chat__round--danger chat__rec-trash" aria-label="Удалить" @click="cancelRec"><IconTrash /></button>
          <div class="chat__rec-preview">
            <span class="chat__wave-mini">
              <span v-for="i in 34" :key="i" class="chat__bar-mini" :style="{ height: (4 + (i * 37 % 15)) + 'px' }"></span>
            </span>
            <span class="chat__rec-play"><svg viewBox="0 0 24 24" fill="currentColor"><path d="M8 5.5v13a1 1 0 0 0 1.5.9l10-6.5a1 1 0 0 0 0-1.7l-10-6.5A1 1 0 0 0 8 5.5z"/></svg></span>
            <span class="chat__rec-time">1:30</span>
          </div>
          <button type="button" class="chat__send" aria-label="Отправить" @click="sendRec">
            <svg viewBox="0 0 24 24" fill="none" stroke="#fff"><path d="M5 12h13M12 5l7 7-7 7" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
          </button>
        </template>
        <template v-else>
          <div class="chat__rec-status">
            <span class="chat__rec-dot"></span>
            <span class="chat__rec-timer">{{ recTime }}</span>
            <span class="chat__rec-hint">{{ recState === 'recording' ? '‹ Влево — отмена' : 'Отмена' }}</span>
          </div>
          <button type="button" class="chat__rec-mic" :aria-label="recState === 'locked' ? 'Отправить' : 'Запись'" @click="recState === 'locked' ? sendRec() : lockRec()">
            <svg v-if="recState === 'locked'" viewBox="0 0 24 24" fill="none" stroke="#fff"><path d="M12 19V5M6 11l6-6 6 6" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <IconMic v-else />
          </button>
        </template>
      </div>
    </div>

    <!-- Ввод -->
    <div v-else class="chat__composer">
      <!-- всплывающее меню вложений -->
      <div v-if="attachOpen" class="chat__attach">
        <button type="button" class="chat__attach-item" @click="attachOpen = false">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><rect x="3" y="4" width="18" height="16" rx="3" stroke-width="1.6"/><circle cx="9" cy="10" r="2" stroke-width="1.6"/><path d="M5 18l5-4 4 3 3-2 2 2" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
          Изображение
        </button>
        <button type="button" class="chat__attach-item" @click="attachOpen = false">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"><path d="M7 3h7l5 5v13H7z" stroke-width="1.6" stroke-linejoin="round"/><path d="M14 3v5h5" stroke-width="1.6" stroke-linejoin="round"/></svg>
          Файл
        </button>
      </div>

      <!-- подсказки упоминаний -->
      <div v-if="showMentions" class="chat__mentions">
        <button v-for="u in mentionList" :key="u.handle" type="button" class="chat__mention" @click="pickMention(u)">
          <span class="chat__mention-av"></span>
          <span class="chat__mention-name">{{ u.name }}</span>
          <span class="chat__mention-handle">{{ u.handle }}</span>
        </button>
      </div>

      <div v-if="replyTo" class="chat__reply-strip">
        <IconReply class="chat__reply-ic" />
        <div class="chat__reply-body">
          <span class="chat__reply-author">{{ replyTo.author || 'Вы' }}</span>
          <span class="chat__reply-text">{{ replyTo.text || 'Сообщение' }}</span>
        </div>
        <button type="button" class="chat__reply-x" aria-label="Отменить" @click="cancelReply"><IconClose /></button>
      </div>

      <div class="chat__input-bar">
        <button type="button" class="chat__plus" :class="{ 'chat__plus--open': attachOpen }" aria-label="Вложение" @click="attachOpen = !attachOpen"><IconPlus /></button>
        <div class="chat__input">
          <input v-model="draft" type="text" placeholder="Напишите что-нибудь" @keyup.enter="send" />
        </div>
        <button v-if="showSend" type="button" class="chat__send" aria-label="Отправить" @click="send">
          <svg viewBox="0 0 24 24" fill="none" stroke="#fff" aria-hidden="true">
            <path d="M5 12h13M12 5l7 7-7 7" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
        <button v-else type="button" class="chat__mic" aria-label="Голосовое сообщение" @click="startRec"><IconMic /></button>
      </div>
    </div>

    <!-- Просмотр фото -->
    <div v-if="photoViewer" class="viewer">
      <header class="viewer__top">
        <button type="button" class="viewer__back" aria-label="Назад" @click="photoViewer = false"><IconBack /></button>
        <div class="viewer__title">
          <span class="viewer__name">Вы</span>
          <span class="viewer__date">Сегодня в 1:42</span>
        </div>
        <button type="button" class="viewer__more" aria-label="Ещё">
          <svg viewBox="0 0 24 24" fill="currentColor"><circle cx="5" cy="12" r="2"/><circle cx="12" cy="12" r="2"/><circle cx="19" cy="12" r="2"/></svg>
        </button>
      </header>
      <div class="viewer__img"></div>
      <div class="viewer__actions">
        <button type="button" class="chat__round" aria-label="Переслать"><IconForward /></button>
        <button type="button" class="chat__round chat__round--danger" aria-label="Удалить"><IconTrash /></button>
      </div>
    </div>

    <!-- Контекстное меню сообщения -->
    <div v-if="menuFor" class="menu-root" @click.self="closeMenu">
      <div class="menu-wrap">
        <div class="menu-reactions">
          <span v-for="(r, i) in reactions" :key="i" class="menu-reaction">{{ r }}</span>
        </div>
        <div class="menu-msg">
          <MessageBubble :message="menuMessage" />
        </div>
        <div class="menu-card">
          <button type="button" class="menu-item" @click="doReply"><IconReply /> Ответить</button>
          <button type="button" class="menu-item" @click="closeMenu"><IconCopy /> Скопировать</button>
          <button type="button" class="menu-item" @click="closeMenu"><IconPin /> Закрепить</button>
          <button type="button" class="menu-item" @click="closeMenu"><IconLink /> Копировать ссылку</button>
          <button type="button" class="menu-item" @click="closeMenu"><IconReport /> Пожаловаться</button>
          <button type="button" class="menu-item menu-item--danger" @click="closeMenu"><IconTrash /> Удалить</button>
          <div class="menu-sep"></div>
          <button type="button" class="menu-item" @click="enterSelect"><IconCheckSquare /> Выбрать</button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: var(--app-height);
  background: radial-gradient(90% 60% at 50% 0%, rgba(70, 55, 70, 0.4), transparent 70%), #1a1b1d;
}

/* --- Шапка --- */
.chat__header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: calc(env(safe-area-inset-top, 0px) + 12px) var(--space-screen-x) 12px;
}
.chat__title-pill {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px 16px;
  border-radius: var(--radius-pill);
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: var(--color-text);
}
.chat__title { font-size: var(--font-size-md); font-weight: 700; }
.chat__subtitle { font-size: var(--font-size-sm); color: var(--color-text-secondary); }
.chat__avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  flex-shrink: 0;
  background: linear-gradient(135deg, #cfc6d6 0%, #a9adbd 40%, #cdbfc9 70%, #b7c2bf 100%);
}

.chat__select-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: calc(env(safe-area-inset-top, 0px) + 12px) var(--space-screen-x) 12px;
}
.chat__pill {
  padding: 10px 18px;
  border-radius: var(--radius-pill);
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-size: var(--font-size-base);
  color: var(--color-text);
}

/* --- Лента --- */
.chat__scroll {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px var(--space-screen-x) 16px;
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='60' height='60' viewBox='0 0 60 60'><path d='M30 20 L38 34 L22 34 Z' fill='none' stroke='rgba(255,255,255,0.03)' stroke-width='1.5'/></svg>");
}
.chat__day { display: flex; justify-content: center; margin: 8px 0; }
.chat__day-pill {
  padding: 4px 14px;
  border-radius: var(--radius-pill);
  background: var(--color-surface-muted);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.chat__row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  max-width: 88%;
}
.chat__row--mine { align-self: flex-end; justify-content: flex-end; }
.chat__row--select { max-width: 100%; width: 100%; align-items: center; }

.chat__msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #8a8a8a;
  flex-shrink: 0;
}

.chat__check {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.4);
  flex-shrink: 0;
  position: relative;
}
.chat__check--on {
  background: #5bb318;
  border-color: #5bb318;
}
.chat__check--on::after {
  content: '';
  position: absolute;
  left: 7px;
  top: 3px;
  width: 6px;
  height: 11px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}

/* --- Нижняя панель выбора --- */
.chat__select-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px var(--space-screen-x) calc(env(safe-area-inset-bottom, 0px) + 14px);
}
.chat__round {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: var(--color-text);
}
.chat__round svg { width: 22px; height: 22px; }
.chat__round--danger { color: var(--color-error); }

/* --- Ввод --- */
.chat__composer {
  padding: 10px var(--space-screen-x) calc(env(safe-area-inset-bottom, 0px) + 14px);
}
.chat__reply-strip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin-bottom: 8px;
  border-radius: 14px;
  background: var(--color-surface-muted);
}
.chat__reply-ic { width: 18px; height: 18px; color: var(--color-text-secondary); flex-shrink: 0; }
.chat__reply-body { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.chat__reply-author { font-size: var(--font-size-sm); font-weight: 700; }
.chat__reply-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.chat__reply-x { display: flex; color: var(--color-text-secondary); flex-shrink: 0; }
.chat__reply-x svg { width: 18px; height: 18px; }

.chat__input-bar { display: flex; align-items: center; gap: 10px; }
.chat__plus, .chat__mic, .chat__send {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  flex-shrink: 0;
}
.chat__plus, .chat__mic {
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: var(--color-text);
}
.chat__plus svg, .chat__mic svg { width: 22px; height: 22px; }
.chat__send { background: #4e8f10; }
.chat__send svg { width: 22px; height: 22px; }

.chat__input {
  flex: 1;
  height: 48px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  border-radius: var(--radius-pill);
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.chat__input input {
  width: 100%;
  background: transparent;
  border: none;
  outline: none;
  color: var(--color-text);
  font-size: var(--font-size-base);
}
.chat__input input::placeholder { color: var(--color-text-muted); }

/* --- Контекстное меню --- */
.menu-root {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 var(--space-screen-x);
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(2px);
  -webkit-backdrop-filter: blur(2px);
}
.menu-wrap {
  width: 100%;
  max-width: 340px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.menu-reactions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: var(--radius-pill);
  background: #2c2d30;
}
.menu-reaction { font-size: 22px; }
.menu-msg { display: flex; }
.menu-card {
  display: flex;
  flex-direction: column;
  padding: 6px;
  border-radius: 18px;
  background: #2c2d30;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 12px;
  border-radius: 10px;
  color: var(--color-text);
  font-size: var(--font-size-md);
  text-align: left;
}
.menu-item svg { width: 20px; height: 20px; }
.menu-item--danger { color: var(--color-error); }
.menu-sep { height: 1px; background: rgba(255, 255, 255, 0.1); margin: 4px 8px; }

/* --- Закреплённое сообщение --- */
.chat__pinned {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 var(--space-screen-x);
  padding: 10px 14px;
  border-radius: 14px;
  background: var(--color-surface);
  border-left: 3px solid #7bc043;
}
.chat__pinned-body { display: flex; flex-direction: column; flex: 1; min-width: 0; }
.chat__pinned-title { font-size: var(--font-size-sm); font-weight: 700; color: #9ed06b; }
.chat__pinned-text {
  font-size: var(--font-size-base);
  color: var(--color-text-secondary);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.chat__pinned-x { display: flex; color: var(--color-text-secondary); flex-shrink: 0; }
.chat__pinned-x svg { width: 20px; height: 20px; }

/* --- Вложения --- */
.chat__attach {
  display: flex;
  flex-direction: column;
  width: max-content;
  margin-bottom: 10px;
  padding: 6px;
  border-radius: 16px;
  background: #2c2d30;
}
.chat__attach-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px 10px 10px;
  border-radius: 10px;
  color: var(--color-text);
  font-size: var(--font-size-md);
}
.chat__attach-item svg { width: 22px; height: 22px; }
.chat__plus--open { transform: rotate(45deg); transition: transform 0.15s ease; }

/* --- Упоминания --- */
.chat__mentions {
  display: flex;
  flex-direction: column;
  margin-bottom: 10px;
  border-radius: 16px;
  background: #2c2d30;
  overflow: hidden;
}
.chat__mention {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  color: var(--color-text);
}
.chat__mention:last-child { border-bottom: none; }
.chat__mention-av { width: 28px; height: 28px; border-radius: 50%; background: #8a8a8a; flex-shrink: 0; }
.chat__mention-name { font-size: var(--font-size-base); font-weight: 700; }
.chat__mention-handle { font-size: var(--font-size-base); color: var(--color-text-secondary); }

/* --- Запись голосового --- */
.chat__rec-side {
  position: absolute;
  right: calc(var(--space-screen-x) + 4px);
  bottom: 78px;
}
.chat__rec-pill-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  width: 40px;
  border-radius: var(--radius-pill);
  background: var(--color-surface);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: var(--color-text);
}
.chat__rec-pill-btn--single { padding: 9px 0; }
.chat__rec-pill-btn svg { width: 20px; height: 20px; }

.chat__rec-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.chat__rec-status {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 16px;
  border-radius: var(--radius-pill);
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.chat__rec-dot { width: 9px; height: 9px; border-radius: 50%; background: #e23b3b; flex-shrink: 0; }
.chat__rec-timer { font-size: var(--font-size-base); }
.chat__rec-hint { font-size: var(--font-size-base); color: var(--color-text-secondary); }
.chat__rec-mic {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: 50%;
  flex-shrink: 0;
  color: #fff;
  background:
    radial-gradient(circle at 50% 45%, #6bbf2a 0%, #4e8f10 55%, rgba(78,143,16,0.35) 72%, transparent 73%);
}
.chat__rec-mic svg { width: 26px; height: 26px; }

.chat__rec-trash { flex-shrink: 0; }
.chat__rec-preview {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 14px;
  border-radius: var(--radius-pill);
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.chat__wave-mini { display: flex; align-items: center; gap: 2px; flex: 1; height: 22px; }
.chat__bar-mini { width: 2px; border-radius: 2px; background: rgba(255,255,255,0.5); }
.chat__rec-play {
  display: flex; align-items: center; justify-content: center;
  width: 26px; height: 26px; color: var(--color-text); flex-shrink: 0;
}
.chat__rec-play svg { width: 18px; height: 18px; }
.chat__rec-time { font-size: var(--font-size-sm); color: var(--color-text-secondary); }

/* --- Просмотр фото --- */
.viewer {
  position: fixed;
  inset: 0;
  z-index: 250;
  display: flex;
  flex-direction: column;
  background: #000;
}
.viewer__top {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: calc(env(safe-area-inset-top, 0px) + 12px) var(--space-screen-x) 12px;
}
.viewer__back, .viewer__more {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--color-surface-muted);
  color: var(--color-text);
  flex-shrink: 0;
}
.viewer__back svg, .viewer__more svg { width: 22px; height: 22px; }
.viewer__title { flex: 1; display: flex; flex-direction: column; align-items: center; }
.viewer__name { font-size: var(--font-size-md); font-weight: 700; }
.viewer__date { font-size: var(--font-size-sm); color: var(--color-text-secondary); }
.viewer__img { flex: 1; background: #d4d4d4; }
.viewer__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px var(--space-screen-x) calc(env(safe-area-inset-bottom, 0px) + 16px);
}
</style>
