<template>
  <div class="rag-view">
    <!-- 顶栏 -->
    <header class="header glass-strong">
      <div class="header-left">
        <button class="icon-btn" @click="showSidebar = !showSidebar" title="文档管理">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
          </svg>
        </button>
        <h1>RAG 知识库问答</h1>
        <span class="badge">{{ chatId }}</span>
        <span v-if="classpathDocs.length" class="doc-chip">{{ classpathDocs.length + uploadedDocs.length }} 篇文档</span>
      </div>
      <div class="header-right">
        <button class="back-btn" @click="goBack">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
          返回
        </button>
      </div>
    </header>

    <!-- 主体：侧边栏 + 聊天 -->
    <div class="content">
      <!-- 文档管理侧边栏 -->
      <transition name="slide-sidebar">
        <aside v-if="showSidebar" class="sidebar">
          <!-- 上传区域 -->
          <div class="sidebar-header">
            <div class="upload-area"
              :class="{ dragging: isDragging }"
              @dragover.prevent="isDragging = true"
              @dragleave.prevent="isDragging = false"
              @drop.prevent="handleDrop"
            >
              <svg v-if="!isUploading" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12"/>
              </svg>
              <span v-if="isUploading" class="spinner"></span>
              <span class="upload-text">{{ isUploading ? '处理中...' : '拖拽文件或点击上传' }}</span>
              <label class="file-pick-btn">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14"/></svg>
                选择文件
                <input type="file" class="file-input-hidden"
                  accept=".md,.txt,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.html,.htm,.xml,.epub,.csv,.json"
                  @change="handleFileSelect" />
              </label>
            </div>
            <p class="format-hint">支持 PDF / Word / Markdown / TXT（≤50MB）</p>
            <div v-if="uploadError" class="upload-error">{{ uploadError }}</div>
          </div>

          <!-- 文档列表 -->
          <div class="doc-section">
            <div v-if="classpathDocs.length" class="section-label">📦 内置文档</div>
            <div
              v-for="doc in classpathDocs"
              :key="doc.filename"
              class="doc-item builtin"
            >
              <div class="doc-info">
                <span class="doc-name">{{ stripExt(doc.filename) }}</span>
                <span class="doc-meta">{{ doc.category || '' }}</span>
              </div>
            </div>

            <div v-if="uploadedDocs.length" class="section-label">📄 已上传 ({{ uploadedDocs.length }})</div>
            <div
              v-for="doc in uploadedDocs"
              :key="doc.filename"
              class="doc-item"
            >
              <div class="doc-info">
                <span class="doc-name">{{ doc.filename }}</span>
                <span class="doc-meta">{{ doc.fileType?.toUpperCase() }} · {{ doc.chunkCount }} 块</span>
              </div>
              <button
                class="delete-doc-btn"
                @click="deleteFile(doc.filename)"
                title="删除此文档"
              >×</button>
            </div>

            <div v-if="classpathDocs.length === 0 && uploadedDocs.length === 0" class="empty-list">
              暂无文档，请上传
            </div>
          </div>

          <!-- 侧边栏底部 -->
          <div class="sidebar-footer">
            <button class="toggle-sidebar-btn" @click="showSidebar = false" title="收起侧边栏">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18l-6-6 6-6"/></svg>
            </button>
          </div>
        </aside>
      </transition>

      <!-- 侧边栏收起态 — 图标条 -->
      <div v-if="!showSidebar" class="sidebar-mini" @mouseenter="showSidebar = true">
        <div class="mini-docs">
          <div
            v-for="doc in [...classpathDocs, ...uploadedDocs].slice(0, 8)"
            :key="doc.filename"
            class="mini-doc-icon"
            :class="{ uploaded: doc.source === 'user-upload' }"
            :title="doc.filename"
          >{{ doc.fileType ? doc.fileType.toUpperCase().charAt(0) : '📄' }}</div>
        </div>
        <label class="mini-upload">
          +
          <input type="file" class="file-input-hidden"
            accept=".md,.txt,.pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.html,.htm,.xml,.epub,.csv,.json"
            @change="handleFileSelect" />
        </label>
      </div>

      <div class="divider-soft" v-if="showSidebar"></div>

      <!-- 聊天区域 -->
      <div class="chat-area">
        <ChatRoom
          :messages="messages"
          :connection-status="connectionStatus"
          ai-type="rag"
          @send-message="sendMessage"
          @regenerate="sendMessage"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, inject } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import ChatRoom from '../components/ChatRoom.vue'
import { chatWithRag, getRagDocuments, uploadRagDocument, deleteRagDocument } from '../api'

useHead({
  title: 'RAG 知识库问答 — 检索增强生成',
  meta: [
    { name: 'description', content: '基于 RAG 检索增强生成技术的知识库问答系统，支持文档向量化与智能问答。' },
    { name: 'keywords', content: 'RAG, 知识库, 检索增强, 向量搜索' }
  ]
})

const router = useRouter()
const toast = inject('toast', null)
const messages = ref([])
const chatId = ref('')
const connectionStatus = ref('disconnected')
const uploadedDocs = ref([])
const classpathDocs = ref([])
const showSidebar = ref(true)
const isUploading = ref(false)
const isDragging = ref(false)
const uploadError = ref('')
let eventSource = null

const addMessage = (content, isUser, type = '') => messages.value.push({ content, isUser, type, time: Date.now() })
const stripExt = (name) => (name || '').replace(/\.\w+$/, '')

const sendMessage = (message) => {
  addMessage(message, true)
  if (eventSource) eventSource.close()
  const idx = messages.value.length
  addMessage('', false, 'ai-streaming')
  connectionStatus.value = 'connecting'
  const sessionId = localStorage.getItem('aiagent_session_id') || ''
  eventSource = chatWithRag(message, chatId.value, sessionId)
  eventSource.onmessage = (event) => {
    const data = event.data
    if (data && data !== '[DONE]' && idx < messages.value.length) messages.value[idx].content += data
    if (data === '[DONE]') { connectionStatus.value = 'disconnected'; if (idx < messages.value.length) messages.value[idx].type = 'ai-final'; eventSource.close() }
  }
  eventSource.onerror = () => {
    if (connectionStatus.value === 'connecting') {
      if (idx < messages.value.length && messages.value[idx].content) {
        connectionStatus.value = 'disconnected'
        messages.value[idx].type = 'ai-final'
      } else {
        connectionStatus.value = 'error'
      }
    }
    eventSource.close()
  }
}

// ── 文档目录刷新 ──
const refreshCatalog = async () => {
  try {
    const allDocs = await getRagDocuments()
    classpathDocs.value = allDocs.filter(d => d.source === 'classpath')
    uploadedDocs.value = allDocs.filter(d => d.source === 'user-upload')
  } catch (e) {
    console.warn('Failed to load document catalog:', e)
  }
}

// ── 文件上传 ──
const uploadFile = async (file) => {
  const ext = file.name.split('.').pop()?.toLowerCase()
  const allowed = ['md','txt','pdf','doc','docx','ppt','pptx','xls','xlsx','html','htm','xml','epub','csv','json']
  if (!ext || !allowed.includes(ext)) {
    const msg = `不支持的文件类型: .${ext || '未知'}`
    uploadError.value = msg
    if (toast) toast.error(msg)
    return
  }
  if (file.size === 0) {
    if (toast) toast.error('文件为空')
    return
  }
  if (file.size > 50 * 1024 * 1024) {
    if (toast) toast.error('文件大小超过 50MB 限制')
    return
  }
  uploadError.value = ''
  isUploading.value = true
  try {
    const result = await uploadRagDocument(file)
    const msg = `✅ ${result.filename} 上传完成 (${result.chunkCount} 块)`
    if (toast) toast.success(msg)
    await refreshCatalog()
  } catch (e) {
    const msg = e?.response?.data?.error || e.message || '上传失败'
    uploadError.value = msg
    if (toast) toast.error(msg)
  } finally {
    isUploading.value = false
  }
}

const handleDrop = (event) => {
  isDragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) uploadFile(file)
}

const handleFileSelect = (event) => {
  const file = event.target?.files?.[0]
  if (file) uploadFile(file)
  event.target.value = ''
}

// ── 文件删除 ──
const deleteFile = async (filename) => {
  if (!confirm(`确定删除文档 "${filename}"？\n从向量库中移除后将无法再检索到此文档。`)) return
  try {
    await deleteRagDocument(filename)
    if (toast) toast.success(`已删除: ${filename}`)
    await refreshCatalog()
  } catch (e) {
    const msg = e?.response?.data?.error || e.message || '删除失败'
    if (toast) toast.error(msg)
  }
}

const goBack = () => router.push('/')

onMounted(async () => {
  chatId.value = 'rag_' + Math.random().toString(36).substring(2, 8)
  await refreshCatalog()
  let welcome = '你好，我是 AI 知识库助手。请提出你的问题，我将基于知识库文档为你提供答案。'
  if (classpathDocs.value.length + uploadedDocs.value.length > 0) {
    welcome += '\n\n📚 当前知识库包含以下文档（点击左上角菜单管理）：'
    for (const doc of classpathDocs.value) {
      welcome += '\n  • ' + stripExt(doc.filename || '')
      if (doc.summary) welcome += ' — ' + doc.summary
    }
    for (const doc of uploadedDocs.value) {
      welcome += '\n  • ' + (doc.filename || '') + ' 📎'
    }
  }
  addMessage(welcome, false, 'ai-final')
})

onBeforeUnmount(() => { if (eventSource) eventSource.close() })
</script>

<style scoped>
.rag-view { display: flex; flex-direction: column; height: 100vh; background: var(--bg-secondary); }

/* ===== 顶栏 ===== */
.header {
  height: 52px; display: flex; align-items: center; justify-content: space-between;
  padding: 0 16px; flex-shrink: 0; z-index: 100;
  border-bottom: 1px solid var(--border-subtle);
}
.header-left { display: flex; align-items: center; gap: 10px; }
.header-left h1 { font-size: 0.95rem; font-weight: 700; color: var(--text-primary); }
.header-right { display: flex; align-items: center; gap: 10px; }

.icon-btn {
  width: 34px; height: 34px; display: flex; align-items: center; justify-content: center;
  border: none; background: transparent; color: var(--text-secondary);
  border-radius: var(--radius-sm); cursor: pointer; transition: all 0.15s;
}
.icon-btn:hover { background: var(--bg-secondary); color: var(--text-primary); }

.badge {
  font-size: 0.68rem; font-family: var(--font-mono);
  color: var(--accent); background: var(--accent-light);
  padding: 2px 8px; border-radius: 8px;
}
.doc-chip {
  font-size: 0.7rem; font-weight: 500; color: var(--text-secondary);
  background: var(--bg-secondary); padding: 2px 10px; border-radius: var(--radius-full);
}

.back-btn {
  display: flex; align-items: center; gap: 4px;
  padding: 6px 14px; border-radius: var(--radius-sm);
  font-size: 0.82rem; color: var(--text-secondary);
  background: var(--bg-primary); border: 1px solid var(--border-subtle);
  cursor: pointer; transition: all 0.2s;
}
.back-btn:hover { color: var(--accent); border-color: var(--accent-light); }

/* ===== 内容区 ===== */
.content {
  display: flex; flex: 1; overflow: hidden; position: relative;
}

/* ===== 侧边栏 ===== */
.sidebar {
  width: 300px; flex-shrink: 0;
  display: flex; flex-direction: column;
  background: var(--bg-primary);
  border-right: 1px solid var(--border-subtle);
  overflow: hidden;
}

/* 上传区域 */
.sidebar-header {
  padding: 12px; border-bottom: 1px solid var(--border-subtle);
}
.upload-area {
  display: flex; flex-wrap: wrap; align-items: center; gap: 8px;
  padding: 12px; border: 2px dashed var(--border-subtle);
  border-radius: var(--radius-sm);
  transition: border-color 0.2s, background 0.2s;
}
.upload-area.dragging {
  border-color: var(--accent);
  background: var(--accent-light);
}
.upload-text {
  font-size: 0.78rem; color: var(--text-muted); min-width: 0;
}
.file-pick-btn {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 12px; border-radius: var(--radius-sm);
  font-size: 0.8rem; font-weight: 500;
  color: #fff; background: var(--accent);
  border: none; cursor: pointer; white-space: nowrap;
  transition: opacity 0.15s;
}
.file-pick-btn:hover { opacity: 0.85; }
.file-input-hidden { display: none; }
.format-hint {
  margin: 8px 0 0; font-size: 0.68rem; color: var(--text-muted);
}
.upload-error {
  margin-top: 8px; font-size: 0.75rem; color: #e74c3c;
  word-break: break-all;
}

.spinner {
  display: inline-block; width: 16px; height: 16px;
  border: 2px solid var(--border-subtle); border-top-color: var(--accent);
  border-radius: 50%; animation: spin 0.6s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* 文档列表 */
.doc-section {
  flex: 1; overflow-y: auto; padding: 8px 0;
}
.section-label {
  padding: 8px 12px 4px;
  font-size: 0.7rem; font-weight: 600; color: var(--text-tertiary);
  text-transform: uppercase; letter-spacing: 0.5px;
}

.doc-item {
  display: flex; align-items: center;
  padding: 8px 12px; transition: background 0.15s;
  border-left: 3px solid transparent;
}
.doc-item:hover { background: var(--bg-secondary); }
.doc-item.builtin { border-left-color: transparent; opacity: 0.85; }
.doc-info { flex: 1; min-width: 0; }
.doc-name {
  display: block; font-size: 0.83rem; color: var(--text-primary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  font-weight: 500;
}
.doc-meta {
  display: block; font-size: 0.68rem; color: var(--text-tertiary);
  margin-top: 1px;
}

.delete-doc-btn {
  width: 24px; height: 24px; border-radius: 4px;
  border: none; background: transparent;
  color: var(--text-tertiary); cursor: pointer;
  font-size: 1.1rem; line-height: 1;
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: all 0.15s; flex-shrink: 0;
}
.doc-item:hover .delete-doc-btn { opacity: 1; }
.delete-doc-btn:hover { background: #fecaca; color: #dc2626; }

.empty-list {
  padding: 24px; text-align: center;
  color: var(--text-tertiary); font-size: 0.82rem;
}

/* 侧边栏底部 */
.sidebar-footer {
  padding: 8px; border-top: 1px solid var(--border-subtle);
}
.toggle-sidebar-btn {
  width: 100%; padding: 4px; border-radius: 4px;
  border: none; background: transparent;
  color: var(--text-tertiary); cursor: pointer;
  display: flex; justify-content: center;
  transition: all 0.15s;
}
.toggle-sidebar-btn:hover { background: var(--bg-secondary); color: var(--text-secondary); }

/* 侧边栏收起态 — 图标条 */
.sidebar-mini {
  width: 56px; flex-shrink: 0;
  display: flex; flex-direction: column; align-items: center;
  padding: 8px 0; background: var(--bg-glass);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border-right: 1px solid var(--border-subtle);
  transition: all 0.25s var(--ease-out);
}
.mini-docs { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 8px 0; overflow-y: auto; }
.mini-doc-icon {
  width: 36px; height: 36px; border-radius: var(--radius-sm);
  display: flex; align-items: center; justify-content: center;
  font-size: 0.7rem; font-weight: 700; color: var(--text-secondary);
  background: var(--bg-secondary); cursor: default;
}
.mini-doc-icon.uploaded { color: var(--accent); }
.mini-upload {
  width: 36px; height: 36px; border-radius: var(--radius-sm);
  border: 1px dashed var(--border-default); background: transparent;
  color: var(--text-muted); font-size: 1.1rem; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.mini-upload:hover { border-color: var(--accent); color: var(--accent); }

/* 侧边栏动画 */
.slide-sidebar-enter-active, .slide-sidebar-leave-active {
  transition: width 0.25s, opacity 0.25s; overflow: hidden;
}
.slide-sidebar-enter-from, .slide-sidebar-leave-to { width: 0; opacity: 0; }

/* 聊天区域 */
.chat-area { flex: 1; min-width: 0; overflow: hidden; }
</style>
