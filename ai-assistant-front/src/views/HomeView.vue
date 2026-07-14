<script setup lang="ts">
import { NLayout, NLayoutHeader, NLayoutContent, NTabs, NTabPane, NSpace, NButton, NDropdown, type DropdownOption } from 'naive-ui';
import { useRouter } from 'vue-router';
import ChatComponent from '@/components/ChatComponent.vue';
import DocumentComponent from '@/components/DocumentComponent.vue';
import VectorSearchComponent from '@/components/VectorSearchComponent.vue';
import DashboardComponent from '@/components/DashboardComponent.vue';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const authStore = useAuthStore();

const handleLogout = () => {
  authStore.logout();
  router.push('/login');
};

const userOptions: DropdownOption[] = [
  {
    label: '登出',
    key: 'logout',
    props: {
      onClick: handleLogout
    }
  }
];
</script>

<template>
  <n-layout class="layout">
    <n-layout-header class="header" bordered>
      <div class="header-content">
        <h1>AI 知识库问答系统</h1>
        <n-space>
          <span style="color: white;">{{ authStore.username }}</span>
          <n-dropdown :options="userOptions" trigger="click">
            <n-button secondary>
              用户菜单
            </n-button>
          </n-dropdown>
        </n-space>
      </div>
    </n-layout-header>
    <n-layout-content class="content">
      <n-tabs type="line" animated class="main-tabs">
        <n-tab-pane name="chat" tab="AI 助手">
          <div class="tab-content">
            <ChatComponent />
          </div>
        </n-tab-pane>
        <n-tab-pane name="documents" tab="文档管理">
          <div class="tab-content">
            <DocumentComponent />
          </div>
        </n-tab-pane>
        <n-tab-pane name="vector-search" tab="向量检索">
          <div class="tab-content">
            <VectorSearchComponent />
          </div>
        </n-tab-pane>
        <n-tab-pane name="dashboard" tab="📊 数据分析">
          <div class="tab-content">
            <DashboardComponent />
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-layout-content>
  </n-layout>
</template>

<style scoped>
.layout {
  height: 100vh;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.header {
  background-color: var(--n-primary-color);
  color: white;
  padding: 0 24px;
  height: 60px;
  display: flex;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  position: sticky;
  top: 0;
  z-index: 10;
  width: 100%;
  box-sizing: border-box;
}

.header-content {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.content {
  flex: 1;
  padding: 0;
  height: calc(100vh - 60px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
}

.main-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
  width: 100%;
}

.main-tabs :deep(.n-tabs-nav) {
  padding: 0 24px;
  border-bottom: 1px solid var(--n-border-color);
  width: 100%;
  box-sizing: border-box;
}

.main-tabs :deep(.n-tabs-tab-wrapper) {
  padding: 12px 0;
}

.main-tabs :deep(.n-tabs-tab) {
  padding: 0 20px;
  font-weight: 500;
}

.tab-content {
  flex: 1;
  height: calc(100vh - 60px - 49px);
  overflow-y: auto;
  overflow-x: hidden;
  padding: 0;
  background-color: var(--n-body-color);
  width: 100%;
  box-sizing: border-box;
  display: flex;
  justify-content: stretch;
}
</style>
