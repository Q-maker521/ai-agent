<template>
  <div class="thinking-chain">
    <div class="chain-header">
      <h3>思考链</h3>
      <span class="step-badge" v-if="steps.length > 0">{{ steps.length }} 步</span>
    </div>

    <div class="chain-timeline" ref="timelineContainer">
      <div v-if="steps.length === 0 && !isRunning" class="chain-empty">
        <p>发送任务后，这里将实时展示<br>Agent 的推理过程</p>
      </div>

      <div v-for="step in steps" :key="step.stepNumber" class="step-card" :class="step.status">
        <div class="step-header" @click="toggleStep(step)">
          <div class="step-indicator">
            <span v-if="step.status === 'running'" class="spinner"></span>
            <span v-else-if="step.status === 'completed'" class="icon-check">✓</span>
            <span v-else-if="step.status === 'error'" class="icon-error">✗</span>
            <span v-else class="icon-pending">○</span>
          </div>
          <div class="step-title">第 {{ step.stepNumber }} 步</div>
          <div class="step-toggle">{{ step.expanded ? '▾' : '▸' }}</div>
        </div>

        <div v-if="step.expanded" class="step-body">
          <div v-if="step.thinking" class="event-block thinking">
            <div class="event-icon">💭</div>
            <div class="event-content">
              <div class="event-label">思考</div>
              <div class="event-text">{{ step.thinking }}</div>
            </div>
          </div>

          <div v-for="(call, idx) in step.toolCalls" :key="'call-' + idx" class="event-block tool-call">
            <div class="event-icon">🔧</div>
            <div class="event-content">
              <div class="event-label">调用工具</div>
              <div class="tool-name">{{ call.name }}</div>
              <pre class="code-block">{{ formatJson(call.input) }}</pre>
            </div>
          </div>

          <div v-for="(result, idx) in step.toolResults" :key="'result-' + idx" class="event-block tool-result">
            <div class="event-icon">📋</div>
            <div class="event-content">
              <div class="event-label">工具返回: {{ result.name }}</div>
              <pre class="code-block" v-if="!result.expanded" @click="result.expanded = true">{{ truncate(result.output, 200) }}<span class="expand-hint" v-if="result.output.length > 200"> [展开]</span></pre>
              <pre class="code-block expanded" v-else @click="result.expanded = false">{{ result.output }}<span class="expand-hint"> [收起]</span></pre>
            </div>
          </div>

          <div v-if="step.summary" class="event-block summary">
            <div class="event-icon">📝</div>
            <div class="event-content">
              <div class="event-label">步骤结果</div>
              <div class="event-text">{{ step.summary }}</div>
            </div>
          </div>

          <div v-if="step.finalAnswer" class="event-block final-answer">
            <div class="event-icon">✅</div>
            <div class="event-content">
              <div class="event-label">最终回复</div>
              <div class="event-text final-text">{{ step.finalAnswer }}</div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="isRunning && steps.length === 0" class="running-hint">
        <span class="spinner large"></span>
        <p>Agent 正在思考...</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, watch, nextTick } from 'vue'

const props = defineProps({
  events: { type: Array, default: () => [] },
  isRunning: { type: Boolean, default: false }
})

const timelineContainer = ref(null)
const steps = reactive([])

watch(() => props.events.length, (newLen, oldLen) => {
  const newEvents = props.events.slice(oldLen)
  for (const event of newEvents) processEvent(event)
  nextTick(() => { if (timelineContainer.value) timelineContainer.value.scrollTop = timelineContainer.value.scrollHeight })
})

function processEvent(event) {
  switch (event.type) {
    case 'step_start':
      steps.push({ stepNumber: event.stepNumber, status: 'running', thinking: null, toolCalls: [], toolResults: [], summary: null, expanded: true })
      break
    case 'thinking': updateStep(s => s.thinking = event.content); break
    case 'tool_call': updateStep(s => s.toolCalls.push({ name: event.toolName, input: event.toolInput })); break
    case 'tool_result': updateStep(s => s.toolResults.push({ name: event.toolName, output: event.toolOutput, expanded: false })); break
    case 'step_end': updateStep(s => { s.status = 'completed'; s.summary = event.content }); break
    case 'final_answer': updateStep(s => { s.finalAnswer = event.content; s.status = 'completed' }); break
    case 'agent_error': updateStep(s => { s.status = 'error'; s.summary = event.content }); break
  }
}

function updateStep(fn) { if (steps.length > 0) fn(steps[steps.length - 1]) }
function toggleStep(step) { step.expanded = !step.expanded }
function formatJson(str) { try { return JSON.stringify(JSON.parse(str), null, 2) } catch { return str } }
function truncate(text, max) { if (!text) return ''; return text.length > max ? text.substring(0, max) + '...' : text }
</script>

<style scoped>
.thinking-chain {
  display: flex; flex-direction: column; height: 100%;
  background: var(--bg-primary); font-family: var(--font-ui);
}
.chain-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-subtle);
}
.chain-header h3 { margin: 0; font-size: 0.9rem; font-weight: 700; color: var(--text-primary); }
.step-badge {
  background: var(--accent-light); color: var(--accent);
  padding: 2px 10px; border-radius: 10px; font-size: 0.7rem; font-weight: 500;
}
.chain-timeline { flex: 1; overflow-y: auto; padding: 12px; }
.chain-empty {
  display: flex; align-items: center; justify-content: center;
  height: 100%; color: var(--text-muted); font-size: 0.85rem; text-align: center;
}

.step-card {
  margin-bottom: 8px; border-radius: var(--radius-md);
  background: var(--bg-secondary); border: 1px solid var(--border-subtle);
  overflow: hidden; transition: all 0.2s;
}
.step-card.running { border-color: var(--accent-light); }
.step-card.completed { border-color: rgba(5, 150, 105, 0.2); }
.step-card.error { border-color: rgba(220, 38, 38, 0.2); }

.step-header {
  display: flex; align-items: center; padding: 10px 14px;
  cursor: pointer; user-select: none; transition: background 0.15s;
}
.step-header:hover { background: rgba(0,0,0,0.02); }

.step-indicator { width: 22px; height: 22px; display: flex; align-items: center; justify-content: center; margin-right: 8px; font-size: 13px; }
.icon-check { color: var(--success); }
.icon-error { color: var(--error); }
.icon-pending { color: var(--text-muted); }
.spinner {
  width: 14px; height: 14px; border: 2px solid var(--accent-light);
  border-top-color: var(--accent); border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
.spinner.large { width: 22px; height: 22px; margin-bottom: 8px; }
@keyframes spin { to { transform: rotate(360deg); } }

.step-title { flex: 1; font-size: 0.82rem; font-weight: 600; color: var(--text-primary); }
.step-toggle { color: var(--text-muted); font-size: 0.75rem; }

.step-body { padding: 0 14px 10px; }
.event-block { display: flex; margin-top: 8px; }
.event-icon { width: 22px; font-size: 0.75rem; flex-shrink: 0; }
.event-content { flex: 1; min-width: 0; }
.event-label { font-size: 0.68rem; color: var(--text-muted); text-transform: uppercase; letter-spacing: 0.3px; margin-bottom: 2px; }
.event-text { font-size: 0.82rem; color: var(--text-secondary); line-height: 1.55; }
.tool-name { font-size: 0.82rem; color: var(--accent); font-weight: 600; margin-bottom: 3px; font-family: var(--font-mono); }
.code-block {
  background: var(--bg-primary); padding: 8px 10px; border-radius: var(--radius-sm);
  font-size: 0.7rem; color: var(--text-secondary); overflow-x: auto;
  white-space: pre-wrap; word-break: break-all; max-height: 100px; overflow-y: auto;
  cursor: pointer; font-family: var(--font-mono); border: 1px solid var(--border-subtle);
}
.code-block.expanded { max-height: none; }
.expand-hint { color: var(--accent); font-size: 0.65rem; cursor: pointer; }

.final-answer {
  background: linear-gradient(135deg, rgba(5,150,105,0.06), rgba(5,150,105,0.02));
  border: 1px solid rgba(5,150,105,0.2);
  border-radius: var(--radius-sm);
  padding: 10px;
}
.final-text {
  color: var(--success) !important;
  font-weight: 500 !important;
}

.running-hint {
  display: flex; flex-direction: column; align-items: center;
  padding: 40px 20px; color: var(--text-muted); font-size: 0.85rem;
}
</style>
