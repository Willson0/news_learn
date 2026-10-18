<script>
import IconBack from '@/components/icons/IconBack.vue'
import { fetchUser } from '@/api/resources'
import { fileUrl } from '@/api/client'

export default {
  name: 'UserProfileView',
  components: { IconBack },
  data() {
    return {
      user: null,
      loading: true,
    }
  },
  computed: {
    registered() {
      if (!this.user || !this.user.registered_at) return ''
      const days = this.user.registered_days
      if (days == null) return this.user.registered_at
      const n = Number(days)
      const word = this.plural(n, ['день', 'дня', 'дней'])
      return `${this.user.registered_at} (${n} ${word})`
    },
    backTo() {
      return { name: 'chat', params: { id: this.$route.params.id } }
    },
    avatarSrc() {
      return fileUrl(this.user && this.user.avatar_url)
    },
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      try {
        this.user = await fetchUser(this.$route.params.uid)
      } catch {
        this.user = null
      } finally {
        this.loading = false
      }
    },
    plural(n, forms) {
      const a = Math.abs(n) % 100
      const b = a % 10
      if (a > 10 && a < 20) return forms[2]
      if (b > 1 && b < 5) return forms[1]
      if (b === 1) return forms[0]
      return forms[2]
    },
  },
}
</script>

<template>
  <section class="uprofile">
    <div class="uprofile__bg"></div>

    <router-link :to="backTo" class="uprofile__back" aria-label="Назад"><IconBack /></router-link>

    <div v-if="user" class="uprofile__center">
      <p class="uprofile__status">Зарегистрирован</p>
      <p class="uprofile__date">{{ registered }}</p>
      <img v-if="user.avatar_url" class="uprofile__avatar" :src="avatarSrc" alt="" />
      <div v-else class="uprofile__avatar"></div>
      <p class="uprofile__name">{{ user.name }}</p>
      <p v-if="user.username" class="uprofile__tag">{{ user.username }}</p>
      <p v-if="user.seen" class="uprofile__seen">{{ user.seen }}</p>
    </div>
    <div v-else-if="!loading" class="uprofile__center">
      <p class="uprofile__status">Пользователь не найден</p>
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
  object-fit: cover;
  background:
    radial-gradient(60% 60% at 40% 35%, #9aa86a, transparent 70%),
    linear-gradient(135deg, #6b7350 0%, #3f4a2c 100%);
}
.uprofile__name { font-size: var(--font-size-title); font-weight: 700; }
.uprofile__tag { font-size: var(--font-size-md); color: var(--color-text-secondary); }
.uprofile__seen { font-size: var(--font-size-sm); color: var(--color-text-muted); margin-top: 4px; }
</style>
