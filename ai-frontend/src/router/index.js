import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '../layouts/MainLayout.vue'

const routes = [
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '首页概览' }
      },
      {
        path: 'prompts',
        name: 'PromptList',
        component: () => import('../views/prompt/PromptList.vue'),
        meta: { title: '提示词管理' }
      },
      {
        path: 'prompts/create',
        name: 'PromptCreate',
        component: () => import('../views/prompt/PromptForm.vue'),
        meta: { title: '新增提示词' }
      },
      {
        path: 'prompts/:id/edit',
        name: 'PromptEdit',
        component: () => import('../views/prompt/PromptForm.vue'),
        meta: { title: '编辑提示词' }
      },
      {
        path: 'knowledge',
        name: 'KnowledgeBase',
        component: () => import('../views/knowledge/KnowledgeBase.vue'),
        meta: { title: '知识库管理' }
      },
      {
        path: 'chat',
        name: 'ChatView',
        component: () => import('../views/chat/ChatView.vue'),
        meta: { title: 'AI问答对话' }
      },
      {
        path: 'evaluation',
        name: 'EvalList',
        component: () => import('../views/evaluation/EvalList.vue'),
        meta: { title: '自动化评测' }
      },
      {
        path: 'ablation',
        name: 'AblationList',
        component: () => import('../views/ablation/AblationList.vue'),
        meta: { title: '消融实验' }
      },
      {
        path: 'train',
        name: 'TrainTask',
        component: () => import('../views/train/TrainTask.vue'),
        meta: { title: '模型微调' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
