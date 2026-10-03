<script>
import BackButton from '@/components/ui/BackButton.vue'
import IconPlus from '@/components/icons/IconPlus.vue'
import IconMic from '@/components/icons/IconMic.vue'

export default {
  name: 'ChatView',
  components: { BackButton, IconPlus, IconMic },
  data() {
    return {
      draft: '',
      chat: {
        title: 'Нефть 12.12.27',
        subtitle: '15 участников',
      },
      // Демо-переписка. Реальные сообщения придут с бэкенда.
      days: [
        {
          label: 'Сегодня',
          messages: [
            {
              id: 1,
              mine: false,
              author: 'Артем',
              role: 'Участник',
              text: 'Здравствуте! Помогите разобраться с графиком',
            },
            {
              id: 2,
              mine: true,
              text: 'А то никак понять не могу',
              time: '20:42',
              read: true,
            },
          ],
        },
      ],
    }
  },
  methods: {
    openInfo() {
      this.$router.push({ name: 'chat-info', params: { id: this.$route.params.id } })
    },
  },
}
</script>

<template>
  <section class="chat">
    <!-- Шапка -->
    <header class="chat__header">
      <BackButton :to="{ name: 'community' }" />
      <button type="button" class="chat__title-pill" @click="openInfo">
        <span class="chat__title">{{ chat.title }}</span>
        <span class="chat__subtitle">{{ chat.subtitle }}</span>
      </button>
      <button type="button" class="chat__avatar" aria-label="Информация о чате" @click="openInfo"></button>
    </header>

    <!-- Лента сообщений -->
    <div class="chat__scroll">
      <template v-for="day in days" :key="day.label">
        <div class="chat__day"><span class="chat__day-pill">{{ day.label }}</span></div>

        <div
          v-for="m in day.messages"
          :key="m.id"
          class="chat__msg-row"
          :class="{ 'chat__msg-row--mine': m.mine }"
        >
          <span v-if="!m.mine" class="chat__msg-avatar"></span>
          <div class="chat__bubble" :class="m.mine ? 'chat__bubble--mine' : 'chat__bubble--other'">
            <div v-if="!m.mine && m.author" class="chat__bubble-head">
              <span class="chat__bubble-author">{{ m.author }}</span>
              <span v-if="m.role" class="chat__bubble-role">{{ m.role }}</span>
            </div>
            <p class="chat__bubble-text">{{ m.text }}</p>
            <span v-if="m.mine" class="chat__bubble-meta">
              {{ m.time }}
              <svg class="chat__read" viewBox="0 0 20 12" fill="none" aria-hidden="true">
                <rect x="1" y="1" width="18" height="10" rx="5" fill="rgba(255,255,255,0.25)" />
                <circle cx="13" cy="6" r="4" fill="#fff" />
              </svg>
            </span>
          </div>
        </div>
      </template>
    </div>

    <!-- Ввод -->
    <div class="chat__input-bar">
      <button type="button" class="chat__plus" aria-label="Вложение">
        <IconPlus />
      </button>
      <div class="chat__input">
        <input v-model="draft" type="text" placeholder="Напишите что-нибудь" />
      </div>
      <button type="button" class="chat__mic" aria-label="Голосовое сообщение">
        <IconMic />
      </button>
    </div>
  </section>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  height: var(--app-height);
  background:
    radial-gradient(90% 60% at 50% 0%, rgba(70, 55, 70, 0.4), transparent 70%),
    #1a1b1d;
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

.chat__title {
  font-size: var(--font-size-md);
  font-weight: 700;
}

.chat__subtitle {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.chat__avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  flex-shrink: 0;
  background:
    linear-gradient(135deg, #cfc6d6 0%, #a9adbd 40%, #cdbfc9 70%, #b7c2bf 100%);
}

/* --- Лента --- */
.chat__scroll {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px var(--space-screen-x) 16px;
  /* Водяной знак-паттерн из макета (приближение) */
  background-image: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='60' height='60' viewBox='0 0 60 60'><path d='M30 20 L38 34 L22 34 Z' fill='none' stroke='rgba(255,255,255,0.03)' stroke-width='1.5'/></svg>");
}

.chat__day {
  display: flex;
  justify-content: center;
  margin: 8px 0;
}
.chat__day-pill {
  padding: 4px 14px;
  border-radius: var(--radius-pill);
  background: var(--color-surface-muted);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.chat__msg-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  max-width: 85%;
}
.chat__msg-row--mine {
  align-self: flex-end;
  justify-content: flex-end;
}

.chat__msg-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #8a8a8a;
  flex-shrink: 0;
}

.chat__bubble {
  padding: 10px 14px;
  border-radius: 18px;
  font-size: var(--font-size-base);
  line-height: 1.35;
}

.chat__bubble--other {
  background: var(--color-surface);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-bottom-left-radius: 6px;
}

.chat__bubble--mine {
  background: var(--gradient-accent);
  border-bottom-right-radius: 6px;
}

.chat__bubble-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 2px;
}
.chat__bubble-author {
  font-size: var(--font-size-base);
  font-weight: 700;
}
.chat__bubble-role {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.chat__bubble-text {
  color: var(--color-text);
}

.chat__bubble-meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 4px;
  font-size: var(--font-size-sm);
  color: rgba(255, 255, 255, 0.8);
}
.chat__read {
  width: 20px;
  height: 12px;
}

/* --- Ввод --- */
.chat__input-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px var(--space-screen-x) calc(env(safe-area-inset-bottom, 0px) + 14px);
}

.chat__plus,
.chat__mic {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--color-surface-muted);
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: var(--color-text);
  flex-shrink: 0;
}
.chat__plus svg,
.chat__mic svg {
  width: 22px;
  height: 22px;
}

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
.chat__input input::placeholder {
  color: var(--color-text-muted);
}
</style>
