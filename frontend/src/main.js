import { createApp } from 'vue'

import App from './App.vue'
import router from './router'
import { initTelegram } from './telegram/webapp'
import { initNative } from './native/capacitor'
import './styles/base.css'

initTelegram()

createApp(App).use(router).mount('#app')

// Нативная оболочка Android (Capacitor). В браузере безопасно бездействует.
initNative(router)
