import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'

import App from './App.vue'
import { router } from './router'
import { vPermission } from './directives/permission'
import {
  registerMessageShower,
  registerPasswordChangeRedirect,
  registerPermissionRefresher,
} from './api/request'
import { useAuthStore } from './stores/auth'
import { usePermissionStore } from './stores/permission'

import './styles/tokens.scss'
import './styles/global.scss'
import './styles/element-override.scss'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.directive('permission', vPermission)

// 请求层通过注入方式使用 UI 提示，避免底层直接耦合组件库
registerMessageShower((msg, type) => {
  if (type === 'warning') ElMessage.warning(msg)
  else ElMessage.error(msg)
})
registerPermissionRefresher(() => {
  void usePermissionStore().fetch().catch(() => {
    /* 403 后的权限同步失败不覆盖原始业务错误 */
  })
})
registerPasswordChangeRedirect(() => {
  const auth = useAuthStore()
  auth.markRestricted()
  if (router.currentRoute.value.path !== '/change-password') {
    void router.replace('/change-password')
  }
})

app.mount('#app')
