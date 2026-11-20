import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '工作台' } },
      { path: 'generate/requirement', name: 'gen-requirement', component: () => import('../views/GenerateRequirement.vue'), meta: { title: '需求文档生成' } },
      { path: 'generate/api', name: 'gen-api', component: () => import('../views/GenerateApi.vue'), meta: { title: '接口用例生成' } },
      { path: 'generate/script', name: 'gen-script', component: () => import('../views/GenerateScript.vue'), meta: { title: '自动化脚本' } },
      { path: 'generate/text', name: 'gen-text', component: () => import('../views/GenerateText.vue'), meta: { title: '自然语言生成' } },
      { path: 'cases', name: 'cases', component: () => import('../views/CaseLibrary.vue'), meta: { title: '用例库' } },
      { path: 'cases/:id', name: 'case-detail', component: () => import('../views/CaseDetail.vue'), meta: { title: '用例详情' } },
      { path: 'documents', name: 'documents', component: () => import('../views/Documents.vue'), meta: { title: '文档库' } },
      { path: 'tasks', name: 'tasks', component: () => import('../views/Tasks.vue'), meta: { title: '任务记录' } },
      { path: 'projects', name: 'projects', component: () => import('../views/Projects.vue'), meta: { title: '项目管理' } },
      { path: 'templates', name: 'templates', component: () => import('../views/Templates.vue'), meta: { title: '模板管理' } },
      { path: 'settings', name: 'settings', component: () => import('../views/Settings.vue'), meta: { title: '设置' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('cece_token')
  if (!to.meta.public && !token) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.name === 'login' && token) return { name: 'dashboard' }
  return true
})

export default router
