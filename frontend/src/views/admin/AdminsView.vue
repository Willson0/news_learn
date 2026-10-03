<script>
import BackButton from '@/components/ui/BackButton.vue'
import AppInput from '@/components/ui/AppInput.vue'
import AppButton from '@/components/ui/AppButton.vue'
import ConfirmDialog from '@/components/admin/ConfirmDialog.vue'
import { fetchAdmins, addAdmin, removeAdmin } from '@/api/resources'
import { session, loadSession } from '@/api/session'
import { haptic } from '@/telegram/webapp'

/*
 * Список админов. Отдельного макета нет — экран собран из тех же компонентов,
 * что и остальные (поле ввода, кнопки-пилюли «Удалить», карточки).
 * Главного админа удалить нельзя (это же проверяет бэкенд).
 */
export default {
  name: 'AdminsView',
  components: { BackButton, AppInput, AppButton, ConfirmDialog },
  data() {
    return {
      admins: [],
      telegramId: '',
      error: '',
      adding: false,
      removing: null,
      confirmFor: null,
    }
  },
  computed: {
    myTelegramId() {
      return session.user && session.user.telegram_id
    },
  },
  mounted() {
    this.load()
  },
  methods: {
    async load() {
      try {
        this.admins = await fetchAdmins()
      } catch (err) {
        this.error = (err && err.message) || 'Не удалось загрузить список'
      }
    },
    async add() {
      const id = this.telegramId.trim()
      if (!/^\d+$/.test(id)) {
        this.error = 'Telegram ID состоит только из цифр'
        return
      }
      this.adding = true
      this.error = ''
      try {
        await addAdmin(id)
        this.telegramId = ''
        haptic('medium')
        await this.load()
      } catch (err) {
        const errors = (err && err.errors) || {}
        this.error = (errors.telegram_id && errors.telegram_id[0]) || (err && err.message) || 'Не удалось добавить'
      } finally {
        this.adding = false
      }
    },
    async confirmRemove() {
      const admin = this.confirmFor
      if (!admin) return
      this.removing = admin.telegram_id
      try {
        await removeAdmin(admin.telegram_id)
        haptic('medium')
        this.confirmFor = null
        if (admin.telegram_id === this.myTelegramId) {
          // Сняли права с себя — обновляем сессию и уходим из админки.
          await loadSession(true)
          this.$router.replace({ name: 'profile' })
          return
        }
        await this.load()
      } catch (err) {
        this.error = (err && err.message) || 'Не удалось удалить'
        this.confirmFor = null
      } finally {
        this.removing = null
      }
    },
  },
}
</script>

<template>
  <section class="admins">
    <div class="admins__top">
      <BackButton :to="{ name: 'profile' }" />
    </div>

    <h1 class="admins__title screen-title">Администраторы</h1>
    <p class="admins__hint">
      Админы видят админ-панель: создают и редактируют отчёты, настраивают чаты и отвечают в личных чатах.
      Добавить нового админа можно по его Telegram ID.
    </p>

    <div class="admins__add">
      <AppInput
        v-model="telegramId"
        placeholder="Telegram ID"
        inputmode="numeric"
        :error="Boolean(error)"
        class="admins__input"
        @update:model-value="error = ''"
      />
      <AppButton variant="accent" class="admins__add-btn" :disabled="adding" @click="add">Добавить</AppButton>
    </div>
    <p v-if="error" class="admins__error">{{ error }}</p>

    <ul class="admins__list">
      <li v-for="a in admins" :key="a.telegram_id" class="admins__row">
        <span class="admins__avatar"></span>
        <div class="admins__body">
          <span class="admins__name">
            {{ a.name || 'Ещё не заходил' }}
            <span v-if="a.telegram_id === myTelegramId" class="admins__you">(вы)</span>
          </span>
          <span class="admins__meta">
            ID {{ a.telegram_id }}<template v-if="a.username"> · {{ a.username }}</template>
          </span>
        </div>
        <span v-if="a.root" class="admins__root">Главный</span>
        <button
          v-else
          type="button"
          class="admins__remove"
          :disabled="removing === a.telegram_id"
          @click="confirmFor = a"
        >
          Удалить
        </button>
      </li>
    </ul>

    <ConfirmDialog
      :open="Boolean(confirmFor)"
      title="Удалить админа?"
      text="Он потеряет доступ к админ-панели"
      :busy="Boolean(removing)"
      @confirm="confirmRemove"
      @cancel="confirmFor = null"
    />
  </section>
</template>

<style scoped>
.admins {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: var(--app-height);
  padding: calc(env(safe-area-inset-top, 0px) + 16px) var(--space-screen-x)
    calc(env(safe-area-inset-bottom, 0px) + 32px);
  background:
    radial-gradient(120% 40% at 100% 0%, rgba(70, 40, 60, 0.35), transparent 70%),
    var(--color-bg);
}

.admins__title {
  margin: 12px 0 0;
}

.admins__hint {
  font-size: var(--font-size-base);
  line-height: 1.4;
  color: var(--color-text-secondary);
}

.admins__add {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.admins__input {
  flex: 1;
  border: 1px solid rgba(255, 255, 255, 0.12);
}
.admins__add-btn {
  width: auto;
  padding: 0 20px;
}

.admins__error {
  font-size: var(--font-size-sm);
  color: var(--color-error);
}

.admins__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.admins__row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 64px;
  padding: 10px 16px;
  border-radius: 20px;
  background: var(--color-surface);
}

.admins__avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #8a8a8a;
  flex-shrink: 0;
}

.admins__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.admins__name {
  font-size: var(--font-size-md);
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admins__you {
  font-weight: 400;
  color: var(--color-text-secondary);
}

.admins__meta {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.admins__root {
  padding: 6px 12px;
  border-radius: var(--radius-pill);
  background: var(--gradient-accent);
  font-size: var(--font-size-sm);
  flex-shrink: 0;
}

.admins__remove {
  height: 32px;
  padding: 0 12px;
  border-radius: var(--radius-pill);
  background: var(--gradient-purple-solid);
  color: var(--color-text);
  font-size: var(--font-size-base);
  flex-shrink: 0;
}
.admins__remove:disabled {
  opacity: 0.6;
}
</style>
