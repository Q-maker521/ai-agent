# Frontend UI/UX 整体优化 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 AI Agent 前端进行全面视觉和交互升级，Apple 风格质感，零后端改动。

**Architecture:** 维护现有 Vue 3 + Vite 架构，不改动组件树结构。新增 3 个全局组件（Toast/Skeleton/ShortcutPanel），升级 2 个轻量 npm 依赖（marked + highlight.js）。

**Tech Stack:** Vue 3.2, Vite 4.3, marked, highlight.js

## Global Constraints

- 零后端改动 — 不触碰任何 Java 文件
- 不引入状态管理库（Pinia 等）
- 不做暗色模式
- 不引入虚拟滚动等重量级优化
- 所有新增依赖通过 npm install 安装

---

### Task 1: 安装 npm 依赖

**Files:**
- Modify: `ai-agent-frontend/package.json`

- [ ] **Step 1: 安装 marked 和 highlight.js**

```bash
cd ai-agent-frontend && npm install marked highlight.js
```

- [ ] **Step 2: 验证安装**

```bash
cd ai-agent-frontend && node -e "console.log(require('marked').marked('**bold**'))"
```

Expected: `<p><strong>bold</strong></p>`

---

### Task 2: 设计系统升级 (style.css)

**Files:**
- Modify: `ai-agent-frontend/src/style.css`

所有后续任务依赖此文件的 CSS 变量，必须先完成。

- [ ] **Step 1: 替换 CSS 变量和全局样式**

将 `ai-agent-frontend/src/style.css` 完全重写为以下内容：

```css
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;500;700&family=JetBrains+Mono:wght@400;500&display=swap');

:root {
  /* Background — Apple warm gray palette */
  --bg-primary: #ffffff;
  --bg-secondary: #f5f5f7;
  --bg-glass: rgba(255, 255, 255, 0.72);
  --bg-glass-strong: rgba(255, 255, 255, 0.88);

  /* Accent — Apple Blue */
  --accent: #0071e3;
  --accent-hover: #0077ed;
  --accent-light: rgba(0, 113, 227, 0.08);
  --accent-glow: rgba(0, 113, 227, 0.16);

  /* Semantic */
  --success: #30b158;
  --error: #ff3b30;
  --warn: #ff9500;

  /* Text */
  --text-primary: #1d1d1f;
  --text-secondary: #6e6e73;
  --text-muted: #aeaeb2;
  --text-tertiary: #aeaeb2;

  /* Borders */
  --border-subtle: rgba(0, 0, 0, 0.06);
  --border-default: rgba(0, 0, 0, 0.10);
  --border-strong: rgba(0, 0, 0, 0.16);

  /* Typography */
  --font-ui: 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'SF Pro Text', sans-serif;
  --font-mono: 'JetBrains Mono', 'SF Mono', 'Fira Code', 'Consolas', monospace;

  /* Radius — softened */
  --radius-sm: 8px;
  --radius-md: 14px;
  --radius-lg: 20px;
  --radius-xl: 28px;
  --radius-full: 9999px;

  /* Shadows — multi-layer Apple style */
  --shadow-sm: 0 1px 3px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.06);
  --shadow-md: 0 4px 12px rgba(0,0,0,0.05), 0 2px 4px rgba(0,0,0,0.04);
  --shadow-lg: 0 10px 32px rgba(0,0,0,0.06), 0 4px 8px rgba(0,0,0,0.04);

  /* Animation — unified easing curves */
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in-out: cubic-bezier(0.65, 0, 0.35, 1);
  --spring: cubic-bezier(0.34, 1.56, 0.64, 1);
  --duration-fast: 150ms;
  --duration-normal: 250ms;
  --duration-slow: 400ms;

  /* Glass */
  --glass-blur: blur(20px) saturate(180%);
  --glass-blur-light: blur(12px) saturate(150%);
}

*,
*::before,
*::after {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html {
  font-size: 16px;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

body {
  font-family: var(--font-ui);
  background: var(--bg-secondary);
  color: var(--text-primary);
  line-height: 1.6;
  min-height: 100vh;
}

::-webkit-scrollbar { width: 5px; height: 5px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #d1d5db; border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: #9ca3af; }

::selection {
  background: var(--accent-light);
  color: var(--accent);
}

/* ── Glass utility ── */
.glass {
  background: var(--bg-glass);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
}

.glass-strong {
  background: var(--bg-glass-strong);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
}

/* ── Divider utility ── */
.divider-soft {
  background: linear-gradient(
    to bottom,
    transparent 5%,
    var(--border-subtle) 50%,
    transparent 95%
  );
  width: 1px;
  flex-shrink: 0;
}

/* ── Pulse animation ── */
@keyframes pulse-glow {
  0%, 100% { box-shadow: 0 0 0 0 var(--accent-glow); }
  50% { box-shadow: 0 0 0 6px transparent; }
}

/* ── Skeleton loading ── */
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

.skeleton {
  background: linear-gradient(90deg, #e8e8ed 25%, #f0f0f5 50%, #e8e8ed 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: var(--radius-sm);
}

/* ── Fade in up ── */
@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

.animate-in {
  animation: fadeInUp 0.5s var(--ease-out) both;
}

/* ── Page transition ── */
.page-enter-active {
  transition: opacity var(--duration-normal) var(--ease-out),
              transform var(--duration-normal) var(--ease-out);
}
.page-leave-active {
  transition: opacity var(--duration-fast);
}
.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
}
```

- [ ] **Step 2: 验证样式生效**

启动前端 dev server，确认 Home 页面正常渲染，颜色已变为 Apple Blue。

```bash
cd ai-agent-frontend && npm run dev
```

---

### Task 3: Toast 通知组件（新增）

**Files:**
- Create: `ai-agent-frontend/src/components/Toast.vue`

**Interfaces:**
- Produces: `<Toast />` 组件，通过 `provide('toast', showToast)` 暴露方法
- `showToast(message: string, type?: 'success' | 'error' | 'info')` — 3 秒自动消失

- [ ] **Step 1: 创建 Toast.vue**

```vue
<template>
  <Teleport to="body">
    <Transition name="toast" @after-leave="$emit('empty')">
      <div v-if="visible" class="toast" :class="type">
        <span class="toast-icon">{{ iconMap[type] }}</span>
        <span class="toast-msg">{{ message }}</span>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  message: { type: String, default: '' },
  type: { type: String, default: 'success' }
})

const visible = ref(false)
const iconMap = { success: '✓', error: '✗', info: 'ℹ' }
let timer = null

watch(() => props.message, (val) => {
  if (!val) return
  visible.value = true
  clearTimeout(timer)
  timer = setTimeout(() => { visible.value = false }, 2800)
})
</script>

<style scoped>
.toast {
  position: fixed; top: 20px; left: 50%; transform: translateX(-50%);
  z-index: 9999;
  display: flex; align-items: center; gap: 8px;
  padding: 10px 20px; border-radius: var(--radius-full);
  font-size: 0.85rem; font-weight: 500;
  box-shadow: var(--shadow-lg);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  pointer-events: none;
}
.toast.success { background: rgba(48,177,88,0.92); color: #fff; }
.toast.error   { background: rgba(255,59,48,0.92); color: #fff; }
.toast.info    { background: rgba(0,113,227,0.92); color: #fff; }
.toast-icon { font-size: 1rem; font-weight: 700; }

.toast-enter-active { transition: all 0.35s var(--spring); }
.toast-leave-active { transition: all 0.2s var(--ease-out); }
.toast-enter-from { opacity: 0; transform: translateX(-50%) translateY(-12px) scale(0.9); }
.toast-leave-to   { opacity: 0; transform: translateX(-50%) translateY(-8px) scale(0.95); }
</style>
```

- [ ] **Step 2: 在 App.vue 中集成 Toast**

将 `ai-agent-frontend/src/App.vue` 改为：

```vue
<template>
  <router-view v-slot="{ Component }">
    <Transition name="page" mode="out-in">
      <component :is="Component" />
    </Transition>
  </router-view>
  <Toast ref="toastRef" :message="toastMsg" :type="toastType" />
</template>

<script setup>
import { ref, provide } from 'vue'
import Toast from './components/Toast.vue'

const toastRef = ref(null)
const toastMsg = ref('')
const toastType = ref('success')

function showToast(message, type = 'success') {
  // 触发 Toast 重新渲染的关键：先清空再设置
  toastMsg.value = ''
  setTimeout(() => {
    toastMsg.value = message
    toastType.value = type
  }, 0)
}

provide('toast', showToast)
</script>
```

- [ ] **Step 3: 验证 Toast**

在任意页面（如 Home.vue）的 `onMounted` 中临时注入测试：

```js
const toast = inject('toast')
toast('测试通知', 'success')
```

验证页面顶部出现绿色通知并 3 秒后消失。

---

### Task 4: Skeleton 骨架屏组件（新增）

**Files:**
- Create: `ai-agent-frontend/src/components/Skeleton.vue`

**Interfaces:**
- Props: `lines: number` (默认 3), `width: string` (默认 '100%')
- Produces: 灰色脉冲条占位

- [ ] **Step 1: 创建 Skeleton.vue**

```vue
<template>
  <div class="skeleton-block">
    <div
      v-for="i in lines"
      :key="i"
      class="skeleton-line"
      :style="{ width: i === lines ? '60%' : widths[(i - 1) % widths.length] }"
    />
  </div>
</template>

<script setup>
defineProps({
  lines: { type: Number, default: 3 }
})

const widths = ['100%', '85%', '72%', '60%']
</script>

<style scoped>
.skeleton-block { padding: 12px 0; }
.skeleton-line {
  height: 14px; border-radius: 6px;
  background: linear-gradient(90deg, #e8e8ed 25%, #f0f0f5 50%, #e8e8ed 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  margin-bottom: 10px;
}
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
</style>
```

---

### Task 5: ShortcutPanel 快捷键面板（新增）

**Files:**
- Create: `ai-agent-frontend/src/components/ShortcutPanel.vue`

- [ ] **Step 1: 创建 ShortcutPanel.vue**

```vue
<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="overlay" @click.self="close">
        <div class="panel glass-strong">
          <h3>快捷键</h3>
          <div class="shortcut-list">
            <div v-for="s in shortcuts" :key="s.key" class="shortcut-row">
              <kbd>{{ s.key }}</kbd>
              <span>{{ s.desc }}</span>
            </div>
          </div>
          <button class="close-btn" @click="close">关闭</button>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const visible = ref(false)
const shortcuts = [
  { key: 'Enter', desc: '发送消息' },
  { key: 'Esc', desc: '停止生成' },
  { key: '/', desc: '聚焦输入框' },
  { key: '?', desc: '显示/隐藏此面板' },
]

function onKeydown(e) {
  if (e.key === '?' && !isInputElement(e.target)) {
    e.preventDefault()
    visible.value = !visible.value
  }
  if (e.key === 'Escape' && visible.value) {
    visible.value = false
  }
}

function isInputElement(el) {
  const tag = el.tagName
  return tag === 'INPUT' || tag === 'TEXTAREA' || el.isContentEditable
}

function close() { visible.value = false }

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<style scoped>
.overlay {
  position: fixed; inset: 0; z-index: 9998;
  display: flex; align-items: center; justify-content: center;
  background: rgba(0,0,0,0.15);
  backdrop-filter: blur(4px);
}
.panel {
  width: 320px; padding: 24px; border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--border-subtle);
}
.panel h3 {
  font-size: 1rem; font-weight: 700; margin-bottom: 16px;
  color: var(--text-primary);
}
.shortcut-row {
  display: flex; align-items: center; gap: 12px;
  padding: 8px 0; font-size: 0.85rem; color: var(--text-secondary);
}
kbd {
  min-width: 52px; text-align: center;
  padding: 3px 8px; border-radius: 4px;
  background: var(--bg-secondary); border: 1px solid var(--border-subtle);
  font-family: var(--font-mono); font-size: 0.75rem;
  color: var(--text-primary);
}
.close-btn {
  margin-top: 16px; width: 100%; padding: 8px;
  border-radius: var(--radius-sm); border: 1px solid var(--border-subtle);
  background: var(--bg-secondary); color: var(--text-secondary);
  font-size: 0.82rem; cursor: pointer;
}
.close-btn:hover { color: var(--text-primary); }
.modal-enter-active { transition: all 0.2s var(--ease-out); }
.modal-leave-active { transition: all 0.15s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
.modal-enter-from .panel { transform: scale(0.95); }
</style>
```

---

### Task 6: AiAvatarFallback 微调

**Files:**
- Modify: `ai-agent-frontend/src/components/AiAvatarFallback.vue`

- [ ] **Step 1: 升级头像样式**

替换 style 部分，给头像加渐变背景和微阴影：

```css
<style scoped>
.avatar {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  font-size: 0.75rem; font-weight: 700;
  border-radius: var(--radius-sm);
}
.rag {
  background: linear-gradient(135deg, #e8f5e9, #c8e6c9);
  color: #2e7d32;
}
.agent {
  background: linear-gradient(135deg, var(--accent-light), rgba(0,113,227,0.15));
  color: var(--accent);
}
</style>
```

---

### Task 7: Home.vue 首页优化

**Files:**
- Modify: `ai-agent-frontend/src/views/Home.vue`

- [ ] **Step 1: 添加 Hero 动画和计数动效**

在 `<script setup>` 中新增：

```js
import { ref, onMounted } from 'vue'

// ── Count-up animation ──
const toolCount = ref(0)
const stepCount = ref(0)

function animateCount(refVar, target, duration = 600) {
  const start = performance.now()
  function tick(now) {
    const p = Math.min((now - start) / duration, 1)
    const eased = 1 - Math.pow(1 - p, 3) // ease-out cubic
    refVar.value = Math.round(eased * target)
    if (p < 1) requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}

onMounted(() => {
  animateCount(toolCount, 7)
  setTimeout(() => animateCount(stepCount, 20), 200)
})
```

- [ ] **Step 2: 模板中绑定动态数字**

修改 Hero stats：

```html
<div class="stat">
  <span class="stat-value">{{ toolCount }}</span>
  <span class="stat-label">内置工具</span>
</div>
<div class="stat">
  <span class="stat-value">{{ stepCount }}</span>
  <span class="stat-label">最大步数</span>
</div>
```

- [ ] **Step 3: 添加渐入动画**

给 Hero 元素加 `animate-in` class 和 `style="animation-delay"`：

```html
<h1 class="hero-title animate-in" style="animation-delay: 0ms">...</h1>
<p class="hero-desc animate-in" style="animation-delay: 80ms">...</p>
<div class="hero-actions animate-in" style="animation-delay: 160ms">...</div>
```

- [ ] **Step 4: 卡片底部加快速示例**

每张卡片底部新增：

```html
<div class="card-hint" @click.stop="navigateTo('/agent-chat'); /* 后续通过 query 传参 */">
  💡 试试: "帮我分析今天 AI 领域的重大新闻"
</div>
```

添加样式：

```css
.card-hint {
  margin-top: 14px; padding: 10px 14px;
  background: var(--bg-secondary); border-radius: var(--radius-sm);
  font-size: 0.8rem; color: var(--accent); cursor: pointer;
  transition: background 0.2s;
}
.card-hint:hover { background: var(--accent-light); }
```

- [ ] **Step 5: 新增 Agent 工作流程示意**

在 features section 和 footer 之间插入：

```html
<section class="workflow-demo">
  <h2>Agent 如何工作？</h2>
  <div class="workflow-steps">
    <div class="wf-step">
      <div class="wf-icon">💡</div>
      <div class="wf-title">思考</div>
      <div class="wf-desc">"用户想要最新 AI 新闻，我需要搜索"</div>
    </div>
    <div class="wf-arrow">→</div>
    <div class="wf-step">
      <div class="wf-icon">🔧</div>
      <div class="wf-title">行动</div>
      <div class="wf-desc">调用 web_search<br/>查询 AI 相关新闻</div>
    </div>
    <div class="wf-arrow">→</div>
    <div class="wf-step">
      <div class="wf-icon">📋</div>
      <div class="wf-title">观察</div>
      <div class="wf-desc">分析搜索结果<br/>生成最终回复</div>
    </div>
  </div>
</section>
```

样式：

```css
.workflow-demo {
  text-align: center; padding: 48px 24px 64px;
  max-width: 700px; margin: 0 auto;
}
.workflow-demo h2 {
  font-size: 1.3rem; font-weight: 700; margin-bottom: 32px;
  color: var(--text-primary);
}
.workflow-steps {
  display: flex; align-items: flex-start; justify-content: center;
  gap: 16px;
}
.wf-step {
  display: flex; flex-direction: column; align-items: center;
  gap: 8px; flex: 1; max-width: 180px;
}
.wf-icon { font-size: 2rem; }
.wf-title { font-size: 0.9rem; font-weight: 700; color: var(--text-primary); }
.wf-desc { font-size: 0.78rem; color: var(--text-secondary); line-height: 1.5; }
.wf-arrow {
  font-size: 1.2rem; color: var(--text-muted);
  margin-top: 18px;
}
@media (max-width: 768px) {
  .workflow-steps { flex-direction: column; align-items: center; }
  .wf-arrow { transform: rotate(90deg); margin: 0; }
}
```

---

### Task 8: Settings.vue 设置页优化

**Files:**
- Modify: `ai-agent-frontend/src/views/Settings.vue`

- [ ] **Step 1: Provider tabs 改为下划线指示器样式**

替换 `.provider-tab.active` 样式：

```css
.provider-tab {
  padding: 8px 18px; border-radius: 0;
  font-size: 0.84rem; font-weight: 500;
  border: none; border-bottom: 2px solid transparent;
  background: transparent; color: var(--text-secondary);
  cursor: pointer; transition: all 0.2s; font-family: var(--font-ui);
}
.provider-tab:hover { color: var(--text-primary); border-bottom-color: var(--border-default); }
.provider-tab.active {
  background: transparent; color: var(--accent);
  border-bottom-color: var(--accent);
}
```

- [ ] **Step 2: 保存按钮移到 Header**

Settings Header 改为两端布局，右侧放保存按钮+状态：

```html
<header class="header">
  <button class="back-btn" @click="goBack">...</button>
  <h1>设置</h1>
  <div class="header-right">
    <span v-if="statusMsg" class="status-inline" :class="{ ok: statusOk, err: !statusOk }">
      {{ statusMsg }}
    </span>
    <button class="save-btn-sm" @click="saveSettings" :disabled="saving || !apiKey.trim()">
      {{ saving ? '保存中...' : '保存' }}
    </button>
  </div>
</header>
```

样式：

```css
.header-right { display: flex; align-items: center; gap: 12px; }
.save-btn-sm {
  padding: 6px 16px; border: none; background: var(--accent); color: #fff;
  font-size: 0.82rem; font-weight: 600; font-family: var(--font-ui);
  border-radius: var(--radius-sm); cursor: pointer; transition: all 0.2s;
}
.save-btn-sm:hover:not(:disabled) { background: var(--accent-hover); }
.save-btn-sm:disabled { opacity: 0.4; cursor: not-allowed; }
.status-inline { font-size: 0.78rem; }
.status-inline.ok { color: var(--success); }
.status-inline.err { color: var(--error); }
```

- [ ] **Step 3: 添加 API Key 粘贴按钮**

在 input-with-toggle 中 key-input 前加粘贴按钮：

```html
<div class="input-with-toggle">
  <button class="paste-btn" @click="pasteApiKey" title="从剪贴板粘贴">
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
  </button>
  <input ... class="key-input" />
  ...
</div>
```

添加方法：

```js
async function pasteApiKey() {
  try {
    const text = await navigator.clipboard.readText()
    if (text) apiKey.value = text.trim()
  } catch { /* permission denied */ }
}
```

样式：

```css
.paste-btn {
  width: 36px; height: 38px; display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--border-subtle); border-radius: var(--radius-sm);
  background: var(--bg-primary); color: var(--text-muted); cursor: pointer; transition: all 0.15s; flex-shrink: 0;
}
.paste-btn:hover { color: var(--accent); border-color: var(--accent-light); }
```

- [ ] **Step 4: 移除底部 Actions 卡片（已移到 Header）**

删除原来的 `<div class="card">` 中的 actions 部分，保留其他卡片不变。

---

### Task 9: KnowledgeRag.vue 微调

**Files:**
- Modify: `ai-agent-frontend/src/views/KnowledgeRag.vue`

- [ ] **Step 1: 统一 Header 样式**

将 Header 背景改为毛玻璃：

```css
.header {
  /* 保持现有样式，增加: */
  background: var(--bg-glass);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
}
```

---

### Task 10: ChatRoom.vue 聊天组件升级

**Files:**
- Modify: `ai-agent-frontend/src/components/ChatRoom.vue`

- [ ] **Step 1: 引入 Markdown 渲染**

```js
import { marked } from 'marked'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import bash from 'highlight.js/lib/languages/bash'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import xml from 'highlight.js/lib/languages/xml'

hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('python', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)

marked.setOptions({
  breaks: true,
  gfm: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
})

function renderMarkdown(text) {
  if (!text) return ''
  return marked(text)
}
```

- [ ] **Step 2: AI 消息使用 v-html 渲染**

给 `ai-final` 类型的消息用 `v-html`：

```html
<div v-if="!msg.isUser && msg.type === 'ai-final'" class="msg-text markdown-body" v-html="renderMarkdown(msg.content)"></div>
<div v-else class="msg-text">{{ msg.content }}</div>
```

- [ ] **Step 3: 添加 Markdown 样式**

```css
.markdown-body :deep(p) { margin-bottom: 8px; }
.markdown-body :deep(p:last-child) { margin-bottom: 0; }
.markdown-body :deep(code) {
  background: rgba(0,0,0,0.05); padding: 2px 6px; border-radius: 4px;
  font-family: var(--font-mono); font-size: 0.82em;
}
.markdown-body :deep(pre) {
  background: #1d1d1f; color: #f5f5f7; padding: 12px 16px; border-radius: var(--radius-sm);
  overflow-x: auto; margin: 8px 0; font-size: 0.8rem; line-height: 1.5;
}
.markdown-body :deep(pre code) { background: none; padding: 0; font-size: inherit; }
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(a) { color: var(--accent); text-decoration: underline; }
```

- [ ] **Step 4: 消息操作栏**

在 AI 消息气泡内加操作按钮（hover 显示）：

```html
<div v-if="!msg.isUser && msg.type === 'ai-final'" class="msg-actions">
  <button class="action-btn" @click="copyMessage(msg.content)" title="复制">
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
  </button>
  <button class="action-btn" @click="regenerate(msg, index)" title="重新生成">
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="1 4 1 10 7 10"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
  </button>
</div>
```

在 `<script setup>` 中添加：

```js
import { inject } from 'vue'
const toast = inject('toast', () => {})

async function copyMessage(text) {
  try {
    await navigator.clipboard.writeText(text)
    toast('已复制到剪贴板', 'success')
  } catch {
    toast('复制失败', 'error')
  }
}

const emit = defineEmits(['send-message', 'regenerate'])
function regenerate(msg, msgIndex) {
  // 找到此 AI 消息前面最近的一条用户消息
  const msgs = props.messages
  for (let i = msgIndex - 1; i >= 0; i--) {
    if (msgs[i].isUser) {
      emit('regenerate', msgs[i].content)
      return
    }
  }
}
```

样式：

```css
.msg-actions {
  display: flex; gap: 4px; margin-top: 6px;
  opacity: 0; transition: opacity 0.15s;
}
.ai-msg:hover .msg-actions { opacity: 1; }
.action-btn {
  width: 28px; height: 28px; border-radius: 6px;
  border: 1px solid var(--border-subtle); background: var(--bg-primary);
  color: var(--text-secondary); cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  transition: all 0.15s;
}
.action-btn:hover { color: var(--accent); border-color: var(--accent-light); }
```

- [ ] **Step 5: 流式打字效果**

给 `ai-streaming` 消息的 msg-text 加光标动画：

```css
.ai-streaming .msg-text::after {
  content: '▊';
  color: var(--accent);
  animation: blink-cursor 0.8s infinite;
}
@keyframes blink-cursor { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }
```

- [ ] **Step 6: 智能时间格式**

替换 `formatTime` 函数：

```js
function formatTime(ts) {
  const d = new Date(ts)
  const now = new Date()
  const time = d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  if (d.toDateString() === now.toDateString()) return time
  const yesterday = new Date(now)
  yesterday.setDate(yesterday.getDate() - 1)
  if (d.toDateString() === yesterday.toDateString()) return '昨天 ' + time
  return `${String(d.getMonth()+1).padStart(2,'0')}/${String(d.getDate()).padStart(2,'0')} ${time}`
}
```

- [ ] **Step 7: 自动滚动优化**

替换 scroll 函数：

```js
function scroll() {
  nextTick(() => {
    const el = messagesContainer.value
    if (!el) return
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 100
    if (atBottom) {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' })
    }
  })
}
```

---

### Task 11: ThinkingChain.vue 思考链升级

**Files:**
- Modify: `ai-agent-frontend/src/components/ThinkingChain.vue`

- [ ] **Step 1: 添加步骤入场动画**

用 Vue `<TransitionGroup>` 包裹步骤列表：

```html
<TransitionGroup name="step" tag="div">
  <div v-for="step in steps" :key="step.stepNumber" class="step-card" :class="step.status">
    ...
  </div>
</TransitionGroup>
```

样式：

```css
.step-enter-active {
  transition: all 0.35s var(--spring);
}
.step-enter-from {
  opacity: 0;
  transform: translateX(16px);
}
```

- [ ] **Step 2: running 状态脉冲指示器**

```css
.step-card.running {
  border-left: 3px solid var(--accent);
  animation: pulse-glow 2s infinite;
}
```

- [ ] **Step 3: JSON 语法着色**

新增函数：

```js
function colorizeJson(str) {
  try {
    const obj = JSON.parse(str)
    return JSON.stringify(obj, null, 2)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/("(?:\\.|[^"\\])*")(\s*:)/g, '<span class="json-key">$1</span>$2')
      .replace(/: (\d+\.?\d*)/g, ': <span class="json-number">$1</span>')
      .replace(/: "(?:\\.|[^"\\])*"/g, (m) => m.replace(/(: )"((?:\\.|[^"\\])*)"/, '$1<span class="json-string">"$2"</span>'))
      .replace(/: (true|false|null)/g, ': <span class="json-bool">$1</span>')
  } catch { return str }
}
```

模板中用 `v-html`：

```html
<pre class="code-block colored" v-html="colorizeJson(call.input)"></pre>
```

样式：

```css
.code-block.colored :deep(.json-key)    { color: #881391; }
.code-block.colored :deep(.json-string) { color: #1a7f37; }
.code-block.colored :deep(.json-number) { color: #0550ae; }
.code-block.colored :deep(.json-bool)   { color: #cf222e; }
```

- [ ] **Step 4: 折叠动画**

用 Vue `<Transition name="collapse">` 包裹 `step-body`：

```html
<Transition name="collapse">
  <div v-if="step.expanded" class="step-body">
    ...
  </div>
</Transition>
```

```css
.collapse-enter-active, .collapse-leave-active {
  transition: max-height 0.35s var(--ease-out), opacity 0.2s;
  overflow: hidden;
}
.collapse-enter-from, .collapse-leave-to {
  max-height: 0; opacity: 0;
}
.collapse-enter-to, .collapse-leave-from {
  max-height: 2000px; opacity: 1;
}
```

- [ ] **Step 5: thinking_delta 闪烁光标**

```css
.step-card.running .event-text {
  position: relative;
}
.step-card.running .event-text::after {
  content: '▊';
  color: var(--accent);
  animation: blink-cursor 0.8s infinite;
}
```

---

### Task 12: AgentChat.vue 主页面重构

**Files:**
- Modify: `ai-agent-frontend/src/views/AgentChat.vue`

这是改动最大的组件。依赖 Task 2-11 全部完成。

- [ ] **Step 1: Header 重构为毛玻璃风格**

```html
<header class="header glass-strong">
  <div class="header-left">
    <button class="icon-btn" @click="showSidebar = !showSidebar" title="侧边栏">
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
      </svg>
    </button>
    <h1>Agent 智能助手</h1>
    <span v-if="stepsCount > 0" class="step-chip">第 {{ stepsCount }} 步</span>
  </div>
  <div class="header-right">
    <button class="icon-btn" :class="{ active: showChain }" @click="showChain = !showChain" title="思考链">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <polyline points="16 3 21 3 21 8"/><line x1="4" y1="20" x2="21" y2="3"/>
        <polyline points="21 16 21 21 16 21"/><line x1="15" y1="15" x2="21" y2="21"/>
        <line x1="4" y1="4" x2="9" y2="9"/>
      </svg>
    </button>
    <span v-if="showChain && stepsCount" class="step-badge">{{ stepsCount }}</span>
  </div>
</header>
```

Header 新样式：

```css
.header {
  height: 52px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 16px; flex-shrink: 0; z-index: 100;
  border-bottom: 1px solid var(--border-subtle);
}
.header-left, .header-right { display: flex; align-items: center; gap: 10px; }
.header-left h1 { font-size: 0.95rem; font-weight: 700; color: var(--text-primary); }
.icon-btn {
  width: 34px; height: 34px; display: flex; align-items: center; justify-content: center;
  border: none; background: transparent; color: var(--text-secondary);
  border-radius: var(--radius-sm); cursor: pointer; transition: all 0.15s;
}
.icon-btn:hover { background: var(--bg-secondary); color: var(--text-primary); }
.icon-btn.active { color: var(--accent); background: var(--accent-light); }
.step-chip {
  font-size: 0.7rem; font-weight: 500; color: var(--accent);
  background: var(--accent-light); padding: 2px 10px; border-radius: var(--radius-full);
}
.step-badge {
  position: absolute; top: 2px; right: 2px;
  min-width: 16px; height: 16px; border-radius: 8px;
  background: var(--accent); color: #fff; font-size: 0.6rem;
  display: flex; align-items: center; justify-content: center;
}
```

- [ ] **Step 2: 添加 stepsCount 计算属性**

```js
import { computed } from 'vue'
const stepsCount = computed(() => {
  // 从 agentEvents 中统计 step_start 数量
  return agentEvents.value.filter(e => e.type === 'step_start').length
})
```

- [ ] **Step 3: 侧边栏收起态改为图标条**

收起侧边栏时（`!showSidebar`），渲染一个 56px 窄条：

```html
<div v-if="!showSidebar" class="sidebar-mini" @mouseenter="showSidebar = true">
  <div class="mini-sessions">
    <div
      v-for="s in sessionList.slice(0, 8)"
      :key="s.sessionId"
      class="mini-avatar"
      :class="{ active: s.sessionId === sessionId }"
      :title="s.title"
    >{{ s.title.charAt(0) }}</div>
  </div>
  <button class="mini-new" @click.stop="newConversation" title="新对话">+</button>
</div>
```

```css
.sidebar-mini {
  width: 56px; flex-shrink: 0;
  display: flex; flex-direction: column; align-items: center;
  padding: 8px 0; background: var(--bg-glass);
  backdrop-filter: var(--glass-blur);
  border-right: 1px solid var(--border-subtle);
  transition: all 0.25s var(--ease-out);
}
.mini-sessions { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 8px 0; }
.mini-avatar {
  width: 36px; height: 36px; border-radius: var(--radius-sm);
  display: flex; align-items: center; justify-content: center;
  font-size: 0.75rem; font-weight: 700; color: var(--text-secondary);
  background: var(--bg-secondary); cursor: pointer; transition: all 0.15s;
}
.mini-avatar:hover, .mini-avatar.active { background: var(--accent-light); color: var(--accent); }
.mini-new {
  width: 36px; height: 36px; border-radius: var(--radius-sm);
  border: 1px dashed var(--border-default); background: transparent;
  color: var(--text-muted); font-size: 1.1rem; cursor: pointer;
}
.mini-new:hover { border-color: var(--accent); color: var(--accent); }
```

- [ ] **Step 4: 三栏分隔线改为渐变**

```html
<div class="divider-soft" v-if="showSidebar"></div>
```

在 sidebar 后、chat-panel 后各插一条。

- [ ] **Step 5: 思考链面板宽度调整**

```css
.chain-panel { width: 380px; } /* 原 400px */
```

- [ ] **Step 6: 集成 ShortcutPanel**

在模板底部加入：

```html
<ShortcutPanel />
```

```js
import ShortcutPanel from '../components/ShortcutPanel.vue'
```

- [ ] **Step 7: 处理 regenerate 事件**

ChatRoom 的 `@regenerate` 事件会触发重新发送，复用 sendMessage 逻辑：

```html
<ChatRoom ... @regenerate="(msg) => sendMessage(msg)" />
```

---

### Task 13: 最终验证

- [ ] **Step 1: 启动前后端**

```bash
# 终端 1: 后端
export JAVA_HOME="D:\java\jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
export DASHSCOPE_API_KEY=sk-REPLACED-DASHSCOPE-KEY
./mvnw spring-boot:run

# 终端 2: 前端
cd ai-agent-frontend && npm run dev
```

- [ ] **Step 2: 验证清单**

| 验证项 | 方法 |
|--------|------|
| Home 页渐入动画 | 刷新页面，观察 Hero 区元素逐个出现 |
| 统计数字 count-up | 刷新页面，观察 "7" 和 "20" 从 0 滚到目标值 |
| AgentChat 毛玻璃 Header | 滚动消息列表，Header 背景呈半透明模糊 |
| 侧边栏收起/展开 | 点击汉堡菜单，收起态显示图标条 |
| Markdown 渲染 | 发送 "写一段带**加粗**和`代码`的回复"，观察格式 |
| 消息复制 | hover AI 消息，点击复制图标，Toast 通知弹出 |
| 思考链入场动画 | 发送任务，观察步骤从右侧滑入 |
| 思考链 JSON 着色 | 展开步骤中的工具参数，key/string/number 不同颜色 |
| 思考链折叠动画 | 点击步骤标题折叠/展开，观察平滑过渡 |
| Settings 配置保存 | 修改配置点击保存，Toast 通知 |
| 快捷键面板 | 按 `?` 键，弹出面板 |
| 页面切换过渡 | 从 Home 切换到 AgentChat，观察淡入淡出 |
| 流式光标 | 发送任务，观察 AI 回复尾部闪烁光标 |
```
