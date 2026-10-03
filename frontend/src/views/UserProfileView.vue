<script>
import IconBack from '@/components/icons/IconBack.vue'

export default {
  name: 'UserProfileView',
  components: { IconBack },
  data() {
    return {
      user: { name: 'Артем', tag: '@AlexeyRub', registered: '14.09.2026 (7 дней)' },
    }
  },
}
</script>

<template>
  <section class="uprofile">
    <div class="uprofile__bg"></div>

    <button type="button" class="uprofile__back" aria-label="Назад" @click="$router.back()">
      <IconBack />
    </button>

    <div class="uprofile__center">
      <p class="uprofile__status">Зарегистрирован</p>
      <p class="uprofile__date">{{ user.registered }}</p>
      <div class="uprofile__avatar"></div>
      <p class="uprofile__name">{{ user.name }}</p>
      <p class="uprofile__tag">{{ user.tag }}</p>
    </div>
  </section>
</template>

<style scoped>
.uprofile {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-height: var(--app-height);
  overflow: hidden;
  background: #1a1b1d;
}

/* Размытый фон из фото пользователя (приближение градиентом) */
.uprofile__bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(60% 40% at 50% 55%, rgba(120, 130, 90, 0.5), transparent 70%),
    linear-gradient(180deg, #4a5a54 0%, #2a2d2e 45%, #1a1b1d 100%);
  filter: blur(2px);
}

.uprofile__back {
  position: absolute;
  top: calc(env(safe-area-inset-top, 0px) + 16px);
  left: var(--space-screen-x);
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--color-surface-muted);
  color: var(--color-text);
}
.uprofile__back svg { width: 22px; height: 22px; }

.uprofile__center {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 0 var(--space-screen-x);
}
.uprofile__status { font-size: var(--font-size-title); font-weight: 700; }
.uprofile__date { font-size: var(--font-size-base); color: var(--color-text-secondary); margin-bottom: 20px; }
.uprofile__avatar {
  width: 160px;
  height: 160px;
  border-radius: 50%;
  margin-bottom: 14px;
  background:
    radial-gradient(60% 60% at 40% 35%, #9aa86a, transparent 70%),
    linear-gradient(135deg, #6b7350 0%, #3f4a2c 100%);
}
.uprofile__name { font-size: var(--font-size-title); font-weight: 700; }
.uprofile__tag { font-size: var(--font-size-md); color: var(--color-text-secondary); }
</style>
