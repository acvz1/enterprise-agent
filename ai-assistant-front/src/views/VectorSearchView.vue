<template>
  <div class="vector-search-container">
    <div class="search-section">
      <h1 class="page-title">向量检索</h1>
      <p class="page-description">使用向量检索技术，根据语义相似度查找相关文档</p>
      
      <div class="search-form">
        <div class="search-input-group">
          <input
            v-model="searchQuery"
            type="text"
            class="search-input"
            placeholder="输入搜索内容..."
            @keyup.enter="performSearch"
          />
          <button @click="performSearch" class="search-button" :disabled="isSearching">
            <span v-if="isSearching">搜索中...</span>
            <span v-else>搜索</span>
          </button>
        </div>
        
        <div class="search-options">
          <div class="option-group">
            <label>搜索类型：</label>
            <select v-model="searchType" class="option-select">
              <option value="vector">向量检索</option>
              <option value="hybrid">混合检索</option>
            </select>
          </div>
          
          <div class="option-group">
            <label>最大结果数：</label>
            <input
              v-model.number="maxResults"
              type="number"
              min="1"
              max="20"
              class="option-input"
            />
          </div>
          
          <div v-if="searchType === 'vector'" class="option-group">
            <label>最小相似度：</label>
            <input
              v-model.number="minScore"
              type="number"
              min="0"
              max="1"
              step="0.1"
              class="option-input"
            />
          </div>
          
          <div v-if="searchType === 'hybrid'" class="option-group">
            <label>向量权重：</label>
            <input
              v-model.number="vectorWeight"
              type="number"
              min="0"
              max="1"
              step="0.1"
              class="option-input"
            />
          </div>
          
          <div v-if="searchType === 'hybrid'" class="option-group">
            <label>关键词权重：</label>
            <input
              v-model.number="keywordWeight"
              type="number"
              min="0"
              max="1"
              step="0.1"
              class="option-input"
            />
          </div>
        </div>
      </div>
    </div>
    
    <div v-if="searchResults.length > 0" class="results-section">
      <h2 class="results-title">搜索结果 ({{ searchResults.length }})</h2>
      
      <div class="results-list">
        <div v-for="document in searchResults" :key="document.id" class="result-card">
          <div class="result-header">
            <h3 class="result-title">{{ document.title }}</h3>
            <span class="result-date">{{ formatDate(document.updatedAt) }}</span>
          </div>
          
          <div class="result-content">
            <p class="result-excerpt">{{ getExcerpt(document.content) }}</p>
          </div>
          
          <div class="result-footer">
            <button @click="showDocumentDetails(document)" class="view-button">
              查看详情
            </button>
            <button @click="findRelevantSegments(document)" class="segments-button">
              查找相关段落
            </button>
          </div>
          
          <div v-if="showSegments[document.id]" class="segments-section">
            <h4 class="segments-title">相关段落</h4>
            <div v-if="loadingSegments[document.id]" class="loading-segments">
              加载中...
            </div>
            <div v-else>
              <div v-for="(segment, index) in relevantSegments[document.id]" :key="index" class="segment-item">
                <p class="segment-text">{{ segment }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    
    <div v-else-if="hasSearched && !isSearching" class="no-results">
      <p>没有找到相关文档</p>
    </div>
    
    <!-- 文档详情弹窗 -->
    <div v-if="selectedDocument" class="modal-overlay" @click="closeDocumentDetails">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>{{ selectedDocument.title }}</h3>
          <button @click="closeDocumentDetails" class="close-button">×</button>
        </div>
        <div class="modal-body">
          <div class="document-meta">
            <span>创建时间: {{ formatDate(selectedDocument.createdAt) }}</span>
            <span>更新时间: {{ formatDate(selectedDocument.updatedAt) }}</span>
          </div>
          <div class="document-content">
            <pre>{{ selectedDocument.content }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { vectorSearchApi } from '@/services/api';

// 搜索参数
const searchQuery = ref('');
const searchType = ref('vector');
const maxResults = ref(5);
const minScore = ref(0.7);
const vectorWeight = ref(0.6);
const keywordWeight = ref(0.4);

// 搜索状态
const isSearching = ref(false);
const hasSearched = ref(false);
const searchResults = ref<any[]>([]);

// 文档详情
const selectedDocument = ref<any>(null);

// 相关段落
const showSegments = reactive<Record<number, boolean>>({});
const loadingSegments = reactive<Record<number, boolean>>({});
const relevantSegments = reactive<Record<number, string[]>>({});

// 执行搜索
const performSearch = async () => {
  if (!searchQuery.value.trim()) {
    return;
  }
  
  isSearching.value = true;
  hasSearched.value = true;
  searchResults.value = [];
  
  try {
    let results;
    
    if (searchType.value === 'vector') {
      results = await vectorSearchApi.searchDocuments(
        searchQuery.value,
        maxResults.value,
        minScore.value
      );
    } else {
      results = await vectorSearchApi.hybridSearch(
        searchQuery.value,
        maxResults.value,
        vectorWeight.value,
        keywordWeight.value
      );
    }
    
    searchResults.value = results as any;
  } catch (error) {
    console.error('搜索失败:', error);
    alert('搜索失败，请稍后重试');
  } finally {
    isSearching.value = false;
  }
};

// 查找相关段落
const findRelevantSegments = async (document: any) => {
  const documentId = document.id;
  
  // 切换显示状态
  showSegments[documentId] = !showSegments[documentId];
  
  if (!showSegments[documentId]) {
    return;
  }
  
  // 如果已经加载过，不再重复加载
  if (relevantSegments[documentId] && relevantSegments[documentId].length > 0) {
    return;
  }
  
  loadingSegments[documentId] = true;
  
  try {
    const segments = await vectorSearchApi.getRelevantSegments(
      documentId,
      searchQuery.value,
      3 // 最多返回3个相关段落
    );
    
    relevantSegments[documentId] = segments as any;
  } catch (error) {
    console.error('获取相关段落失败:', error);
    alert('获取相关段落失败，请稍后重试');
  } finally {
    loadingSegments[documentId] = false;
  }
};

// 显示文档详情
const showDocumentDetails = (document: any) => {
  selectedDocument.value = document;
};

// 关闭文档详情
const closeDocumentDetails = () => {
  selectedDocument.value = null;
};

// 获取内容摘要
const getExcerpt = (content: string) => {
  if (!content) return '';
  
  // 移除多余的空白字符
  const cleanContent = content.replace(/\s+/g, ' ').trim();
  
  // 如果内容长度小于200，直接返回
  if (cleanContent.length <= 200) {
    return cleanContent;
  }
  
  // 否则返回前200个字符并添加省略号
  return cleanContent.substring(0, 200) + '...';
};

// 格式化日期
const formatDate = (dateString: string) => {
  if (!dateString) return '';
  
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

// 组件挂载时获取向量检索统计信息
onMounted(async () => {
  try {
    const stats = await vectorSearchApi.getStats();
    console.log('向量检索统计信息:', stats);
  } catch (error) {
    console.error('获取向量检索统计信息失败:', error);
  }
});
</script>

<style scoped>
.vector-search-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.page-title {
  font-size: 2rem;
  font-weight: bold;
  margin-bottom: 10px;
  color: #333;
}

.page-description {
  color: #666;
  margin-bottom: 30px;
}

.search-form {
  background-color: #f9f9f9;
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 30px;
}

.search-input-group {
  display: flex;
  margin-bottom: 20px;
}

.search-input {
  flex: 1;
  padding: 12px;
  border: 1px solid #ddd;
  border-radius: 4px 0 0 4px;
  font-size: 16px;
}

.search-button {
  padding: 12px 24px;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
  font-size: 16px;
  transition: background-color 0.3s;
}

.search-button:hover {
  background-color: #45a049;
}

.search-button:disabled {
  background-color: #cccccc;
  cursor: not-allowed;
}

.search-options {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
}

.option-group {
  display: flex;
  align-items: center;
  gap: 8px;
}

.option-select,
.option-input {
  padding: 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
}

.results-title {
  font-size: 1.5rem;
  font-weight: bold;
  margin-bottom: 20px;
  color: #333;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.result-card {
  background-color: white;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.result-title {
  font-size: 1.2rem;
  font-weight: bold;
  margin: 0;
  color: #333;
}

.result-date {
  color: #666;
  font-size: 0.9rem;
}

.result-content {
  margin-bottom: 15px;
}

.result-excerpt {
  color: #555;
  line-height: 1.5;
  margin: 0;
}

.result-footer {
  display: flex;
  gap: 10px;
}

.view-button,
.segments-button {
  padding: 8px 16px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  transition: background-color 0.3s;
}

.view-button {
  background-color: #2196F3;
  color: white;
}

.view-button:hover {
  background-color: #0b7dda;
}

.segments-button {
  background-color: #FF9800;
  color: white;
}

.segments-button:hover {
  background-color: #e68a00;
}

.segments-section {
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #eee;
}

.segments-title {
  font-size: 1rem;
  font-weight: bold;
  margin-bottom: 10px;
  color: #333;
}

.loading-segments {
  color: #666;
  font-style: italic;
}

.segment-item {
  margin-bottom: 10px;
  padding: 10px;
  background-color: #f9f9f9;
  border-radius: 4px;
}

.segment-text {
  margin: 0;
  color: #555;
  line-height: 1.5;
}

.no-results {
  text-align: center;
  padding: 40px;
  color: #666;
  background-color: #f9f9f9;
  border-radius: 8px;
}

.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background-color: white;
  border-radius: 8px;
  width: 90%;
  max-width: 800px;
  max-height: 90vh;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px;
  border-bottom: 1px solid #ddd;
}

.modal-header h3 {
  margin: 0;
  color: #333;
}

.close-button {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
}

.close-button:hover {
  color: #333;
}

.modal-body {
  padding: 20px;
}

.document-meta {
  display: flex;
  gap: 20px;
  margin-bottom: 15px;
  color: #666;
  font-size: 0.9rem;
}

.document-content {
  background-color: #f9f9f9;
  padding: 15px;
  border-radius: 4px;
  white-space: pre-wrap;
  line-height: 1.5;
}
</style>