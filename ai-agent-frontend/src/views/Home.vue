<template>
  <div class="home">
    <!-- 导航栏 -->
    <nav class="nav">
      <div class="nav-brand">AI Agent 平台</div>
      <div class="nav-links">
        <button class="nav-link settings-link" @click="navigateTo('/settings')">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
          设置
        </button>
        <a href="https://github.com" target="_blank" class="nav-link">GitHub</a>
      </div>
    </nav>

    <!-- Hero -->
    <section class="hero">
      <h1 class="hero-title">
        构建能<span class="hero-accent">思考、行动、观察</span>的智能体
      </h1>
      <p class="hero-desc">
        基于 ReAct 模式的自主规划 AI Agent 平台。支持多工具编排、RAG 知识检索、
        结构化流式输出与思考链实时可视化。
      </p>
      <div class="hero-actions">
        <button class="btn-primary" @click="navigateTo('/agent-chat')">
          体验 Agent <span class="arrow">→</span>
        </button>
        <button class="btn-secondary" @click="navigateTo('/knowledge-rag')">
          RAG 知识库问答
        </button>
      </div>
      <div class="hero-stats">
        <div class="stat">
          <span class="stat-value">7</span>
          <span class="stat-label">内置工具</span>
        </div>
        <div class="stat">
          <span class="stat-value">20</span>
          <span class="stat-label">最大步数</span>
        </div>
        <div class="stat">
          <span class="stat-value">ReAct</span>
          <span class="stat-label">推理模式</span>
        </div>
        <div class="stat">
          <span class="stat-value">SSE</span>
          <span class="stat-label">流式传输</span>
        </div>
      </div>
    </section>

    <!-- 功能卡片 -->
    <section class="features">
      <div class="card" @click="navigateTo('/agent-chat')">
        <div class="card-icon">
          <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
          </svg>
        </div>
        <div class="card-body">
          <h3>Agent 智能助手</h3>
          <p>基于 ReAct 模式的自主规划智能体。输入复杂任务，Agent 自主分解步骤、调用工具，每一步的思考过程实时可视化展示。</p>
          <div class="card-tags">
            <span>网页搜索</span><span>文件读写</span><span>终端命令</span><span>PDF 生成</span><span>网页爬取</span>
          </div>
        </div>
        <div class="card-arrow">→</div>
      </div>

      <div class="card" @click="navigateTo('/knowledge-rag')">
        <div class="card-icon">
          <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5">
            <path d="M4 19.5A2.5 2.5 0 016.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 014 19.5v-15A2.5 2.5 0 016.5 2z"/><line x1="8" y1="7" x2="16" y2="7"/><line x1="8" y1="11" x2="14" y2="11"/>
          </svg>
        </div>
        <div class="card-body">
          <h3>RAG 知识库问答</h3>
          <p>基于检索增强生成的知识库问答系统。文档自动向量化，查询重写优化检索精度，支持上下文关联的多轮对话。</p>
          <div class="card-tags">
            <span>文档加载</span><span>向量检索</span><span>查询重写</span><span>多轮记忆</span>
          </div>
        </div>
        <div class="card-arrow">→</div>
      </div>
    </section>

    <footer class="footer">
      <p>© 2026 AI Research Assistant Agent — Spring AI + Vue 3</p>
    </footer>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'

useHead({
  title: 'AI Agent 平台 — 自主规划智能体',
  meta: [
    { name: 'description', content: '基于 ReAct 模式的自主规划 AI Agent 平台，支持多工具编排、RAG 知识检索与思考链实时可视化。' },
    { name: 'keywords', content: 'AI Agent, ReAct, 自主规划, 工具调用, RAG, 智能体' }
  ]
})

const router = useRouter()
const navigateTo = (path) => router.push(path)
</script>

<style scoped>
.home {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--bg-secondary);
}

/* Nav */
.nav {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 32px;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--border-subtle);
  position: sticky; top: 0; z-index: 100;
}
.nav-brand {
  font-size: 0.95rem; font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.3px;
}
.nav-links { display: flex; align-items: center; gap: 12px; }
.nav-link {
  font-size: 0.85rem; color: var(--text-muted);
  transition: color 0.2s;
}
.nav-link:hover { color: var(--text-primary); }
.settings-link {
  background: none; border: none;
  display: inline-flex; align-items: center; gap: 5px;
  cursor: pointer; font-family: var(--font-ui);
}

/* Hero */
.hero {
  text-align: center;
  padding: 72px 24px 56px;
  max-width: 720px; margin: 0 auto;
}
.hero-title {
  font-size: 2.8rem; font-weight: 700;
  line-height: 1.25; letter-spacing: -0.8px;
  margin-bottom: 16px;
  color: var(--text-primary);
}
.hero-accent {
  color: var(--accent);
  position: relative;
}
.hero-accent::after {
  content: '';
  position: absolute; bottom: 2px; left: 0; right: 0;
  height: 8px; background: var(--accent-light);
  z-index: -1; border-radius: 2px;
}
.hero-desc {
  font-size: 1.05rem; color: var(--text-secondary);
  line-height: 1.7; max-width: 520px;
  margin: 0 auto 32px;
}
.hero-actions {
  display: flex; gap: 12px; justify-content: center;
  margin-bottom: 48px;
}
.btn-primary {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 12px 28px; border-radius: var(--radius-md);
  font-size: 0.95rem; font-weight: 600;
  background: var(--accent); color: #fff; border: none;
  cursor: pointer; transition: all 0.2s;
}
.btn-primary:hover { box-shadow: 0 4px 16px var(--accent-glow); transform: translateY(-1px); }
.btn-primary .arrow { transition: transform 0.2s; }
.btn-primary:hover .arrow { transform: translateX(4px); }

.btn-secondary {
  padding: 12px 28px; border-radius: var(--radius-md);
  font-size: 0.95rem; font-weight: 500;
  background: var(--bg-primary); color: var(--text-primary);
  border: 1px solid var(--border-default); cursor: pointer;
  transition: all 0.2s;
}
.btn-secondary:hover { border-color: var(--accent); color: var(--accent); }

/* Stats */
.hero-stats {
  display: flex; justify-content: center; gap: 48px;
}
.stat { display: flex; flex-direction: column; align-items: center; gap: 4px; }
.stat-value {
  font-family: var(--font-mono);
  font-size: 1.4rem; font-weight: 700;
  color: var(--accent);
}
.stat-label {
  font-size: 0.75rem; color: var(--text-muted);
}

/* Cards */
.features {
  max-width: 860px; margin: 0 auto 72px; padding: 0 24px;
  display: flex; flex-direction: column; gap: 14px;
}
.card {
  display: flex; align-items: flex-start; gap: 22px;
  padding: 26px 30px; border-radius: var(--radius-lg);
  background: var(--bg-primary);
  border: 1px solid var(--border-subtle);
  cursor: pointer; transition: all 0.25s;
  box-shadow: var(--shadow-sm);
}
.card:hover {
  transform: translateY(-3px);
  box-shadow: var(--shadow-lg);
  border-color: var(--accent-light);
}
.card-icon {
  flex-shrink: 0; width: 50px; height: 50px;
  border-radius: var(--radius-md);
  background: var(--accent-light);
  display: flex; align-items: center; justify-content: center;
  color: var(--accent);
}
.card-body { flex: 1; min-width: 0; }
.card-body h3 {
  font-size: 1.1rem; font-weight: 700; margin-bottom: 6px;
  color: var(--text-primary);
}
.card-body p {
  font-size: 0.9rem; color: var(--text-secondary);
  line-height: 1.65; margin-bottom: 12px;
}
.card-tags {
  display: flex; flex-wrap: wrap; gap: 6px;
}
.card-tags span {
  font-size: 0.76rem; color: var(--text-secondary);
  background: var(--bg-secondary); padding: 3px 10px; border-radius: 6px;
}
.card-arrow {
  flex-shrink: 0; width: 36px; height: 36px;
  border-radius: 50%; background: var(--bg-secondary);
  display: flex; align-items: center; justify-content: center;
  font-size: 1.1rem; color: var(--text-muted);
  transition: all 0.25s; margin-top: 8px;
}
.card:hover .card-arrow { background: var(--accent); color: #fff; }

/* Footer */
.footer {
  text-align: center; padding: 20px;
  border-top: 1px solid var(--border-subtle);
  color: var(--text-muted); font-size: 0.8rem;
  margin-top: auto;
}

@media (max-width: 768px) {
  .nav { padding: 12px 18px; }
  .hero { padding: 48px 16px 36px; }
  .hero-title { font-size: 2rem; }
  .hero-stats { gap: 24px; }
  .card { padding: 20px 22px; }
  .card-arrow { display: none; }
}
</style>
