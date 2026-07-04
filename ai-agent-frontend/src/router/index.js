import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: {
      title: '首页 - AI Agent 平台',
      description: '基于 ReAct 模式的自主规划 AI Agent 平台，支持多工具编排、RAG 知识检索与思考链实时可视化'
    }
  },
  {
    path: '/knowledge-rag',
    name: 'KnowledgeRag',
    component: () => import('../views/KnowledgeRag.vue'),
    meta: {
      title: 'AI 知识库问答 - AI Agent 平台',
      description: 'AI 知识库问答基于 RAG 检索增强生成技术，能够基于知识库文档智能回答您的问题'
    }
  },
  {
    path: '/agent-chat',
    name: 'AgentChat',
    component: () => import('../views/AgentChat.vue'),
    meta: {
      title: 'AI 智能研发助手 - AI Agent 平台',
      description: 'AI 智能研发助手是一个具备自主规划能力的 AI Agent，能够使用多种工具完成复杂任务'
    }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('../views/Settings.vue'),
    meta: {
      title: '设置 - AI Agent 平台',
      description: '配置 API Key 和模型参数，自定义 AI 智能研发助手的行为'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局导航守卫，设置文档标题
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta.title) {
    document.title = to.meta.title
  }
  next()
})

export default router