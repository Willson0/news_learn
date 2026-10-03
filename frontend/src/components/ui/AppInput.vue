<script>
import IconEye from '@/components/icons/IconEye.vue'
import IconEyeClosed from '@/components/icons/IconEyeClosed.vue'

export default {
  name: 'AppInput',
  components: { IconEye, IconEyeClosed },
  props: {
    modelValue: { type: String, default: '' },
    type: { type: String, default: 'text' }, // text | password | email | tel
    placeholder: { type: String, default: '' },
    error: { type: Boolean, default: false }, // подсветить поле как ошибочное
    autocomplete: { type: String, default: 'off' },
    inputmode: { type: String, default: null },
  },
  emits: ['update:modelValue'],
  data() {
    return {
      revealed: false, // показан ли пароль
    }
  },
  computed: {
    isPassword() {
      return this.type === 'password'
    },
    resolvedType() {
      if (!this.isPassword) return this.type
      return this.revealed ? 'text' : 'password'
    },
  },
  methods: {
    onInput(e) {
      this.$emit('update:modelValue', e.target.value)
    },
    toggleReveal() {
      this.revealed = !this.revealed
    },
  },
}
</script>

<template>
  <div class="app-input" :class="{ 'app-input--error': error }">
    <input
      class="app-input__field"
      :type="resolvedType"
      :value="modelValue"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      :inputmode="inputmode"
      @input="onInput"
    />
    <button
      v-if="isPassword"
      type="button"
      class="app-input__eye"
      :aria-label="revealed ? 'Скрыть пароль' : 'Показать пароль'"
      @click="toggleReveal"
    >
      <IconEye v-if="revealed" />
      <IconEyeClosed v-else />
    </button>
  </div>
</template>

<style scoped>
.app-input {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 48px;
  padding: 0 16px;
  border-radius: var(--radius-input);
  background: var(--color-surface-muted);
  border: 1px solid transparent;
}

.app-input--error .app-input__field {
  color: #ff4d4d;
}
.app-input--error .app-input__field::placeholder {
  color: #ff4d4d;
}

.app-input__field {
  flex: 1;
  min-width: 0;
  height: 100%;
  background: transparent;
  border: none;
  outline: none;
  color: var(--color-text);
  font-size: var(--font-size-base);
}

.app-input__field::placeholder {
  color: var(--color-text-muted);
}

.app-input__eye {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  color: var(--color-text);
  flex-shrink: 0;
}

.app-input__eye svg {
  width: 24px;
  height: 24px;
}
</style>
