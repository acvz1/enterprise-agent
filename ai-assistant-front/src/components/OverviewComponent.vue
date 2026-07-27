<script setup lang="ts">
import { NIcon } from 'naive-ui'
import {
  ArrowForwardOutline,
  CheckmarkCircleOutline,
  DocumentTextOutline,
  GitMergeOutline,
  LockClosedOutline,
  ServerOutline,
  SparklesOutline
} from '@vicons/ionicons5'

const metrics = [
  { label: 'Hybrid Hit@3', value: '93.3%', note: '命中率', tone: 'green' },
  { label: 'Hybrid Recall', value: '93.3%', note: '召回率', tone: 'blue' },
  { label: '平均检索延迟', value: '214 ms', note: '混合链路', tone: 'amber' },
  { label: 'MySQL 查询次数', value: '1 次', note: 'Top K 批量补全', tone: 'slate' }
]

const pipeline = [
  { title: '双路召回', text: 'Redis Vector + ES BM25', icon: GitMergeOutline },
  { title: '排名融合', text: 'RRF 消除分数量纲差异', icon: SparklesOutline },
  { title: '权威补全', text: 'MySQL 一次批量查询', icon: ServerOutline },
  { title: 'Agent 决策', text: '按需调用知识库工具', icon: CheckmarkCircleOutline },
  { title: '引用输出', text: '答案关联原文切片', icon: DocumentTextOutline }
]
</script>

<template>
  <div class="overview">
    <section class="overview-hero">
      <div class="hero-copy">
        <span class="hero-kicker">VERIFIED ENGINEERING EVIDENCE</span>
        <h2>让企业知识问答<br />从“能回答”走向“可解释”</h2>
        <p>
          最近一次离线评测显示，混合检索在语义召回与关键词匹配之间取得了更稳定的结果。
          指标来自项目真实测试，不是实时监控数据。
        </p>
      </div>
      <div class="hero-signal" aria-hidden="true">
        <div class="signal-orbit orbit-one"></div>
        <div class="signal-orbit orbit-two"></div>
        <div class="signal-core">
          <n-icon size="31"><git-merge-outline /></n-icon>
          <strong>RRF</strong>
          <span>Rank Fusion</span>
        </div>
        <span class="signal-node node-a">Redis</span>
        <span class="signal-node node-b">BM25</span>
        <span class="signal-node node-c">MySQL</span>
      </div>
    </section>

    <section class="metric-grid">
      <article v-for="metric in metrics" :key="metric.label" :class="['metric-card', metric.tone]">
        <div class="metric-top">
          <span>{{ metric.label }}</span>
          <i></i>
        </div>
        <strong>{{ metric.value }}</strong>
        <small>{{ metric.note }}</small>
      </article>
    </section>

    <section class="overview-grid">
      <article class="panel pipeline-panel">
        <div class="panel-heading">
          <div>
            <span>END-TO-END FLOW</span>
            <h3>核心运行链路</h3>
          </div>
          <div class="verified-badge">
            <n-icon><checkmark-circle-outline /></n-icon>
            已运行验证
          </div>
        </div>

        <div class="pipeline">
          <template v-for="(step, index) in pipeline" :key="step.title">
            <div class="pipeline-step">
              <div class="step-icon"><n-icon size="20"><component :is="step.icon" /></n-icon></div>
              <strong>{{ step.title }}</strong>
              <span>{{ step.text }}</span>
            </div>
            <n-icon v-if="index < pipeline.length - 1" class="pipeline-arrow" size="17">
              <arrow-forward-outline />
            </n-icon>
          </template>
        </div>
      </article>

      <article class="panel comparison-panel">
        <div class="panel-heading">
          <div>
            <span>RETRIEVAL EVALUATION</span>
            <h3>检索效果对比</h3>
          </div>
          <small>离线测试集</small>
        </div>

        <div class="comparison-head">
          <span>检索方式</span>
          <span>Hit@3</span>
          <span>Recall</span>
          <span>平均耗时</span>
        </div>
        <div class="comparison-row">
          <strong><i class="dot redis"></i>Redis Vector</strong>
          <span>80.0%</span>
          <span>76.7%</span>
          <span>116 ms</span>
        </div>
        <div class="comparison-row">
          <strong><i class="dot es"></i>ES BM25</strong>
          <span class="muted-cell">1/1</span>
          <span class="muted-cell">1/1</span>
          <span>52 ms</span>
        </div>
        <div class="comparison-row highlight">
          <strong><i class="dot hybrid"></i>Hybrid RRF</strong>
          <span>93.3%</span>
          <span>93.3%</span>
          <span>214 ms</span>
        </div>
        <p class="comparison-note">
          BM25 当前仅有 1 条独立样本，因此保留原始结果，不与完整评测集直接横向下结论。
        </p>
      </article>
    </section>

    <section class="capability-strip">
      <div class="capability-title">
        <n-icon size="20"><lock-closed-outline /></n-icon>
        <div>
          <strong>企业级回答边界</strong>
          <span>权限、工具调用和证据引用共同约束最终输出</span>
        </div>
      </div>
      <div class="capability-list">
        <span>JWT 鉴权</span>
        <span>qa:ask 权限</span>
        <span>Agent Tool</span>
        <span>Citation Trace</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.overview {
  height: 100%;
  padding: 24px 28px 32px;
  overflow-y: auto;
}

.overview-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(300px, 0.75fr);
  min-height: 250px;
  overflow: hidden;
  color: #eaf6f2;
  background:
    radial-gradient(circle at 15% 0%, rgba(73, 190, 157, 0.24), transparent 36%),
    linear-gradient(122deg, #123b36 0%, #0d2928 70%);
  border-radius: 20px;
  box-shadow: 0 20px 45px rgba(23, 59, 53, 0.12);
}

.hero-copy {
  position: relative;
  z-index: 1;
  padding: 36px 40px;
}

.hero-kicker,
.panel-heading span {
  color: #71c9b0;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.18em;
}

.hero-copy h2 {
  max-width: 580px;
  margin: 10px 0 14px;
  color: #fff;
  font-family: Georgia, 'Songti SC', serif;
  font-size: clamp(28px, 3vw, 40px);
  font-weight: 700;
  line-height: 1.22;
}

.hero-copy p {
  max-width: 600px;
  color: #a8c3bc;
  font-size: 13px;
  line-height: 1.8;
}

.hero-signal {
  position: relative;
  min-height: 250px;
}

.signal-core {
  position: absolute;
  top: 50%;
  left: 50%;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 116px;
  height: 116px;
  color: #0d302b;
  background: #91dec8;
  border: 9px solid rgba(255, 255, 255, 0.08);
  border-radius: 50%;
  box-shadow: 0 0 50px rgba(87, 215, 180, 0.28);
  transform: translate(-50%, -50%);
}

.signal-core strong {
  margin-top: 2px;
  font-size: 18px;
  letter-spacing: 0.08em;
}

.signal-core span {
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.signal-orbit {
  position: absolute;
  top: 50%;
  left: 50%;
  border: 1px solid rgba(139, 222, 200, 0.2);
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.orbit-one {
  width: 210px;
  height: 210px;
}

.orbit-two {
  width: 310px;
  height: 310px;
  border-style: dashed;
}

.signal-node {
  position: absolute;
  z-index: 2;
  padding: 5px 9px;
  color: #bde7dc;
  font-size: 9px;
  font-weight: 700;
  background: rgba(17, 70, 62, 0.86);
  border: 1px solid rgba(122, 208, 185, 0.25);
  border-radius: 999px;
}

.node-a {
  top: 24%;
  left: 11%;
}

.node-b {
  top: 25%;
  right: 10%;
}

.node-c {
  right: 20%;
  bottom: 17%;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin: 18px 0;
}

.metric-card {
  position: relative;
  padding: 18px 20px;
  overflow: hidden;
  background: var(--kb-surface);
  border: 1px solid var(--kb-line);
  border-radius: 15px;
}

.metric-card::after {
  position: absolute;
  right: -16px;
  bottom: -30px;
  width: 80px;
  height: 80px;
  content: '';
  background: currentColor;
  border-radius: 50%;
  opacity: 0.045;
}

.metric-card.green { color: #167a68; }
.metric-card.blue { color: #2563eb; }
.metric-card.amber { color: #c77a16; }
.metric-card.slate { color: #536a64; }

.metric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--kb-muted);
  font-size: 10px;
  font-weight: 700;
}

.metric-top i {
  width: 6px;
  height: 6px;
  background: currentColor;
  border-radius: 50%;
}

.metric-card strong {
  display: block;
  margin: 10px 0 2px;
  color: var(--kb-ink);
  font-family: Georgia, serif;
  font-size: 26px;
}

.metric-card small {
  color: var(--kb-muted);
  font-size: 9px;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(380px, 0.8fr);
  gap: 18px;
}

.panel {
  padding: 22px;
  background: var(--kb-surface);
  border: 1px solid var(--kb-line);
  border-radius: 16px;
}

.panel-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.panel-heading h3 {
  margin-top: 4px;
  color: var(--kb-ink);
  font-family: Georgia, 'Songti SC', serif;
  font-size: 18px;
  font-weight: 700;
}

.panel-heading small {
  color: var(--kb-muted);
  font-size: 10px;
}

.verified-badge {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 6px 9px;
  color: var(--kb-primary);
  font-size: 9px;
  font-weight: 700;
  background: var(--kb-primary-soft);
  border-radius: 999px;
}

.pipeline {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto 1fr auto 1fr auto 1fr;
  align-items: center;
}

.pipeline-step {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  text-align: center;
}

.step-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  margin-bottom: 9px;
  color: var(--kb-primary);
  background: var(--kb-primary-soft);
  border-radius: 12px;
}

.pipeline-step strong {
  color: var(--kb-ink);
  font-size: 11px;
}

.pipeline-step span {
  max-width: 110px;
  margin-top: 4px;
  color: var(--kb-muted);
  font-size: 8px;
  line-height: 1.45;
}

.pipeline-arrow {
  color: #adbfba;
}

.comparison-head,
.comparison-row {
  display: grid;
  grid-template-columns: minmax(125px, 1.35fr) repeat(3, minmax(58px, 0.65fr));
  align-items: center;
  gap: 8px;
}

.comparison-head {
  padding: 0 10px 8px;
  color: var(--kb-muted);
  font-size: 8px;
  font-weight: 700;
  text-transform: uppercase;
}

.comparison-row {
  min-height: 42px;
  padding: 0 10px;
  color: #4c625c;
  font-size: 10px;
  border-top: 1px solid #edf1ef;
}

.comparison-row strong {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--kb-ink);
  font-size: 10px;
}

.comparison-row.highlight {
  color: var(--kb-primary);
  background: #f1f8f6;
  border: 1px solid #d8ebe5;
  border-radius: 9px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}

.dot.redis { background: #e04f5f; }
.dot.es { background: #e6a329; }
.dot.hybrid { background: #167a68; }

.muted-cell {
  color: #83958f;
}

.comparison-note {
  margin: 12px 3px 0;
  color: var(--kb-muted);
  font-size: 8px;
  line-height: 1.5;
}

.capability-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-top: 18px;
  padding: 15px 18px;
  background: #eaf3f0;
  border: 1px solid #d9e8e3;
  border-radius: 14px;
}

.capability-title {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--kb-primary);
}

.capability-title div {
  display: flex;
  flex-direction: column;
}

.capability-title strong {
  color: var(--kb-ink);
  font-size: 11px;
}

.capability-title span {
  margin-top: 2px;
  color: var(--kb-muted);
  font-size: 9px;
}

.capability-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 7px;
}

.capability-list span {
  padding: 5px 8px;
  color: #315b52;
  font-size: 8px;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid #d4e5df;
  border-radius: 7px;
}

@media (max-width: 1180px) {
  .overview-hero {
    grid-template-columns: minmax(0, 1fr) 300px;
  }

  .overview-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .overview {
    padding: 18px;
  }

  .overview-hero {
    grid-template-columns: 1fr;
  }

  .hero-signal {
    display: none;
  }

  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .pipeline {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .pipeline-arrow {
    display: none;
  }

  .pipeline-step {
    flex-direction: row;
    gap: 10px;
    text-align: left;
  }

  .step-icon {
    flex: 0 0 auto;
    margin: 0;
  }

  .pipeline-step span {
    margin-left: auto;
  }

  .capability-strip {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
