import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

// Naive UI
import {
  create,
  NButton,
  NInput,
  NIcon,
  NLayout,
  NLayoutHeader,
  NLayoutContent,
  NTabs,
  NTabPane,
  NCard,
  NSpin,
  NEmpty,
  NModal,
  NForm,
  NFormItem,
  NSpace,
  NMessageProvider,
  useMessage,
  NSwitch
} from 'naive-ui'

const naive = create({
  components: [
    NButton,
    NInput,
    NIcon,
    NLayout,
    NLayoutHeader,
    NLayoutContent,
    NTabs,
    NTabPane,
    NCard,
    NSpin,
    NEmpty,
    NModal,
    NForm,
    NFormItem,
    NSpace,
    NMessageProvider,
    NSwitch
  ]
})

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(naive)

app.mount('#app')
