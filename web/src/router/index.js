import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../components/Layout.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '监控大盘' } },
      { path: 'clusters', name: 'Clusters', component: () => import('../views/ClusterList.vue'), meta: { title: '集群管理' } },
      { path: 'clusters/add', name: 'ClusterAdd', component: () => import('../views/ClusterForm.vue'), meta: { title: '新增集群' } },
      { path: 'clusters/:id/edit', name: 'ClusterEdit', component: () => import('../views/ClusterForm.vue'), meta: { title: '编辑集群' } },
      { path: 'tasks', name: 'Tasks', component: () => import('../views/TaskList.vue'), meta: { title: '迁移任务' } },
      { path: 'task-instances', name: 'TaskInstances', component: () => import('../views/TaskInstanceList.vue'), meta: { title: '任务实例' } },
      { path: 'tasks/add', name: 'TaskAdd', component: () => import('../views/TaskForm.vue'), meta: { title: '新建任务' } },
      { path: 'tasks/:id/edit', name: 'TaskEdit', component: () => import('../views/TaskForm.vue'), meta: { title: '编辑任务' } },
      { path: 'agents', name: 'Agents', component: () => import('../views/AgentList.vue'), meta: { title: 'Agent管理' } },
      { path: 'verify', name: 'Verify', component: () => import('../views/VerifyResult.vue'), meta: { title: '校验结果' } },
      { path: 'users', name: 'Users', component: () => import('../views/UserList.vue'), meta: { title: '用户管理' } },
      { path: 'login-logs', name: 'LoginLogs', component: () => import('../views/LoginLogList.vue'), meta: { title: '登录日志' } },
      { path: 'ai-chat', name: 'AiChat', component: () => import('../views/AiChat.vue'), meta: { title: 'AI 助手' } },
      { path: 'ai-config', name: 'AiConfig', component: () => import('../views/AiConfig.vue'), meta: { title: 'AI 模型配置' } },
      { path: 'alert-config', name: 'AlertConfig', component: () => import('../views/AlertConfig.vue'), meta: { title: '告警配置' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const isPublic = to.meta.public === true || to.path === '/login'
  const token = localStorage.getItem('token')
  if (!isPublic && !token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.path === '/login' && token) {
    next('/')
  } else if ((to.path === '/users' || to.path === '/login-logs' || to.path === '/alert-config') && localStorage.getItem('role') !== 'admin') {
    next('/dashboard')
  } else {
    next()
  }
})

export default router