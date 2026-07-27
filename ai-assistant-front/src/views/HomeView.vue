<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { NAvatar, NDropdown, NIcon, type DropdownOption } from 'naive-ui'
import {
  AnalyticsOutline,
  BookOutline,
  ChevronDownOutline,
  GitNetworkOutline,
  LogOutOutline,
  SearchOutline,
  ShieldCheckmarkOutline,
  SparklesOutline
} from '@vicons/ionicons5'
import { useRouter } from 'vue-router'
import ChatComponent from '@/components/ChatComponent.vue'
import DocumentComponent from '@/components/DocumentComponent.vue'
import RetrievalLabComponent from '@/components/RetrievalLabComponent.vue'
import OverviewComponent from '@/components/OverviewComponent.vue'
import { useAuthStore } from '@/stores/auth'

type SectionKey = 'agent' | 'documents' | 'retrieval' | 'overview'

const router = useRouter()
const authStore = useAuthStore()
const activeSection = ref<SectionKey>('agent')

const navigation = [
  {
    key: 'agent' as const,
    label: '智能问答',
    eyebrow: 'KNOWLEDGE AGENT',
    description: '基于企业知识库进行权限感知、证据可追溯的问答',
    icon: SparklesOutline
  },
  {
    key: 'documents' as const,
    label: '知识库',
    eyebrow: 'KNOWLEDGE BASE',
    description: '管理企业文档、解析任务与索引生命周期',
    icon: BookOutline
  },
  {
    key: 'retrieval' as const,
    label: '检索实验室',
    eyebrow: 'RETRIEVAL LAB',
    description: '观察向量检索、关键词检索与混合召回结果',
    icon: SearchOutline
  },
  {
    key: 'overview' as const,
    label: '评测看板',
    eyebrow: 'SYSTEM EVALUATION',
    description: '查看系统架构、离线评测与关键工程指标',
    icon: AnalyticsOutline
  }
]

const componentMap = {
  agent: ChatComponent,
  documents: DocumentComponent,
  retrieval: RetrievalLabComponent,
  overview: OverviewComponent
}

const currentSection = computed(
  () => navigation.find((item) => item.key === activeSection.value) ?? navigation[0]!
)
const activeComponent = computed(() => componentMap[activeSection.value])
const userInitial = computed(() => (authStore.username || 'U').slice(0, 1).toUpperCase())

const userOptions: DropdownOption[] = [
  {
    label: '退出登录',
    key: 'logout',
    icon: () => h(NIcon, null, { default: () => h(LogOutOutline) })
  }
]

const handleUserAction = (key: string) => {
  if (key !== 'logout') return
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="workspace-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">
          <git-network-outline />
        </div>
        <div class="brand-copy">
          <strong>NEXUS</strong>
          <span>企业知识中枢</span>
        </div>
      </div>

      <div class="workspace-label">工作空间</div>
      <nav class="sidebar-nav" aria-label="主导航">
        <button
          v-for="item in navigation"
          :key="item.key"
          type="button"
          :class="['nav-item', { active: activeSection === item.key }]"
          @click="activeSection = item.key"
        >
          <n-icon size="20"><component :is="item.icon" /></n-icon>
          <span>{{ item.label }}</span>
          <i v-if="activeSection === item.key"></i>
        </button>
      </nav>

      <div class="sidebar-spacer"></div>

      <div class="security-note">
        <n-icon size="18"><shield-checkmark-outline /></n-icon>
        <div>
          <strong>企业级访问控制</strong>
          <span>所有请求均经过 JWT 鉴权</span>
        </div>
      </div>

      <n-dropdown
        :options="userOptions"
        trigger="click"
        placement="top-start"
        @select="handleUserAction"
      >
        <button class="user-card" type="button">
          <n-avatar round :size="36">{{ userInitial }}</n-avatar>
          <span class="user-copy">
            <strong>{{ authStore.username || '已登录用户' }}</strong>
            <small>{{ authStore.email || '企业成员' }}</small>
          </span>
          <n-icon size="16"><chevron-down-outline /></n-icon>
        </button>
      </n-dropdown>
    </aside>

    <main class="workspace-main">
      <header class="topbar">
        <div>
          <span class="section-eyebrow">{{ currentSection.eyebrow }}</span>
          <h1>{{ currentSection.label }}</h1>
          <p>{{ currentSection.description }}</p>
        </div>
        <div class="topbar-status">
          <span class="status-dot"></span>
          <div>
            <strong>RAG Pipeline</strong>
            <small>Redis · Elasticsearch · MySQL</small>
          </div>
        </div>
      </header>

      <section class="workspace-content">
        <KeepAlive>
          <component :is="activeComponent" />
        </KeepAlive>
      </section>
    </main>
  </div>
</template>

<style scoped>
.workspace-shell {
  display: grid;
  grid-template-columns: 248px minmax(0, 1fr);
  width: 100%;
  height: 100vh;
  min-height: 680px;
  overflow: hidden;
  background: var(--kb-canvas);
}

.sidebar {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 28px 18px 20px;
  color: #dcebe6;
  background:
    radial-gradient(circle at 10% 0%, rgba(46, 160, 136, 0.2), transparent 32%),
    linear-gradient(178deg, #102d2b 0%, #0b2221 100%);
  border-right: 1px solid rgba(255, 255, 255, 0.05);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 10px 34px;
}

.brand-mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  color: #102d2b;
  background: #79d5bd;
  border-radius: 12px;
  box-shadow: 0 10px 28px rgba(79, 200, 169, 0.22);
}

.brand-mark svg {
  width: 22px;
  height: 22px;
}

.brand-copy {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
}

.brand-copy strong {
  color: #fff;
  font-size: 17px;
  letter-spacing: 0.16em;
}

.brand-copy span {
  margin-top: 5px;
  color: #9ab4ad;
  font-size: 11px;
  letter-spacing: 0.08em;
}

.workspace-label {
  padding: 0 12px 10px;
  color: #6f9289;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  height: 46px;
  padding: 0 13px;
  color: #9eb8b1;
  background: transparent;
  border: 0;
  border-radius: 10px;
  cursor: pointer;
  transition: color 160ms ease, background 160ms ease, transform 160ms ease;
}

.nav-item:hover {
  color: #eef8f5;
  background: rgba(255, 255, 255, 0.055);
}

.nav-item.active {
  color: #fff;
  background: rgba(58, 170, 145, 0.17);
}

.nav-item.active i {
  position: absolute;
  right: 0;
  width: 3px;
  height: 22px;
  background: #79d5bd;
  border-radius: 4px 0 0 4px;
}

.nav-item span {
  font-size: 14px;
  font-weight: 600;
}

.sidebar-spacer {
  flex: 1;
}

.security-note {
  display: flex;
  gap: 10px;
  margin: 0 3px 14px;
  padding: 13px;
  color: #82aa9f;
  background: rgba(255, 255, 255, 0.035);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
}

.security-note strong,
.security-note span {
  display: block;
}

.security-note strong {
  margin-bottom: 3px;
  color: #c9ddd7;
  font-size: 11px;
}

.security-note span {
  font-size: 9px;
  line-height: 1.45;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  color: inherit;
  text-align: left;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  cursor: pointer;
}

.user-card :deep(.n-avatar) {
  color: #10332e;
  font-weight: 800;
  background: #93dfca;
}

.user-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.user-copy strong,
.user-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-copy strong {
  color: #eef8f5;
  font-size: 12px;
}

.user-copy small {
  margin-top: 3px;
  color: #789a91;
  font-size: 10px;
}

.workspace-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 104px;
  padding: 18px 34px;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid var(--kb-line);
  backdrop-filter: blur(12px);
}

.section-eyebrow {
  color: var(--kb-primary);
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.18em;
}

.topbar h1 {
  margin: 3px 0 2px;
  color: var(--kb-ink);
  font-family: Georgia, 'Songti SC', serif;
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
}

.topbar p {
  color: var(--kb-muted);
  font-size: 12px;
}

.topbar-status {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 218px;
  padding: 10px 14px;
  background: var(--kb-surface-soft);
  border: 1px solid var(--kb-line);
  border-radius: 12px;
}

.status-dot {
  width: 9px;
  height: 9px;
  background: #22a37f;
  border: 3px solid #d7f3ea;
  border-radius: 50%;
  box-sizing: content-box;
}

.topbar-status div {
  display: flex;
  flex-direction: column;
}

.topbar-status strong {
  color: var(--kb-ink);
  font-size: 11px;
}

.topbar-status small {
  margin-top: 2px;
  color: var(--kb-muted);
  font-size: 9px;
}

.workspace-content {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

@media (max-width: 900px) {
  .workspace-shell {
    grid-template-columns: 76px minmax(0, 1fr);
  }

  .sidebar {
    padding-inline: 10px;
  }

  .brand {
    justify-content: center;
    padding-inline: 0;
  }

  .brand-copy,
  .workspace-label,
  .nav-item span,
  .nav-item i,
  .security-note,
  .user-copy,
  .user-card > .n-icon {
    display: none;
  }

  .nav-item,
  .user-card {
    justify-content: center;
    padding-inline: 0;
  }

  .topbar {
    min-height: 92px;
    padding-inline: 20px;
  }

  .topbar-status {
    display: none;
  }
}
</style>
