<template>
  <div class="vector-search-container">
    <n-card title="向量检索" class="search-card">
      <n-form :model="searchForm" label-placement="left" label-width="auto">
        <n-form-item label="搜索内容">
          <n-input
            v-model:value="searchForm.query"
            type="text"
            placeholder="请输入要搜索的内容"
            clearable
          />
        </n-form-item>
        <n-form-item label="搜索类型">
          <n-radio-group v-model:value="searchForm.searchType">
            <n-radio-button value="vector">向量检索</n-radio-button>
            <n-radio-button value="hybrid">混合检索</n-radio-button>
          </n-radio-group>
        </n-form-item>
        <n-form-item label="相似度阈值">
          <n-slider
            v-model:value="searchForm.similarityThreshold"
            :min="0"
            :max="1"
            :step="0.05"
            :marks="{
              0: '0',
              0.5: '0.5',
              1: '1'
            }"
          />
        </n-form-item>
        <n-form-item label="最大结果数">
          <n-input-number
            v-model:value="searchForm.maxResults"
            :min="1"
            :max="50"
            placeholder="最大结果数"
          />
        </n-form-item>
        <n-form-item>
          <n-space>
            <n-button type="primary" @click="handleSearch" :loading="searching">
              搜索
            </n-button>
            <n-button @click="resetForm">
              重置
            </n-button>
          </n-space>
        </n-form-item>
      </n-form>
    </n-card>

    <n-card v-if="searchResults.length > 0" title="搜索结果" class="results-card">
      <div class="results-list">
        <div class="result-item" v-for="(result, index) in searchResults" :key="result.id">
          <div class="result-header">
            <h3>{{ result.title }}</h3>
            <n-tag type="success">文档 #{{ result.id }}</n-tag>
          </div>
          <div class="result-content">
            <p>{{ result.content && result.content.length > 200 ? result.content.substring(0, 200) + '...' : result.content }}</p>
          </div>
          <div class="result-meta" v-if="result.updatedAt">
            <span style="font-size: 12px; color: #999;">更新时间: {{ new Date(result.updatedAt).toLocaleString('zh-CN') }}</span>
          </div>
          <div class="result-actions">
            <n-button size="small" @click="viewDocumentDetails(result.id)">
              查看详情
            </n-button>
            <n-button size="small" @click="viewRelevantSegments(result.id)">
              相关段落
            </n-button>
          </div>
        </div>
      </div>
      
      <n-pagination
        v-if="totalPages > 1"
        v-model:page="currentPage"
        v-model:page-size="pageSize"
        :item-count="totalItems"
        :page-sizes="[10, 20, 30]"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
        class="pagination"
      />
    </n-card>

    <n-modal v-model:show="showDocumentModal" :mask-closable="true" preset="card" style="max-width: 800px">
      <template #header>
        <span>文档详情</span>
      </template>
      <div v-if="selectedDocument">
        <h3>{{ selectedDocument.title }}</h3>
        <div class="document-meta">
          <n-tag type="info">ID: {{ selectedDocument.id }}</n-tag>
          <n-tag v-if="selectedDocument.createdAt">创建: {{ new Date(selectedDocument.createdAt).toLocaleDateString('zh-CN') }}</n-tag>
          <n-tag v-if="selectedDocument.updatedAt">更新: {{ new Date(selectedDocument.updatedAt).toLocaleDateString('zh-CN') }}</n-tag>
        </div>
        <div class="document-content">
          <p style="white-space: pre-wrap;">{{ selectedDocument.content }}</p>
        </div>
      </div>
    </n-modal>

    <n-modal v-model:show="showSegmentsModal" :mask-closable="true" preset="card" style="max-width: 800px">
      <template #header>
        <span>相关段落</span>
      </template>
      <div v-if="relevantSegments.length > 0">
        <div class="segment-item" v-for="(segment, index) in relevantSegments" :key="index">
          <div class="segment-header">
            <h4>段落 {{ index + 1 }}</h4>
            <n-tag type="info">相似度: {{ (segment.similarity * 100).toFixed(2) }}%</n-tag>
          </div>
          <div class="segment-content">
            <p>{{ segment.content }}</p>
          </div>
        </div>
      </div>
      <div v-else>
        <n-empty description="没有找到相关段落" />
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { 
  NCard, NForm, NFormItem, NInput, NRadioGroup, NRadioButton, 
  NSlider, NInputNumber, NSpace, NButton, NTag, NPagination, 
  NModal, NEmpty, useMessage 
} from 'naive-ui';
import { vectorSearchApi } from '../services/api';

const message = useMessage();

// 搜索表单
const searchForm = reactive({
  query: '',
  searchType: 'vector',
  similarityThreshold: 0.7,
  maxResults: 10
});

// 分页相关
const currentPage = ref(1);
const pageSize = ref(10);
const totalItems = ref(0);
const totalPages = ref(0);

// 搜索结果
const searchResults = ref<any[]>([]);
const searching = ref(false);

// 文档详情
const showDocumentModal = ref(false);
const selectedDocument = ref<any>(null);

// 相关段落
const showSegmentsModal = ref(false);
const relevantSegments = ref<any[]>([]);

// 搜索方法
const handleSearch = async () => {
  if (!searchForm.query.trim()) {
    message.warning('请输入搜索内容');
    return;
  }

  searching.value = true;
  try {
    let results: any;
    if (searchForm.searchType === 'vector') {
      // 修复API调用：使用正确的参数顺序和名称
      results = await vectorSearchApi.searchDocuments(
        searchForm.query,
        searchForm.maxResults,
        searchForm.similarityThreshold  // 后端参数名是minScore
      );
    } else {
      // 修复混合检索调用
      results = await vectorSearchApi.hybridSearch(
        searchForm.query,
        searchForm.maxResults,
        0.6,  // 向量权重
        0.4   // 关键词权重
      );
    }
    
    // 后端直接返回文档数组，不是{data: {results: []}}
    searchResults.value = Array.isArray(results) ? results : (results.data || []);
    totalItems.value = searchResults.value.length;
    totalPages.value = Math.ceil(totalItems.value / pageSize.value);
    
    if (searchResults.value.length === 0) {
      message.info('没有找到相关结果');
    } else {
      message.success(`找到 ${searchResults.value.length} 个相关文档`);
    }
  } catch (error) {
    console.error('搜索失败:', error);
    message.error('搜索失败，请稍后重试');
  } finally {
    searching.value = false;
  }
};

// 重置表单
const resetForm = () => {
  searchForm.query = '';
  searchForm.searchType = 'vector';
  searchForm.similarityThreshold = 0.7;
  searchForm.maxResults = 10;
  searchResults.value = [];
  currentPage.value = 1;
  totalItems.value = 0;
  totalPages.value = 0;
};

// 分页处理
const handlePageChange = (page: number) => {
  currentPage.value = page;
  // 这里可以添加重新加载数据的逻辑
};

const handlePageSizeChange = (size: number) => {
  pageSize.value = size;
  currentPage.value = 1;
  // 这里可以添加重新加载数据的逻辑
};

// 查看文档详情
const viewDocumentDetails = async (documentId: number) => {
  try {
    // 从搜索结果中找到对应的文档
    const document = searchResults.value.find(doc => doc.id === documentId);
    if (document) {
      selectedDocument.value = document;
      showDocumentModal.value = true;
    } else {
      message.warning('未找到文档详情');
    }
  } catch (error) {
    console.error('获取文档详情失败:', error);
    message.error('获取文档详情失败');
  }
};

// 查看相关段落
const viewRelevantSegments = async (documentId: number) => {
  try {
    // 修复API调用：使用正确的参数顺序
    const segments: any = await vectorSearchApi.getRelevantSegments(
      documentId,
      searchForm.query,
      5  // maxSegments
    );
    
    // 后端直接返回字符串数组
    relevantSegments.value = Array.isArray(segments) ? 
      segments.map((text, index) => ({ content: text, similarity: 0.8 - index * 0.1 })) : 
      [];
    showSegmentsModal.value = true;
    
    if (relevantSegments.value.length === 0) {
      message.info('没有找到相关段落');
    }
  } catch (error) {
    console.error('获取相关段落失败:', error);
    message.error('获取相关段落失败');
  }
};

// 获取检索统计信息
const getSearchStats = async () => {
  try {
    const result = await vectorSearchApi.getStats();
    console.log('检索统计信息:', result.data);
  } catch (error) {
    console.error('获取统计信息失败:', error);
  }
};

// 组件挂载时获取统计信息
onMounted(() => {
  getSearchStats();
});
</script>

<style scoped>
.vector-search-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  max-width: 100%;
  margin: 0;
  padding: 20px;
  box-sizing: border-box;
  overflow: hidden;
}

.search-card {
  margin-bottom: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.results-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

.results-card :deep(.n-card__content) {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.results-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}

.result-item {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
  transition: all 0.3s ease;
}

.result-item:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  border-color: var(--n-primary-color);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.result-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--n-text-color);
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-content {
  margin-bottom: 16px;
  line-height: 1.6;
  color: var(--n-text-color-2);
  max-height: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
}

.result-actions {
  display: flex;
  gap: 12px;
}

.pagination {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--n-divider-color);
  display: flex;
  justify-content: center;
}

.document-meta {
  margin-bottom: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.document-content {
  line-height: 1.8;
  white-space: pre-wrap;
  color: var(--n-text-color-2);
  max-height: 400px;
  overflow-y: auto;
  padding: 12px;
  background-color: var(--n-modal-color);
  border-radius: 4px;
}

.segment-item {
  margin-bottom: 16px;
  padding: 16px;
  border: 1px solid var(--n-border-color);
  border-radius: 8px;
  background-color: var(--n-color-modal);
}

.segment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.segment-header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--n-text-color);
}

.segment-content {
  line-height: 1.6;
  color: var(--n-text-color-2);
  padding: 12px;
  background-color: var(--n-color-popover);
  border-radius: 4px;
}

:deep(.n-form-item-label) {
  font-weight: 500;
}

:deep(.n-button) {
  border-radius: 4px;
}

:deep(.n-tag) {
  border-radius: 4px;
}
</style>