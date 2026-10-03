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

export default {
  name: 'ChatView',
  components: {
    BackButton, MessageBubble, IconPlus, IconMic, IconReply, IconCopy,
    IconPin, IconLink, IconReport, IconTrash, IconForward, IconCheckSquare, IconClose,
  },
  data() {
    return {
      draft: '',
      replyTo: null,
      menuFor: null,
      selectMode: false,
      selected: [],
      reactions: ['👍', '❤️', '🔥', '😂', '😮', '😢', '🙏', '👏'],
      chat: { title: 'Нефть 12.12.27', subtitle: '15 участников' },
      messages: [
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
      ],
    }
  },
  computed: {
    menuMessage() {
      return this.messages.find((m) => m.id === this.menuFor) || null
    },
    showSend() {
      return this.draft.trim().length > 0 || !!this.replyTo
    },
  },
  methods: {
    openInfo() {
      this.$router.push({ name: 'chat-info', params: { id: this.$route.params.id } })
    },
    onBubble(m) {
      if (this.selectMode) this.toggleSelect(m.id)
      else this.menuFor = m.id
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
        <span v-if="!m.mine && !selectMode" class="chat__msg-avatar"></span>
        <MessageBubble :message="m" @click="onBubble(m)" />
      </div>
    </div>

    <!-- Нижняя панель режима выбора -->
    <div v-if="selectMode" class="chat__select-actions">
      <button type="button" class="chat__round chat__round--danger" aria-label="Удалить"><IconTrash /></button>
      <button type="button" class="chat__round" aria-label="Переслать"><IconForward /></button>
    </div>

    <!-- Ввод -->
    <div v-else class="chat__composer">
      <div v-if="replyTo" class="chat__reply-strip">
        <IconReply class="chat__reply-ic" />
        <div class="chat__reply-body">
          <span class="chat__reply-author">{{ replyTo.author || 'Вы' }}</span>
          <span class="chat__reply-text">{{ replyTo.text || 'Сообщение' }}</span>
        </div>
        <button type="button" class="chat__reply-x" aria-label="Отменить" @click="cancelReply"><IconClose /></button>
      </div>
      <div class="chat__input-bar">
        <button type="button" class="chat__plus" aria-label="Вложение"><IconPlus /></button>
        <div class="chat__input">
          <input v-model="draft" type="text" placeholder="Напишите что-нибудь" @keyup.enter="send" />
        </div>
        <button v-if="showSend" type="button" class="chat__send" aria-label="Отправить" @click="send">
          <svg viewBox="0 0 24 24" fill="none" stroke="#fff" aria-hidden="true">
            <path d="M5 12h13M12 5l7 7-7 7" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </button>
        <button v-else type="button" class="chat__mic" aria-label="Голосовое сообщение"><IconMic /></button>
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
</style>
