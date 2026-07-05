<template>
  <div class="settings-view">
    <header class="header">
      <button class="back-btn" @click="goBack">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M19 12H5M12 19l-7-7 7-7"/></svg>
        返回
      </button>
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

    <div class="content">
      <!-- Provider 选择 -->
      <div class="card">
        <div class="card-title">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/></svg>
          选择 Provider
        </div>
        <div class="provider-tabs">
          <button v-for="p in providers" :key="p.value"
            class="provider-tab" :class="{ active: selectedProvider === p.value }"
            @click="selectProvider(p.value)">
            {{ p.label }}
          </button>
        </div>
      </div>

      <!-- API Key -->
      <div class="card">
        <div class="card-title">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          API Key
        </div>
        <p class="card-desc">{{ providerDesc[selectedProvider] || '输入你对应平台的 API Key' }} Key 仅保存在你的浏览器和当前会话中。</p>
        <div class="input-with-toggle">
          <button class="paste-btn" @click="pasteApiKey" title="从剪贴板粘贴">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
          </button>
          <input
            :type="showKey ? 'text' : 'password'"
            v-model="apiKey"
            :placeholder="keyPlaceholder[selectedProvider] || '输入 API Key'"
            class="key-input"
            @input="onInputChange"
          />
          <button class="toggle-btn" @click="showKey = !showKey">
            <svg v-if="!showKey" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>
            <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
          </button>
        </div>
      </div>

      <!-- 模型选择 -->
      <div class="card">
        <div class="card-title">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 22 8.5 22 15.5 12 22 2 15.5 2 8.5 12 2"/><line x1="12" y1="22" x2="12" y2="15.5"/><polyline points="22 8.5 12 15.5 2 8.5"/></svg>
          模型选择
        </div>
        <div class="model-options">
          <label
            v-for="m in currentModels"
            :key="m.value"
            class="model-radio"
            :class="{ active: selectedModel === m.value && !useCustom }"
            @click="selectPreset(m.value)"
          >
            <div class="radio-dot"><span v-if="selectedModel === m.value && !useCustom" class="dot-fill"></span></div>
            <div class="radio-text">
              <span class="model-name">{{ m.label }}</span>
              <span class="model-desc">{{ m.desc }}</span>
            </div>
          </label>
          <label class="model-radio" :class="{ active: useCustom }" @click="selectCustom">
            <div class="radio-dot"><span v-if="useCustom" class="dot-fill"></span></div>
            <div class="radio-text" style="flex:1">
              <span class="model-name">自定义</span>
              <input v-model="customModel" placeholder="输入模型名称" class="custom-input"
                @focus="selectCustom" @input="onInputChange" />
            </div>
          </label>
        </div>
      </div>

      <!-- Base URL（仅 OpenAI兼容 显示） -->
      <div class="card" v-if="selectedProvider === 'openai-compatible'">
        <div class="card-title">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
          Base URL
        </div>
        <p class="card-desc">API 端点地址，如 https://api.deepseek.com 或 https://api.moonshot.cn/v1</p>
        <input v-model="baseUrl" placeholder="https://api.deepseek.com"
          class="url-input" @input="onInputChange" />
      </div>

      <!-- 链接 -->
      <div class="card">
        <div class="card-title">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
          获取 API Key
        </div>
        <p class="card-desc">
          前往对应平台创建 API Key：
          <a :href="providerLinks[selectedProvider]" target="_blank" class="link">{{ providerLinks[selectedProvider] }}</a>
        </p>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { saveConfig, getConfig } from '../api'

const router = useRouter()
const STORAGE_KEY = 'aiagent_config'

// ─── Providers ───
const providers = [
  { value: 'dashscope', label: 'DashScope' },
  { value: 'openai',    label: 'OpenAI' },
  { value: 'anthropic', label: 'Anthropic' },
  { value: 'openai-compatible', label: 'OpenAI兼容' }
]

const keyPlaceholder = {
  'dashscope': 'sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
  'openai': 'sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
  'anthropic': 'sk-ant-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx',
  'openai-compatible': 'sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'
}

const providerDesc = {
  'dashscope': '阿里云百炼 DashScope API Key。',
  'openai': 'OpenAI 官方 API Key。',
  'anthropic': 'Anthropic Claude API Key。',
  'openai-compatible': '任何兼容 OpenAI 接口的平台（DeepSeek、Moonshot、Groq 等）。需填写下方 Base URL。'
}

const providerLinks = {
  'dashscope': 'https://bailian.console.aliyun.com/',
  'openai': 'https://platform.openai.com/api-keys',
  'anthropic': 'https://console.anthropic.com/',
  'openai-compatible': 'https://platform.deepseek.com/api_keys'
}

// 各 Provider 推荐模型
const modelPresets = {
  'dashscope': [
    { value: 'qwen3.7-max', label: 'qwen3.7-max', desc: '最新旗舰，综合能力最强' },
    { value: 'qwen-plus',    label: 'qwen-plus',    desc: '均衡性价比，适合日常任务' },
    { value: 'qwen-max',     label: 'qwen-max',     desc: '复杂推理，长文本理解' },
    { value: 'qwen-turbo',   label: 'qwen-turbo',   desc: '速度最快，适合简单任务' }
  ],
  'openai': [
    { value: 'gpt-4o',       label: 'gpt-4o',       desc: '旗舰多模态，速度与质量兼具' },
    { value: 'gpt-4o-mini',  label: 'gpt-4o-mini',  desc: '轻量快速，性价比高' },
    { value: 'o3',           label: 'o3',            desc: '最新推理模型，擅长复杂逻辑' },
    { value: 'gpt-4.1',      label: 'gpt-4.1',      desc: '最新旗舰，编程能力增强' }
  ],
  'anthropic': [
    { value: 'claude-opus-4-8',     label: 'claude-opus-4-8',     desc: '最强旗舰，适合高难度任务' },
    { value: 'claude-sonnet-4-6',   label: 'claude-sonnet-4-6',   desc: '平衡性能与速度' },
    { value: 'claude-haiku-4-5',    label: 'claude-haiku-4-5',    desc: '最快，适合简单任务' }
  ],
  'openai-compatible': [
    { value: 'deepseek-chat',     label: 'deepseek-chat',     desc: 'DeepSeek 通用对话' },
    { value: 'deepseek-reasoner', label: 'deepseek-reasoner', desc: 'DeepSeek 推理增强' },
    { value: 'moonshot-v1-8k',    label: 'moonshot-v1-8k',    desc: 'Moonshot (Kimi) 8K' },
    { value: 'llama-3.1-70b',     label: 'llama-3.1-70b',     desc: 'Groq / Together AI 等' }
  ]
}

// ─── Reactive state ───
const selectedProvider = ref('dashscope')
const apiKey = ref('')
const selectedModel = ref('qwen3.7-max')
const customModel = ref('')
const useCustom = ref(false)
const baseUrl = ref('')
const showKey = ref(false)
const saving = ref(false)
const statusMsg = ref('')
const statusOk = ref(true)
const sessionId = ref(localStorage.getItem('aiagent_session_id') || '')

const currentModels = computed(() => modelPresets[selectedProvider.value] || modelPresets['dashscope'])

function selectProvider(val) {
  selectedProvider.value = val
  useCustom.value = false
  // 切换 provider 时自动选中第一个推荐模型
  const models = modelPresets[val] || []
  if (models.length) selectedModel.value = models[0].value
  statusMsg.value = ''
}

function selectPreset(val) { selectedModel.value = val; useCustom.value = false; statusMsg.value = '' }
function selectCustom() { useCustom.value = true; statusMsg.value = '' }
function onInputChange() { statusMsg.value = '' }
function goBack() { router.push('/') }

async function pasteApiKey() {
  try {
    const text = await navigator.clipboard.readText()
    if (text) apiKey.value = text.trim()
  } catch { /* permission denied */ }
}

async function saveSettings() {
  const modelName = useCustom.value ? customModel.value.trim() : selectedModel.value
  if (!modelName) {
    statusMsg.value = '请选择或输入模型名称'; statusOk.value = false; return
  }
  if (!apiKey.value.trim()) {
    statusMsg.value = '请输入 API Key'; statusOk.value = false; return
  }

  saving.value = true; statusMsg.value = ''
  try {
    const result = await saveConfig(
      selectedProvider.value, apiKey.value.trim(), modelName,
      baseUrl.value.trim(), sessionId.value
    )
    const config = { provider: selectedProvider.value, apiKey: apiKey.value.trim(),
      modelName, useCustom: useCustom.value, customModel: customModel.value.trim(),
      selectedModel: selectedModel.value, baseUrl: baseUrl.value.trim() }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(config))
    localStorage.setItem('aiagent_session_id', result.sessionId)
    sessionId.value = result.sessionId
    statusMsg.value = '配置已保存: ' + result.provider + ' / ' + result.modelName
    statusOk.value = true
  } catch (e) {
    statusMsg.value = '保存失败: ' + (e.response?.data?.error || e.message)
    statusOk.value = false
  } finally { saving.value = false }
}

onMounted(async () => {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved) {
    try {
      const c = JSON.parse(saved)
      if (c.provider) selectedProvider.value = c.provider
      apiKey.value = c.apiKey || ''
      baseUrl.value = c.baseUrl || ''
      if (c.useCustom) { useCustom.value = true; customModel.value = c.customModel || '' }
      else { selectedModel.value = c.selectedModel || currentModels.value[0]?.value || '' }
    } catch {}
  }
  if (sessionId.value) {
    try {
      const cfg = await getConfig(sessionId.value)
      if (cfg.configured) {
        statusMsg.value = '当前: ' + (cfg.provider || '') + ' / ' + cfg.modelName + ' (已配置)'
        statusOk.value = true
      }
    } catch {}
  }
})
</script>

<style scoped>
.settings-view { display: flex; flex-direction: column; height: 100vh; background: var(--bg-secondary); }
.header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 20px; background: var(--bg-primary);
  border-bottom: 1px solid var(--border-subtle); flex-shrink: 0; box-shadow: var(--shadow-sm);
}
.header h1 { font-size: 1rem; font-weight: 700; color: var(--text-primary); }
.back-btn {
  display: flex; align-items: center; gap: 4px; padding: 6px 14px;
  border-radius: var(--radius-sm); font-size: 0.82rem; color: var(--text-secondary);
  background: var(--bg-primary); border: 1px solid var(--border-subtle);
  cursor: pointer; transition: all 0.2s; font-family: var(--font-ui);
}
.back-btn:hover { color: var(--accent); border-color: var(--accent-light); }
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

.content { flex: 1; overflow-y: auto; padding: 24px; max-width: 640px; margin: 0 auto; width: 100%; }

.card {
  background: var(--bg-primary); border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg); padding: 20px 24px; margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
}
.card-title { display: flex; align-items: center; gap: 8px; font-size: 0.92rem; font-weight: 600; color: var(--text-primary); margin-bottom: 6px; }
.card-title svg { color: var(--accent); flex-shrink: 0; }
.card-desc { font-size: 0.8rem; color: var(--text-muted); margin-bottom: 14px; line-height: 1.55; }

/* Provider tabs */
.provider-tabs { display: flex; gap: 6px; flex-wrap: wrap; }
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

/* API Key */
.input-with-toggle { display: flex; align-items: center; gap: 8px; }
.key-input {
  flex: 1; padding: 10px 14px; border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md); font-size: 0.85rem; font-family: var(--font-mono);
  color: var(--text-primary); background: var(--bg-secondary); outline: none; transition: border-color 0.2s;
}
.key-input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow); }
.key-input::placeholder { color: var(--text-muted); font-family: var(--font-ui); font-size: 0.78rem; }
.paste-btn {
  width: 36px; height: 38px; display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--border-subtle); border-radius: var(--radius-sm);
  background: var(--bg-primary); color: var(--text-muted); cursor: pointer; transition: all 0.15s; flex-shrink: 0;
}
.paste-btn:hover { color: var(--accent); border-color: var(--accent-light); }

.toggle-btn {
  width: 38px; height: 38px; display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--border-subtle); border-radius: var(--radius-md);
  background: var(--bg-primary); color: var(--text-muted); cursor: pointer; transition: all 0.15s; flex-shrink: 0;
}
.toggle-btn:hover { color: var(--accent); border-color: var(--accent-light); }

/* Model */
.model-options { display: flex; flex-direction: column; gap: 6px; }
.model-radio {
  display: flex; align-items: flex-start; gap: 10px; padding: 10px 14px;
  border-radius: var(--radius-md); border: 1px solid var(--border-subtle);
  cursor: pointer; transition: all 0.15s;
}
.model-radio:hover { border-color: var(--accent-light); background: var(--accent-light); }
.model-radio.active { border-color: var(--accent); background: var(--accent-light); }
.radio-dot {
  width: 18px; height: 18px; border-radius: 50%; border: 2px solid var(--border-default);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 1px;
}
.model-radio.active .radio-dot { border-color: var(--accent); }
.dot-fill { width: 8px; height: 8px; border-radius: 50%; background: var(--accent); }
.radio-text { display: flex; flex-direction: column; gap: 2px; }
.model-name { font-size: 0.85rem; font-weight: 600; color: var(--text-primary); }
.model-desc { font-size: 0.74rem; color: var(--text-muted); }
.custom-input {
  margin-top: 4px; padding: 6px 10px; border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm); font-size: 0.8rem; font-family: var(--font-mono);
  color: var(--text-primary); background: var(--bg-primary); outline: none; width: 100%; max-width: 280px;
}
.custom-input:focus { border-color: var(--accent); }

/* Base URL */
.url-input {
  width: 100%; padding: 10px 14px; border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md); font-size: 0.85rem; font-family: var(--font-mono);
  color: var(--text-primary); background: var(--bg-secondary); outline: none; transition: border-color 0.2s;
}
.url-input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-glow); }

.link { color: var(--accent); font-weight: 500; text-decoration: underline; text-underline-offset: 2px; }
.link:hover { opacity: 0.8; }


@media (max-width: 768px) { .content { padding: 16px; } .card { padding: 16px 18px; } }
</style>
