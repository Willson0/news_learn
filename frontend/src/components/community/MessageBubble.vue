<script>
import IconPlay from '@/components/icons/IconPlay.vue'
import IconDownloadCloud from '@/components/icons/IconDownloadCloud.vue'

export default {
  name: 'MessageBubble',
  components: { IconPlay, IconDownloadCloud },
  props: {
    message: { type: Object, required: true },
    // message: { mine, author, role, type, text, time, read, reply, file, voice, image }
  },
  computed: {
    bars() {
      // Псевдослучайная «волна» для голосового (детерминированная по id)
      const seed = this.message.id || 1
      return Array.from({ length: 26 }, (_, i) => {
        const v = Math.abs(Math.sin(seed * 7.3 + i * 1.7))
        return 4 + Math.round(v * 14)
      })
    },
  },
}
</script>

<template>
  <div class="bubble" :class="message.mine ? 'bubble--mine' : 'bubble--other'">
    <div v-if="!message.mine && message.author" class="bubble__head">
      <span class="bubble__author">{{ message.author }}</span>
      <span v-if="message.role" class="bubble__role">{{ message.role }}</span>
    </div>

    <!-- Сообщение автору -->
    <div v-if="message.toAuthor" class="bubble__to-author">Автору</div>

    <!-- Цитата (ответ) -->
    <div v-if="message.reply" class="bubble__quote">
      <span class="bubble__quote-author">{{ message.reply.author }}</span>
      <span class="bubble__quote-text">{{ message.reply.text }}</span>
    </div>

    <!-- Изображение -->
    <div v-if="message.image" class="bubble__image"></div>

    <!-- Файл -->
    <div v-if="message.file" class="bubble__file">
      <span class="bubble__file-badge">{{ message.file.ext }}</span>
      <span class="bubble__file-info">
        <span class="bubble__file-name">{{ message.file.name }}</span>
        <span class="bubble__file-size">{{ message.file.size }}</span>
      </span>
      <IconDownloadCloud class="bubble__file-dl" />
    </div>

    <!-- Голосовое -->
    <div v-if="message.voice" class="bubble__voice">
      <span class="bubble__play"><IconPlay /></span>
      <span class="bubble__wave">
        <span v-for="(h, i) in bars" :key="i" class="bubble__bar" :style="{ height: h + 'px' }"></span>
      </span>
      <span class="bubble__voice-dur">{{ message.voice.duration }}</span>
    </div>

    <!-- Текст -->
    <p v-if="message.text" class="bubble__text">{{ message.text }}</p>

    <!-- Реакции -->
    <div v-if="message.reactions && message.reactions.length" class="bubble__reactions">
      <span v-for="(r, i) in message.reactions" :key="i" class="bubble__reaction">
        {{ r.emoji }} <small>{{ r.count }}</small>
      </span>
    </div>

    <span v-if="message.time" class="bubble__meta">
      {{ message.time }}
      <svg v-if="message.mine" class="bubble__read" viewBox="0 0 20 12" aria-hidden="true">
        <rect x="1" y="1" width="18" height="10" rx="5" fill="rgba(255,255,255,0.25)" />
        <circle cx="13" cy="6" r="4" fill="#fff" />
      </svg>
    </span>
  </div>
</template>

<style scoped>
.bubble {
  max-width: 300px;
  padding: 10px 14px;
  border-radius: 18px;
  font-size: var(--font-size-base);
  line-height: 1.35;
}
.bubble--other {
  background: var(--color-surface);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-bottom-left-radius: 6px;
}
.bubble--mine {
  background: var(--gradient-accent);
  border-bottom-right-radius: 6px;
}

.bubble__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 4px;
}
.bubble__author {
  font-size: var(--font-size-base);
  font-weight: 700;
}
.bubble__role {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.bubble__quote {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 6px;
  padding: 6px 10px;
  border-left: 3px solid rgba(255, 255, 255, 0.5);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.18);
}
.bubble__quote-author {
  font-size: var(--font-size-sm);
  font-weight: 700;
}
.bubble__quote-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 230px;
}

.bubble__image {
  width: 240px;
  height: 150px;
  border-radius: 12px;
  margin-bottom: 6px;
  background: linear-gradient(135deg, #cfc6d6, #a9adbd 50%, #b7c2bf);
}

.bubble__file {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 6px 0;
}
.bubble__file-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 44px;
  border-radius: 8px;
  background: #fff;
  color: #1a1b1d;
  font-size: var(--font-size-sm);
  font-weight: 700;
  flex-shrink: 0;
}
.bubble__file-info {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}
.bubble__file-name {
  font-size: var(--font-size-base);
}
.bubble__file-size {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
.bubble__file-dl {
  width: 22px;
  height: 22px;
  flex-shrink: 0;
}

.bubble__voice {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 2px 0;
}
.bubble__play {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #4e8f10;
  color: #fff;
  flex-shrink: 0;
}
.bubble__play svg {
  width: 18px;
  height: 18px;
  margin-left: 2px;
}
.bubble__wave {
  display: flex;
  align-items: center;
  gap: 2px;
  flex: 1;
  height: 24px;
}
.bubble__bar {
  width: 2px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.55);
}
.bubble__voice-dur {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.bubble__to-author {
  font-size: var(--font-size-base);
  font-weight: 700;
  margin-bottom: 4px;
}

.bubble__text {
  color: var(--color-text);
}

.bubble__reactions {
  display: flex;
  gap: 6px;
  margin-top: 6px;
}
.bubble__reaction {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  background: rgba(0, 0, 0, 0.25);
  font-size: var(--font-size-sm);
}

.bubble__meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin-top: 4px;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
.bubble__read {
  width: 20px;
  height: 12px;
}
</style>
