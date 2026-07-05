# AI Agent 前端 UI/UX 整体优化 — 设计文档

> 日期: 2026-07-04 | 范围: 视觉焕新 + 交互增强 | 后端: 零改动

## 概述

在不改动后端代码的前提下，对前端进行全面的视觉和交互升级。范围涵盖设计系统、布局、聊天体验、思考链、首页、设置页、全局组件 7 个模块。

## 设计系统升级

### 色彩

- 主色调: `#4f46e5` → `#0071e3` (Apple Blue)
- 背景: `#f8f9fb` → `#f5f5f7`
- 仅亮色主题，不支持暗色模式

### 视觉质感

- Header/Sidebar: 毛玻璃半透明 (`backdrop-filter: blur() saturate()`)
- 阴影: 多层叠加替代单层 `box-shadow`
- 圆角: 全局 +2~4px

### 动画系统

4 条统一缓动曲线 + 3 档时长 (150ms/250ms/400ms)

## 各模块改动

### 1. style.css — 设计令牌

修改 `:root` CSS 变量：主色、背景色、阴影、圆角、动画系统。约 100 行改动。

### 2. AgentChat.vue — 三栏布局

- Header: 毛玻璃、降低高度到 52px、汉堡菜单图标、精简元素
- 侧边栏: 收起时缩成 56px 图标条，hover 展开完整面板
- 分隔线: 渐变半透明替代硬边框
- 思考链: 380px 宽度，入场动画

### 3. ChatRoom.vue — 聊天体验

- Markdown 渲染（marked + highlight.js），仅对 `ai-final` 消息
- 消息操作栏: hover 显示复制 + 重新生成
- 流式 token 字符级淡入动画
- 智能时间格式
- 自动滚动优化（用户手动上滚时不强制拉回）

### 4. ThinkingChain.vue — 思考链

- 步骤入场动画（右侧滑入 + 淡入）
- running 状态脉冲指示器
- JSON 语法着色（纯 CSS 实现）
- 折叠动画（Vue Transition）
- thinking_delta 闪烁光标

### 5. Home.vue — 首页

- Hero 元素渐入动画
- 统计数字 count-up 动效
- 卡片 hover 增强
- 快速示例提示
- 新增: Agent 工作流程示意（3 步静态展示）

### 6. Settings.vue — 设置页

- 保存按钮双位置（Header + 底部固定栏）
- API Key 粘贴按钮
- Provider tabs 改为下划线指示器样式
- 模型卡片选中动画

### 7. 全局组件（新增）

- Toast.vue: 通知系统（success/error/info）
- ShortcutPanel.vue: `?` 键触发的快捷键提示
- Skeleton.vue: 负载骨架屏

### 8. App.vue — 页面过渡

路由切换淡入淡出动画。

## 依赖

- `marked`: Markdown 渲染 (~20KB gzip)
- `highlight.js`: 代码语法高亮 (~22KB gzip)

## 后端影响

零改动。所有优化纯前端层面，不触碰任何 API 契约。

## 文件改动清单

| 文件 | 类型 | 估算行数 |
|------|------|---------|
| `src/style.css` | 修改 | ~100 |
| `src/App.vue` | 修改 | ~20 |
| `src/views/AgentChat.vue` | 修改 | ~80 |
| `src/views/Home.vue` | 修改 | ~60 |
| `src/views/Settings.vue` | 修改 | ~50 |
| `src/views/KnowledgeRag.vue` | 修改 | ~15 |
| `src/components/ChatRoom.vue` | 修改 | ~60 |
| `src/components/ThinkingChain.vue` | 修改 | ~70 |
| `src/components/Toast.vue` | 新增 | ~60 |
| `src/components/ShortcutPanel.vue` | 新增 | ~50 |
| `src/components/Skeleton.vue` | 新增 | ~30 |
| `src/components/AiAvatarFallback.vue` | 修改 | ~10 |
