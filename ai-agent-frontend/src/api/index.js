import axios from 'axios'

// 根据环境变量设置 API 基础 URL
const API_BASE_URL = process.env.NODE_ENV === 'production'
 ? '/api' // 生产环境使用相对路径，适用于前后端部署在同一域名下
 : 'http://localhost:8123/api' // 开发环境指向本地后端服务

// 创建axios实例
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000
})

// 封装SSE连接
export const connectSSE = (url, params, onMessage, onError) => {
  // 构建带参数的URL
  const queryString = Object.keys(params)
    .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')

  const fullUrl = `${API_BASE_URL}${url}?${queryString}`

  // 创建EventSource
  const eventSource = new EventSource(fullUrl)

  eventSource.onmessage = event => {
    let data = event.data

    // 检查是否是特殊标记
    if (data === '[DONE]') {
      if (onMessage) onMessage('[DONE]')
    } else {
      // 处理普通消息
      if (onMessage) onMessage(data)
    }
  }

  eventSource.onerror = error => {
    if (onError) onError(error)
    eventSource.close()
  }

  // 返回eventSource实例，以便后续可以关闭连接
  return eventSource
}

// AI 知识库问答聊天（支持 sessionId 复用用户配置的 Provider）
export const chatWithRag = (message, chatId, sessionId) => {
  const params = { message, chatId }
  if (sessionId) params.sessionId = sessionId
  return connectSSE('/ai/rag/chat/sse', params)
}

// 获取知识库文档目录
export const getRagDocuments = async () => {
  const res = await request.get('/ai/rag/documents')
  return res.data
}

// 上传文档到知识库
export const uploadRagDocument = async (file) => {
  const formData = new FormData()
  formData.append('file', file)
  const res = await request.post('/ai/rag/documents/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000  // 2 分钟超时（大文件 + embedding 耗时）
  })
  return res.data
}

// 删除已上传的知识库文档
export const deleteRagDocument = async (filename) => {
  const res = await request.delete(
    `/ai/rag/documents/${encodeURIComponent(filename)}`)
  return res.data
}

// AI 智能研发助手聊天（支持 sessionId 会话复用）
export const chatWithAgent = (message, sessionId) => {
  const params = { message }
  if (sessionId) params.sessionId = sessionId
  return connectSSE('/ai/agent/chat', params)
}

// AI 智能研发助手结构化流（思考链可视化）
export const chatWithAgentStream = (message, sessionId) => {
  const params = { message }
  if (sessionId) params.sessionId = sessionId
  return connectSSE('/ai/agent/chat/stream', params)
}

// 保存用户模型配置（多 Provider）
export const saveConfig = async (provider, apiKey, modelName, baseUrl, sessionId) => {
  const body = { provider, apiKey, modelName }
  if (baseUrl) body.baseUrl = baseUrl
  if (sessionId) body.sessionId = sessionId
  const res = await request.post('/ai/config', body)
  return res.data
}

// 获取当前配置状态
export const getConfig = async (sessionId) => {
  const params = sessionId ? { sessionId } : {}
  const res = await request.get('/ai/config', { params })
  return res.data
}

// 查询会话上下文（包含历史消息列表 + 所有会话 ID）
export const getSessionContext = async (sessionId) => {
  const params = sessionId ? { sessionId } : {}
  const res = await request.get('/ai/agent/sessions/context', { params })
  return res.data
}

// 会话管理 API
export const listSessions = async () => {
  const res = await request.get('/ai/agent/sessions')
  return res.data
}

export const getSessionDetail = async (sessionId) => {
  const res = await request.get(`/ai/agent/sessions/${sessionId}`)
  return res.data
}

export const deleteSession = async (sessionId) => {
  const res = await request.delete(`/ai/agent/sessions/${sessionId}`)
  return res.data
}

export const updateSession = async (sessionId, title) => {
  const res = await request.patch(`/ai/agent/sessions/${sessionId}`, { title })
  return res.data
}

export default {
  chatWithRag,
  chatWithAgent,
  chatWithAgentStream,
  saveConfig,
  getConfig,
  getSessionContext,
  listSessions,
  getSessionDetail,
  deleteSession,
  updateSession,
  uploadRagDocument,
  deleteRagDocument,
  getRagDocuments
}