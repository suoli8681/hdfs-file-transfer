import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import * as utils from './utils'

const app = createApp(App)
app.use(ElementPlus)
app.use(router)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.config.globalProperties.$statusType = utils.statusType
app.config.globalProperties.$statusLabel = utils.statusLabel
app.config.globalProperties.$formatSize = utils.formatSize
app.mount('#app')