<script setup lang="ts">
import { ref } from 'vue'
import { NButton, NIcon, NInput, NSpin, useMessage } from 'naive-ui'
import {
  ArrowForwardOutline,
  FlashOutline,
  GitMergeOutline,
  SearchOutline
} from '@vicons/ionicons5'
import { aiApi } from '@/services/api'

interface RetrievalHit {
  documentId: number
  chunkId: number
  chunkIndex: number
  documentTitle: string
  content: string
  fusionScore: number
  sources: string[]
}

interface RagResponse {
  answer: string
  model: string
  fromCache: boolean
  citations: RetrievalHit[]
}

const message = useMessage()
const query = ref('')
const loading = ref(false)
const result = ref<RagResponse | null>(null)

const presets = [
  '员工申请购买固定资产需要哪些审批？',
  '哪些情况不属于免费保修范围？',
  '匿名举报可以通过哪些渠道？'
]

const sourceLabel = (source: string) => {
  if (source === 'REDIS_VECTOR') return 'Redis Vector'
  if (source === 'ELASTICSEARCH_BM25') return 'ES BM25'
  return source
}

const runExperiment = async (preset?: string) => {
  const currentQuery = (preset ?? query.value).trim()
  if (!currentQuery || loading.value) return
  query.value = currentQuery
  loading.value = true
  result.value = null

  try {
    const response = (await aiApi.askQuestion(
      currentQuery,
      'deepseek'
    )) as unknown as RagResponse
    result.value = {
      ...response,
      citations: response.citations || []
    }
  } catch (error: any) {
    if (error?.response?.status === 403) {
      message.error('当前账号没有知识库检索权限')
    } else {
      message.error(error?.response?.data?.message || '检索实验执行失败')
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="lab-page">
    <section class="lab-query">
      <div class="lab-heading">
        <div>
          <span>HYBRID RETRIEVAL OBSERVATORY</span>
          <h2>用一次问题观察完整检索链路</h2>
          <p>该实验调用非 Agent 的 RAG 接口，便于单独查看召回来源、RRF 分数和最终证据。</p>
        </div>
        <div class="pipeline-labels">
          <span>Redis Vector</span>
          <n-icon><arrow-forward-outline /></n-icon>
          <span>ES BM25</span>
          <n-icon><arrow-forward-outline /></n-icon>
          <strong>RRF Fusion</strong>
        </div>
      </div>

      <div class="query-box">
        <n-input
          v-model:value="query"
          placeholder="输入一个需要检索企业文档的问题"
          size="large"
          clearable
          @keyup.enter="runExperiment()"
        >
          <template #prefix><n-icon><search-outline /></n-icon></template>
        </n-input>
        <n-button type="primary" size="large" :loading="loading" @click="runExperiment()">
          执行检索
        </n-button>
      </div>

      <div class="preset-row">
        <span>测试问题</span>
        <button v-for="preset in presets" :key="preset" type="button" @click="runExperiment(preset)">
          {{ preset }}
        </button>
      </div>
    </section>

    <div v-if="loading" class="lab-loading">
      <n-spin size="large" />
      <strong>正在执行双路召回与 RRF 融合</strong>
      <span>Redis Vector → Elasticsearch BM25 → MySQL Top K 补全</span>
    </div>

    <section v-else-if="result" class="lab-results">
      <article class="result-summary">
        <div class="summary-icon"><n-icon size="22"><git-merge-outline /></n-icon></div>
        <div>
          <span>RETRIEVAL RESULT</span>
          <h3>返回 {{ result.citations.length }} 条可引用证据</h3>
          <p>{{ result.fromCache ? '本次回答命中缓存' : '本次回答经过完整 RAG 链路生成' }} · {{ result.model }}</p>
        </div>
      </article>

      <div class="result-grid">
        <article
          v-for="(hit, index) in result.citations"
          :key="`${hit.documentId}-${hit.chunkId}`"
          class="hit-card"
        >
          <div class="hit-rank">
            <span>RANK</span>
            <strong>{{ String(index + 1).padStart(2, '0') }}</strong>
          </div>
          <div class="hit-body">
            <div class="hit-heading">
              <div>
                <span>DOCUMENT #{{ hit.documentId }}</span>
                <h3>{{ hit.documentTitle }}</h3>
              </div>
              <div class="fusion-score">
                <span>FUSION SCORE</span>
                <strong>{{ hit.fusionScore.toFixed(5) }}</strong>
              </div>
            </div>
            <p>{{ hit.content }}</p>
            <div class="hit-footer">
              <div>
                <span v-for="source in hit.sources" :key="source">
                  {{ sourceLabel(source) }}
                </span>
              </div>
              <small>Chunk {{ hit.chunkIndex }} · ID {{ hit.chunkId }}</small>
            </div>
          </div>
        </article>
      </div>

      <article class="rag-answer">
        <div>
          <n-icon size="18"><flash-outline /></n-icon>
          <strong>基于以上证据生成的回答</strong>
        </div>
        <p>{{ result.answer }}</p>
      </article>
    </section>

    <section v-else class="lab-empty">
      <div class="empty-visual">
        <span>V</span>
        <i></i>
        <span>K</span>
        <i></i>
        <strong>RRF</strong>
      </div>
      <h3>等待一次检索实验</h3>
      <p>上传真实企业文档后，使用上方问题观察不同检索来源如何共同进入最终排名。</p>
    </section>
  </div>
</template>

<style scoped>
.lab-page {
  height: 100%;
  padding: 20px 24px 28px;
  overflow-y: auto;
}

.lab-query {
  padding: 25px 28px 20px;
  color: #e6f3ef;
  background:
    radial-gradient(circle at 90% 0%, rgba(69, 185, 154, 0.2), transparent 30%),
    linear-gradient(120deg, #123b36 0%, #0c2927 100%);
  border-radius: 18px;
}

.lab-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 30px;
}

.lab-heading > div:first-child > span,
.result-summary > div > span,
.hit-heading > div > span {
  color: #6fc6ad;
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.lab-heading h2 {
  margin: 7px 0 4px;
  color: #fff;
  font-family: Georgia, 'Songti SC', serif;
  font-size: 23px;
  font-weight: 700;
}

.lab-heading p {
  color: #9cbab2;
  font-size: 10px;
}

.pipeline-labels {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 12px;
  color: #7ea59a;
  font-size: 8px;
  white-space: nowrap;
}

.pipeline-labels span,
.pipeline-labels strong {
  padding: 5px 7px;
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 6px;
}

.pipeline-labels strong {
  color: #88dbc4;
}

.query-box {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 9px;
  margin-top: 22px;
}

.query-box :deep(.n-input) {
  background: rgba(255, 255, 255, 0.96);
}

.preset-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 10px;
  overflow-x: auto;
}

.preset-row > span {
  flex: 0 0 auto;
  color: #688d84;
  font-size: 8px;
  font-weight: 700;
}

.preset-row button {
  flex: 0 0 auto;
  padding: 5px 8px;
  color: #9dbab2;
  font-size: 8px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 6px;
  cursor: pointer;
}

.preset-row button:hover {
  color: #d9f3ec;
  border-color: rgba(125, 218, 193, 0.3);
}

.lab-loading,
.lab-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  min-height: 360px;
  color: var(--kb-muted);
  text-align: center;
}

.lab-loading strong {
  margin-top: 15px;
  color: var(--kb-ink);
  font-family: Georgia, 'Songti SC', serif;
  font-size: 16px;
}

.lab-loading span {
  margin-top: 5px;
  font-size: 9px;
}

.lab-results {
  margin-top: 16px;
}

.result-summary {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 17px;
  background: #fff;
  border: 1px solid var(--kb-line);
  border-radius: 14px;
}

.summary-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
  border-radius: 12px;
}

.result-summary h3 {
  margin: 2px 0;
  color: var(--kb-ink);
  font-size: 13px;
}

.result-summary p {
  color: var(--kb-muted);
  font-size: 8px;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.hit-card {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--kb-line);
  border-radius: 14px;
}

.hit-rank {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding-top: 16px;
  color: #658078;
  background: #edf4f1;
  border-right: 1px solid #dbe7e2;
}

.hit-rank span {
  font-size: 7px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.hit-rank strong {
  margin-top: 4px;
  color: var(--kb-primary);
  font-family: Georgia, serif;
  font-size: 19px;
}

.hit-body {
  min-width: 0;
  padding: 14px;
}

.hit-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.hit-heading h3 {
  margin-top: 3px;
  overflow: hidden;
  color: var(--kb-ink);
  font-size: 11px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fusion-score {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-end;
  flex-direction: column;
}

.fusion-score span {
  color: #9aaba6;
  font-size: 6px;
  font-weight: 700;
}

.fusion-score strong {
  color: var(--kb-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 9px;
}

.hit-body > p {
  display: -webkit-box;
  margin: 12px 0;
  overflow: hidden;
  color: #536963;
  font-size: 9px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
}

.hit-footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 8px;
  padding-top: 9px;
  border-top: 1px dashed #e2e9e6;
}

.hit-footer > div {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.hit-footer span {
  padding: 3px 6px;
  color: #45685f;
  font-size: 7px;
  font-weight: 700;
  background: #e9f1ee;
  border-radius: 5px;
}

.hit-footer small {
  flex: 0 0 auto;
  color: #9aacA6;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 7px;
}

.rag-answer {
  margin-top: 12px;
  padding: 18px 20px;
  background: #fff;
  border: 1px solid var(--kb-line);
  border-radius: 14px;
}

.rag-answer > div {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--kb-primary);
}

.rag-answer strong {
  color: var(--kb-ink);
  font-size: 11px;
}

.rag-answer p {
  margin-top: 10px;
  color: #405a54;
  font-size: 10px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.empty-visual {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 17px;
}

.empty-visual span,
.empty-visual strong {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  color: #55736b;
  font-family: Georgia, serif;
  background: #e8f0ed;
  border-radius: 12px;
}

.empty-visual strong {
  width: 58px;
  color: #fff;
  font-size: 10px;
  background: var(--kb-primary);
}

.empty-visual i {
  width: 22px;
  height: 1px;
  background: #c7d6d1;
}

.lab-empty h3 {
  color: var(--kb-ink);
  font-family: Georgia, 'Songti SC', serif;
  font-size: 16px;
}

.lab-empty p {
  max-width: 380px;
  margin-top: 7px;
  font-size: 9px;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .lab-heading {
    flex-direction: column;
  }

  .pipeline-labels {
    margin-top: 0;
  }

  .result-grid {
    grid-template-columns: 1fr;
  }
}
</style>
