<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { NButton, NIcon, NInput, NSelect, NSpin, useMessage } from 'naive-ui'
import {
  ArrowForwardOutline,
  BookOutline,
  CheckmarkCircleOutline,
  ChevronForwardOutline,
  CopyOutline,
  GitMergeOutline,
  LockClosedOutline,
  SendOutline,
  SparklesOutline,
  TerminalOutline
} from '@vicons/ionicons5'
import { aiApi } from '@/services/api'

interface RetrievalCitation {
  documentId: number
  chunkId: number
  chunkIndex: number
  documentTitle: string
  content: string
  fusionScore: number
  sources: string[]
}

interface AgentResponse {
  answer: string
  model: string
  toolUsed: boolean
  toolNames: string[]
  citations: RetrievalCitation[]
}

interface ChatMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
  response?: AgentResponse
  pending?: boolean
  failed?: boolean
}

const message = useMessage()
const inputMessage = ref('')
const loading = ref(false)
const selectedModel = ref('deepseek')
const modelOptions = ref([{ label: 'DeepSeek', value: 'deepseek' }])
const messagesContainer = ref<HTMLElement>()
const selectedResponse = ref<AgentResponse | null>(null)
const selectedMessageId = ref<number | null>(null)
let messageId = 0

const messages = ref<ChatMessage[]>([
  {
    id: ++messageId,
    role: 'assistant',
    content:
      '你好，我是企业知识 Agent。你可以询问制度、流程、产品服务或内部知识；需要企业资料时，我会主动检索知识库并给出原文依据。',
    timestamp: new Date()
  }
])

const promptSuggestions = [
  '员工申请购买固定资产需要经过哪些审批？',
  '工业通讯设备的保修期是多久？',
  '你好，请介绍一下你能做什么'
]

const evidenceCount = computed(() => selectedResponse.value?.citations?.length ?? 0)

const fetchAvailableModels = async () => {
  try {
    const response: any = await aiApi.getAvailableModels()
    const models: string[] = response?.models || []
    if (models.length === 0) return
    modelOptions.value = models.map((model) => ({
      label: model === 'deepseek' ? 'DeepSeek' : model,
      value: model
    }))
    selectedModel.value = response.default || models[0] || 'deepseek'
  } catch {
    // 模型列表不可用时继续使用项目默认模型，不阻断问答主链路。
  }
}

const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
}

const selectEvidence = (chatMessage: ChatMessage) => {
  if (chatMessage.role !== 'assistant' || !chatMessage.response) return
  selectedResponse.value = chatMessage.response
  selectedMessageId.value = chatMessage.id
}

const resolveErrorMessage = (error: any) => {
  const status = error?.response?.status
  if (status === 401) return '登录状态已失效，请重新登录后再试。'
  if (status === 403) return '当前账号没有知识库问答权限，需要 qa:ask 权限。'
  return error?.response?.data?.message || 'Agent 暂时无法完成回答，请检查后端服务与模型配置。'
}

const sendMessage = async (preset?: string) => {
  const question = (preset ?? inputMessage.value).trim()
  if (!question || loading.value) return

  messages.value.push({
    id: ++messageId,
    role: 'user',
    content: question,
    timestamp: new Date()
  })
  inputMessage.value = ''

  const assistantMessage: ChatMessage = {
    id: ++messageId,
    role: 'assistant',
    content: '',
    timestamp: new Date(),
    pending: true
  }
  messages.value.push(assistantMessage)
  loading.value = true
  await scrollToBottom()

  try {
    const response = (await aiApi.askAgent(
      question,
      selectedModel.value
    )) as unknown as AgentResponse
    assistantMessage.content = response.answer
    assistantMessage.response = {
      ...response,
      toolNames: response.toolNames || [],
      citations: response.citations || []
    }
    assistantMessage.pending = false
    selectedResponse.value = assistantMessage.response
    selectedMessageId.value = assistantMessage.id
  } catch (error: any) {
    assistantMessage.content = resolveErrorMessage(error)
    assistantMessage.pending = false
    assistantMessage.failed = true
    message.error(assistantMessage.content)
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

const copyAnswer = async (content: string) => {
  try {
    await navigator.clipboard.writeText(content)
    message.success('回答已复制')
  } catch {
    message.warning('浏览器未允许复制，请手动选择文本')
  }
}

const formatTime = (timestamp: Date) =>
  timestamp.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const sourceLabel = (source: string) => {
  if (source === 'REDIS_VECTOR') return 'Redis 语义召回'
  if (source === 'ELASTICSEARCH_BM25') return 'ES BM25'
  return source
}

onMounted(fetchAvailableModels)
</script>

<template>
  <div class="agent-layout">
    <section class="conversation-panel">
      <div class="conversation-toolbar">
        <div class="agent-identity">
          <div class="agent-icon"><n-icon size="21"><sparkles-outline /></n-icon></div>
          <div>
            <strong>Knowledge Agent</strong>
            <span><i></i>权限感知 · 引用可追溯</span>
          </div>
        </div>
        <n-select
          v-model:value="selectedModel"
          :options="modelOptions"
          :consistent-menu-width="false"
          size="small"
          class="model-select"
        />
      </div>

      <div ref="messagesContainer" class="message-list">
        <div class="conversation-date"><span>当前会话</span></div>

        <article
          v-for="chatMessage in messages"
          :key="chatMessage.id"
          :class="[
            'message-row',
            chatMessage.role,
            {
              selected: selectedMessageId === chatMessage.id,
              clickable: !!chatMessage.response,
              failed: chatMessage.failed
            }
          ]"
          @click="selectEvidence(chatMessage)"
        >
          <div v-if="chatMessage.role === 'assistant'" class="message-avatar assistant-avatar">
            <n-icon size="17"><sparkles-outline /></n-icon>
          </div>

          <div class="message-block">
            <div class="message-meta">
              <strong>{{ chatMessage.role === 'assistant' ? 'NEXUS Agent' : '你' }}</strong>
              <span>{{ formatTime(chatMessage.timestamp) }}</span>
            </div>

            <div class="message-bubble">
              <div v-if="chatMessage.pending" class="thinking">
                <n-spin size="small" />
                <span>Agent 正在判断是否需要调用企业知识库…</span>
              </div>
              <p v-else>{{ chatMessage.content }}</p>
            </div>

            <div v-if="chatMessage.response" class="answer-footer">
              <div class="answer-trace">
                <span :class="{ active: chatMessage.response.toolUsed }">
                  <n-icon><terminal-outline /></n-icon>
                  {{ chatMessage.response.toolUsed ? '已调用知识库' : '直接回答' }}
                </span>
                <span v-if="chatMessage.response.citations.length">
                  <n-icon><book-outline /></n-icon>
                  {{ chatMessage.response.citations.length }} 条证据
                </span>
                <span>{{ chatMessage.response.model }}</span>
              </div>
              <button
                type="button"
                class="copy-button"
                aria-label="复制回答"
                @click.stop="copyAnswer(chatMessage.content)"
              >
                <n-icon><copy-outline /></n-icon>
              </button>
            </div>
          </div>

          <div v-if="chatMessage.role === 'user'" class="message-avatar user-avatar">你</div>
        </article>
      </div>

      <div v-if="messages.length <= 1" class="prompt-suggestions">
        <button
          v-for="prompt in promptSuggestions"
          :key="prompt"
          type="button"
          @click="sendMessage(prompt)"
        >
          <span>{{ prompt }}</span>
          <n-icon><arrow-forward-outline /></n-icon>
        </button>
      </div>

      <div class="composer">
        <n-input
          v-model:value="inputMessage"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 4 }"
          placeholder="询问企业制度、业务流程或产品知识…"
          :disabled="loading"
          @keydown.enter.exact.prevent="sendMessage()"
        />
        <n-button
          type="primary"
          class="send-button"
          :loading="loading"
          :disabled="!inputMessage.trim()"
          aria-label="发送问题"
          @click="sendMessage()"
        >
          <template #icon><n-icon><send-outline /></n-icon></template>
        </n-button>
        <div class="composer-hint">
          <n-icon><lock-closed-outline /></n-icon>
          回答仅基于当前账号可访问的知识
          <span>Enter 发送</span>
        </div>
      </div>
    </section>

    <aside class="evidence-panel">
      <div class="evidence-header">
        <div>
          <span>EVIDENCE TRACE</span>
          <h2>回答依据</h2>
        </div>
        <div class="evidence-count">{{ evidenceCount }}</div>
      </div>

      <template v-if="selectedResponse">
        <section class="trace-summary">
          <div class="trace-status">
            <div :class="['trace-status-icon', { direct: !selectedResponse.toolUsed }]">
              <n-icon size="19">
                <git-merge-outline v-if="selectedResponse.toolUsed" />
                <checkmark-circle-outline v-else />
              </n-icon>
            </div>
            <div>
              <strong>{{ selectedResponse.toolUsed ? '知识库工具已执行' : '无需检索，直接回答' }}</strong>
              <span v-if="selectedResponse.toolNames.length">
                {{ selectedResponse.toolNames.join(' · ') }}
              </span>
              <span v-else>本轮没有调用外部工具</span>
            </div>
          </div>
        </section>

        <div v-if="selectedResponse.citations.length" class="citation-list">
          <article
            v-for="(citation, index) in selectedResponse.citations"
            :key="`${citation.documentId}-${citation.chunkId}`"
            class="citation-card"
          >
            <div class="citation-index">{{ String(index + 1).padStart(2, '0') }}</div>
            <div class="citation-main">
              <div class="citation-title">
                <div>
                  <span>DOCUMENT SOURCE</span>
                  <h3>{{ citation.documentTitle }}</h3>
                </div>
                <n-icon size="15"><chevron-forward-outline /></n-icon>
              </div>

              <p>{{ citation.content }}</p>

              <div class="citation-meta">
                <div class="source-tags">
                  <span v-for="source in citation.sources" :key="source">
                    {{ sourceLabel(source) }}
                  </span>
                </div>
                <span class="score">RRF {{ citation.fusionScore.toFixed(5) }}</span>
              </div>

              <div class="chunk-address">
                Doc #{{ citation.documentId }} · Chunk #{{ citation.chunkIndex }} · ID {{ citation.chunkId }}
              </div>
            </div>
          </article>
        </div>

        <div v-else class="empty-evidence compact">
          <div class="empty-symbol"><n-icon size="24"><checkmark-circle-outline /></n-icon></div>
          <strong>本轮无需企业知识</strong>
          <p>Agent 判断这是普通交流，因此没有执行检索，也不会伪造引用。</p>
        </div>
      </template>

      <div v-else class="empty-evidence">
        <div class="empty-symbol"><n-icon size="26"><book-outline /></n-icon></div>
        <strong>等待回答证据</strong>
        <p>选择一条已完成的 Agent 回答，这里会展示工具调用、检索来源、RRF 分数和原文切片。</p>
      </div>
    </aside>
  </div>
</template>

<style scoped>
.agent-layout {
  display: grid;
  grid-template-columns: minmax(500px, 1.48fr) minmax(340px, 0.82fr);
  height: 100%;
  min-height: 0;
  padding: 20px 24px 24px;
  gap: 18px;
}

.conversation-panel,
.evidence-panel {
  min-width: 0;
  min-height: 0;
  background: var(--kb-surface);
  border: 1px solid var(--kb-line);
  border-radius: 18px;
  box-shadow: 0 10px 30px rgba(25, 57, 50, 0.04);
}

.conversation-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.conversation-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 68px;
  padding: 12px 18px;
  border-bottom: 1px solid #e8eeeb;
}

.agent-identity {
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
  border-radius: 11px;
}

.agent-identity > div:last-child {
  display: flex;
  flex-direction: column;
}

.agent-identity strong {
  color: var(--kb-ink);
  font-size: 12px;
}

.agent-identity span {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 3px;
  color: var(--kb-muted);
  font-size: 9px;
}

.agent-identity i {
  width: 6px;
  height: 6px;
  background: #2eae88;
  border-radius: 50%;
  box-shadow: 0 0 0 3px #e4f5ef;
}

.model-select {
  width: 130px;
}

.message-list {
  flex: 1;
  min-height: 0;
  padding: 18px 22px;
  overflow-y: auto;
}

.conversation-date {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 20px;
  color: #9aaba6;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.conversation-date::before,
.conversation-date::after {
  flex: 1;
  height: 1px;
  content: '';
  background: #edf1ef;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 22px;
  padding: 3px;
  border: 1px solid transparent;
  border-radius: 14px;
  transition: background 140ms ease, border-color 140ms ease;
}

.message-row.user {
  justify-content: flex-end;
}

.message-row.clickable {
  cursor: pointer;
}

.message-row.clickable:hover,
.message-row.selected {
  background: #f8faf9;
  border-color: #e5ece9;
}

.message-row.failed .message-bubble {
  color: #a33b3b;
  background: #fff3f3;
}

.message-avatar {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
}

.assistant-avatar {
  color: #fff;
  background: var(--kb-navy);
}

.user-avatar {
  color: #365c52;
  font-size: 10px;
  font-weight: 800;
  background: #dce9e5;
}

.message-block {
  max-width: min(78%, 680px);
}

.message-row.user .message-block {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
}

.message-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 3px 5px;
}

.message-meta strong {
  color: var(--kb-ink);
  font-size: 10px;
}

.message-meta span {
  color: #a2b0ac;
  font-size: 8px;
}

.message-row.user .message-meta {
  flex-direction: row-reverse;
}

.message-bubble {
  padding: 12px 14px;
  color: #314944;
  background: #f1f5f3;
  border-radius: 4px 14px 14px 14px;
}

.message-row.user .message-bubble {
  color: #fff;
  background: var(--kb-primary);
  border-radius: 14px 4px 14px 14px;
}

.message-bubble p {
  margin: 0;
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.thinking {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 260px;
  color: var(--kb-muted);
  font-size: 10px;
}

.answer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 6px;
}

.answer-trace {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.answer-trace span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 7px;
  color: #6f837d;
  font-size: 8px;
  font-weight: 700;
  background: #f4f7f6;
  border: 1px solid #e3ebe7;
  border-radius: 999px;
}

.answer-trace span.active {
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
  border-color: #d2e9e2;
}

.copy-button {
  display: grid;
  place-items: center;
  width: 25px;
  height: 25px;
  color: #84968f;
  background: transparent;
  border: 0;
  border-radius: 7px;
  cursor: pointer;
}

.copy-button:hover {
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
}

.prompt-suggestions {
  display: flex;
  gap: 8px;
  padding: 0 22px 12px;
}

.prompt-suggestions button {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: space-between;
  min-width: 0;
  padding: 9px 10px;
  color: #516b64;
  text-align: left;
  background: #fafcfb;
  border: 1px solid #e2eae7;
  border-radius: 10px;
  cursor: pointer;
}

.prompt-suggestions button:hover {
  color: var(--kb-primary);
  border-color: #bcded4;
}

.prompt-suggestions span {
  overflow: hidden;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer {
  position: relative;
  padding: 12px 68px 24px 18px;
  border-top: 1px solid #e8eeeb;
}

.composer :deep(.n-input) {
  background: #f8faf9;
}

.composer :deep(.n-input__textarea-el) {
  min-height: 43px !important;
  padding: 11px 4px;
  line-height: 1.55;
}

.send-button {
  position: absolute;
  top: 15px;
  right: 18px;
  width: 42px;
  min-width: 42px;
  height: 38px;
}

.composer-hint {
  position: absolute;
  bottom: 6px;
  left: 20px;
  right: 19px;
  display: flex;
  align-items: center;
  gap: 4px;
  color: #91a19c;
  font-size: 8px;
}

.composer-hint span {
  margin-left: auto;
}

.evidence-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.evidence-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 68px;
  padding: 12px 18px;
  border-bottom: 1px solid #e8eeeb;
}

.evidence-header span,
.citation-title span {
  color: var(--kb-primary);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.15em;
}

.evidence-header h2 {
  margin-top: 2px;
  color: var(--kb-ink);
  font-family: Georgia, 'Songti SC', serif;
  font-size: 17px;
  font-weight: 700;
}

.evidence-count {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  color: var(--kb-primary);
  font-family: Georgia, serif;
  font-size: 14px;
  font-weight: 700;
  background: var(--kb-primary-soft);
  border-radius: 9px;
}

.trace-summary {
  padding: 14px 16px 0;
}

.trace-status {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px;
  background: #eef7f4;
  border: 1px solid #d8ebe5;
  border-radius: 12px;
}

.trace-status-icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 34px;
  height: 34px;
  color: #fff;
  background: var(--kb-primary);
  border-radius: 10px;
}

.trace-status-icon.direct {
  color: var(--kb-primary);
  background: #d7eee7;
}

.trace-status > div:last-child {
  display: flex;
  flex-direction: column;
}

.trace-status strong {
  color: var(--kb-ink);
  font-size: 10px;
}

.trace-status span {
  margin-top: 3px;
  color: var(--kb-muted);
  font-size: 8px;
}

.citation-list {
  flex: 1;
  min-height: 0;
  padding: 12px 16px 18px;
  overflow-y: auto;
}

.citation-card {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 9px;
  margin-bottom: 10px;
  padding: 12px;
  background: #fbfcfc;
  border: 1px solid #e3eae7;
  border-radius: 12px;
}

.citation-index {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  color: #507068;
  font-family: Georgia, serif;
  font-size: 10px;
  background: #e7efec;
  border-radius: 8px;
}

.citation-main {
  min-width: 0;
}

.citation-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.citation-title h3 {
  margin-top: 3px;
  overflow: hidden;
  color: var(--kb-ink);
  font-size: 11px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.citation-card p {
  display: -webkit-box;
  margin: 10px 0;
  overflow: hidden;
  color: #536a64;
  font-size: 9px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.citation-meta {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
}

.source-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.source-tags span {
  padding: 3px 6px;
  color: #496b62;
  font-size: 7px;
  font-weight: 700;
  background: #e9f1ee;
  border-radius: 5px;
}

.score {
  flex: 0 0 auto;
  color: var(--kb-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 8px;
  font-weight: 700;
}

.chunk-address {
  margin-top: 8px;
  padding-top: 7px;
  color: #9aaba6;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 7px;
  border-top: 1px dashed #e1e8e5;
}

.empty-evidence {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 32px;
  text-align: center;
}

.empty-evidence.compact {
  margin: 16px;
  background: #f8faf9;
  border: 1px dashed #dce6e2;
  border-radius: 14px;
}

.empty-symbol {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  margin-bottom: 13px;
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
  border-radius: 16px;
}

.empty-evidence strong {
  color: var(--kb-ink);
  font-family: Georgia, 'Songti SC', serif;
  font-size: 15px;
}

.empty-evidence p {
  max-width: 260px;
  margin-top: 7px;
  color: var(--kb-muted);
  font-size: 9px;
  line-height: 1.65;
}

@media (max-width: 1050px) {
  .agent-layout {
    grid-template-columns: minmax(0, 1fr) 320px;
    padding: 16px;
  }
}

@media (max-width: 820px) {
  .agent-layout {
    display: flex;
    flex-direction: column;
    overflow-y: auto;
  }

  .conversation-panel {
    min-height: 600px;
  }

  .evidence-panel {
    min-height: 420px;
  }

  .prompt-suggestions {
    flex-direction: column;
  }
}
</style>
