<template>
  <div class="document-container">
    <div class="document-header">
      <h2>文档管理</h2>
      <n-space>
        <n-button type="primary" @click="showAddModal = true">
          <template #icon>
            <n-icon><add-outline /></n-icon>
          </template>
          添加文档
        </n-button>
        <n-button type="info" @click="showUploadModal = true">
          <template #icon>
            <n-icon><cloud-upload-outline /></n-icon>
          </template>
          上传文件
        </n-button>
        <n-button type="warning" @click="confirmRebuildIndex">
          <template #icon>
            <n-icon><refresh-outline /></n-icon>
          </template>
          重建向量索引
        </n-button>
      </n-space>
    </div>
    
    <!-- 搜索区域 -->
    <div class="search-section">
      <n-card>
  <n-form :model="searchForm" label-placement="left" label-width="80" :show-feedback="false">
    <n-grid :cols="24" :x-gap="24">
      <n-form-item-gi :span="8" label="关键词">
        <n-input v-model:value="searchForm.keyword" placeholder="请输入关键词" clearable>
          <template #prefix>
            <n-icon><search-outline /></n-icon>
          </template>
        </n-input>
      </n-form-item-gi>
      
      <n-form-item-gi :span="5" label="分类">
        <n-select v-model:value="searchForm.category" placeholder="请选择分类" clearable :options="categoryOptions" />
      </n-form-item-gi>
      
      <n-form-item-gi :span="5" label="标签">
        <n-select v-model:value="searchForm.tags" placeholder="请选择标签" multiple clearable :options="tagOptions" />
      </n-form-item-gi>
      
      <n-form-item-gi :span="6" label="操作">
        <n-space>
          <n-button type="primary" @click="handleSearch">
            <template #icon>
              <n-icon><search-outline /></n-icon>
            </template>
            搜索
          </n-button>
          <n-button @click="resetSearch">
            <template #icon>
              <n-icon><refresh-outline /></n-icon>
            </template>
            重置
          </n-button>
        </n-space>
      </n-form-item-gi>
      
      <n-form-item-gi :span="5" label="文件类型">
        <n-select v-model:value="searchForm.fileType" placeholder="请选择文件类型" clearable :options="fileTypeOptions" />
      </n-form-item-gi>
      
      <n-form-item-gi :span="8" label="上传日期">
        <n-date-picker
          v-model:value="searchForm.dateRange"
          type="daterange"
          clearable
          format="yyyy-MM-dd"
          placeholder="请选择日期范围"
        />
      </n-form-item-gi>
    </n-grid>
  </n-form>
</n-card>
    </div>
    
    <div class="document-list">
      <n-spin :show="loading">
        <n-empty v-if="documents.length === 0 && !loading" description="暂无文档" />
        
        <n-card v-for="doc in documents" :key="doc.id" class="document-item">
      <template #header>
        <div class="document-title">{{ doc.title }}</div>
      </template>
      <template #header-extra>
        <n-tag v-if="doc.category" type="info">{{ doc.category }}</n-tag>
        <n-tag v-if="doc.fileType" type="success">{{ doc.fileType }}</n-tag>
      </template>
      <div class="document-content">{{ doc.content }}</div>
      <template #footer>
        <div class="document-footer">
          <div class="document-tags" v-if="doc.tags && doc.tags.length > 0">
            <n-tag v-for="tag in doc.tags" :key="tag" size="small" round>{{ tag }}</n-tag>
          </div>
          <div class="document-meta" v-if="doc.createdAt || doc.updatedAt">
            <span v-if="doc.createdAt">创建: {{ formatDate(doc.createdAt) }}</span>
            <span v-if="doc.updatedAt">更新: {{ formatDate(doc.updatedAt) }}</span>
          </div>
        </div>
        <div class="document-actions">
          <n-button size="small" @click="editDocument(doc)">
            <template #icon>
              <n-icon><create-outline /></n-icon>
            </template>
            编辑
          </n-button>
          <n-button size="small" type="info" @click="viewVersions(doc)">
            <template #icon>
              <n-icon><git-branch-outline /></n-icon>
            </template>
            版本历史
          </n-button>
          <n-button size="small" type="error" @click="confirmDelete(doc)">
            <template #icon>
              <n-icon><trash-outline /></n-icon>
            </template>
            删除
          </n-button>
        </div>
      </template>
    </n-card>
        
        <!-- 分页 -->
        <div v-if="documents.length > 0" class="pagination-container">
          <n-pagination
            v-model:page="pagination.page"
            v-model:page-size="pagination.size"
            :item-count="pagination.total"
            :page-sizes="[10, 20, 50]"
            show-size-picker
            @update:page="handlePageChange"
            @update:page-size="handlePageSizeChange"
          />
        </div>
      </n-spin>
    </div>
    
    <!-- 添加/编辑文档模态框 -->
    <n-modal
      v-model:show="showAddModal"
      :title="editingDocument ? '编辑文档' : '添加文档'"
      :mask-closable="false"
      preset="card"
      style="max-width: 600px"
    >
      <n-form
        ref="formRef"
        :model="documentForm"
        :rules="rules"
        label-placement="left"
        label-width="80"
        require-mark-placement="right-hanging"
      >
        <n-form-item label="标题" path="title">
          <n-input
            v-model:value="documentForm.title"
            placeholder="请输入文档标题"
          />
        </n-form-item>
        <n-form-item label="内容" path="content">
          <n-input
            v-model:value="documentForm.content"
            type="textarea"
            placeholder="请输入文档内容"
            :rows="6"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showAddModal = false">取消</n-button>
          <n-button type="primary" @click="saveDocument" :loading="saving">
            {{ editingDocument ? '更新' : '添加' }}
          </n-button>
        </n-space>
      </template>
    </n-modal>
    
    <!-- 文件上传模态框 -->
    <n-modal
      v-model:show="showUploadModal"
      title="上传文件"
      :mask-closable="false"
      preset="card"
      style="max-width: 800px"
    >
      <file-upload-component
        @upload-success="handleUploadSuccess"
        @upload-error="handleUploadError"
      />
      <template #footer>
        <n-space justify="end">
          <n-button @click="showUploadModal = false">关闭</n-button>
        </n-space>
      </template>
    </n-modal>
    
    <!-- 删除确认对话框 -->
    <n-modal
      v-model:show="showDeleteModal"
      preset="dialog"
      title="确认删除"
      content="确定要删除这个文档吗？此操作不可恢复。"
      positive-text="确认"
      negative-text="取消"
      @positive-click="deleteDocument"
      @negative-click="showDeleteModal = false"
    />
    
    <!-- 版本历史模态框 -->
    <n-modal
      v-model:show="showVersionModal"
      :title="`文档版本历史 - ${selectedDocument?.title || ''}`"
      :mask-closable="false"
      preset="card"
      style="max-width: 1000px; max-height: 80vh"
    >
      <document-version-component
        v-if="selectedDocument"
        :document-id="selectedDocument.id"
      />
      <template #footer>
        <n-space justify="end">
          <n-button @click="showVersionModal = false">关闭</n-button>
        </n-space>
      </template>
    </n-modal>
    
    <!-- 重建向量索引确认对话框 -->
    <n-modal
      v-model:show="showRebuildModal"
      preset="dialog"
      title="确认重建向量索引"
      positive-text="确认重建"
      negative-text="取消"
      :positive-button-props="{ loading: rebuilding }"
      @positive-click="rebuildVectorIndex"
      @negative-click="showRebuildModal = false"
    >
      <n-space vertical>
        <p>此操作将执行以下步骤：</p>
        <ul style="margin: 0; padding-left: 20px;">
          <li>清空 Redis 中的旧向量索引</li>
          <li>重新处理所有文档并生成向量数据</li>
          <li>确保数据库与向量库数据同步</li>
        </ul>
        <p style="color: #f0a020; margin-top: 8px;">
          <strong>⚠️ 警告：</strong>此操作可能需要较长时间，建议在系统空闲时执行。
        </p>
      </n-space>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import {
  NButton,
  NIcon,
  NCard,
  NSpin,
  NEmpty,
  NModal,
  NForm,
  NFormItem,
  NInput,
  NSpace,
  NPagination,
  NInputGroup,
  NSelect,
  NGrid,
  NFormItemGi,
  NDatePicker,
  NTag,
  useMessage
} from 'naive-ui';
import { AddOutline, CreateOutline, TrashOutline, CloudUploadOutline, SearchOutline, GitBranchOutline, RefreshOutline } from '@vicons/ionicons5';
import { documentApi } from '@/services/api';
import FileUploadComponent from './FileUploadComponent.vue';
import DocumentVersionComponent from './DocumentVersionComponent.vue';

const message = useMessage();

interface Document {
  id: number;
  title: string;
  content: string;
  category?: string;
  tags?: string[];
  fileType?: string;
  createdAt?: string;
  updatedAt?: string;
}

const documents = ref<Document[]>([]);
const loading = ref(false);
const saving = ref(false);
const showAddModal = ref(false);
const showUploadModal = ref(false);
const showDeleteModal = ref(false);
const showVersionModal = ref(false);
const showRebuildModal = ref(false);
const rebuilding = ref(false);
const editingDocument = ref<Document | null>(null);
const documentToDelete = ref<Document | null>(null);
const selectedDocument = ref<Document | null>(null);

// 搜索相关
const searchKeyword = ref('');
const searchType = ref('title');
const searchTypeOptions = [
  { label: '按标题', value: 'title' },
  { label: '按内容', value: 'content' }
];

// 高级搜索相关
const searchForm = reactive({
  keyword: '',
  category: '',
  tags: [],
  fileType: '',
  dateRange: null as [number, number] | null
});

// 分类选项
const categoryOptions = ref<Array<{ id: number; label: string; value: string }>>([]);

// 标签选项
const tagOptions = ref<Array<{ id: number; label: string; value: string }>>([]);

// 文件类型选项
const fileTypeOptions = ref([
  { label: 'PDF', value: 'PDF' },
  { label: 'Word', value: 'Word' },
  { label: 'Excel', value: 'Excel' },
  { label: 'TXT', value: 'TXT' },
  { label: 'PPT', value: 'PPT' }
]);

// 获取分类和标签数据
const fetchCategoriesAndTags = async () => {
  try {
    // 获取分类数据
    const categoriesResponse = await documentApi.getAllCategories();
    if (categoriesResponse && Array.isArray(categoriesResponse)) {
      categoryOptions.value = categoriesResponse.map(category => ({
        id: category.id,
        label: category.name,
        value: category.name
      }));
    }
    
    // 获取标签数据
    const tagsResponse = await documentApi.getAllTags();
    if (tagsResponse && Array.isArray(tagsResponse)) {
      tagOptions.value = tagsResponse.map(tag => ({
        id: tag.id,
        label: tag.name,
        value: tag.name
      }));
    }
  } catch (error) {
    console.error('获取分类和标签数据失败:', error);
    // 如果获取失败，使用默认数据
    categoryOptions.value = [
      { id: 1, label: '技术文档', value: '技术文档' },
      { id: 2, label: '产品说明', value: '产品说明' },
      { id: 3, label: '用户手册', value: '用户手册' },
      { id: 4, label: '会议记录', value: '会议记录' },
      { id: 5, label: '项目文档', value: '项目文档' },
      { id: 6, label: '其他', value: '其他' }
    ];
    
    tagOptions.value = [
      { id: 1, label: '重要', value: '重要' },
      { id: 2, label: '待审核', value: '待审核' },
      { id: 3, label: '已归档', value: '已归档' },
      { id: 4, label: '草稿', value: '草稿' },
      { id: 5, label: '公开', value: '公开' },
      { id: 6, label: '内部', value: '内部' }
    ];
  }
};

// 分页相关
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
  sort: 'updatedAt',
  direction: 'desc'
});

const formRef = ref();
const documentForm = reactive({
  title: '',
  content: ''
});

const rules = {
  title: {
    required: true,
    message: '请输入文档标题',
    trigger: 'blur'
  },
  content: {
    required: true,
    message: '请输入文档内容',
    trigger: 'blur'
  }
};

// 获取所有文档
const fetchDocuments = async (isSearch = false) => {
  loading.value = true;
  try {
    let response;
    
    if (isSearch && (searchForm.keyword.trim() || searchForm.category || searchForm.tags.length > 0 || searchForm.fileType || searchForm.dateRange)) {
      // 执行高级搜索
      const searchParams: any = {};
      
      if (searchForm.keyword.trim()) {
        searchParams.keyword = searchForm.keyword;
      }
      
      // 将分类名称转换为ID
      if (searchForm.category) {
        const selectedCategory = categoryOptions.value.find(cat => cat.value === searchForm.category);
        if (selectedCategory && selectedCategory.id) {
          searchParams.categoryIds = selectedCategory.id.toString();
        }
      }
      
      // 将标签名称转换为ID
      if (searchForm.tags.length > 0) {
        const selectedTagIds = searchForm.tags.map(tagName => {
          const tag = tagOptions.value.find(t => t.value === tagName);
          return tag ? tag.id : null;
        }).filter(id => id !== null);
        
        if (selectedTagIds.length > 0) {
          searchParams.tagIds = selectedTagIds.join(',');
        }
      }
      
      // 添加文件类型参数
      if (searchForm.fileType) {
        searchParams.fileType = searchForm.fileType;
      }
      
      if (searchForm.dateRange) {
        searchParams.startDate = new Date(searchForm.dateRange[0]).toISOString();
        searchParams.endDate = new Date(searchForm.dateRange[1]).toISOString();
      }
      
      // 添加分页和排序参数
      searchParams.page = pagination.page - 1;
      searchParams.size = pagination.size;
      searchParams.sort = pagination.sort;
      searchParams.direction = pagination.direction;
      
      response = await documentApi.advancedSearch(searchParams);
      documents.value = (response as any).content || [];
      pagination.total = (response as any).totalElements || 0;
    } else {
      // 获取分页文档列表
      response = await documentApi.getAllDocuments({
        page: pagination.page - 1,
        size: pagination.size,
        sort: pagination.sort,
        direction: pagination.direction
      });
      // 后端返回的是Page<Document>对象
      documents.value = (response as any).content || [];
      pagination.total = (response as any).totalElements || 0;
    }
  } catch (error) {
    message.error('获取文档列表失败');
    console.error('获取文档列表错误:', error);
    documents.value = [];
    pagination.total = 0;
  } finally {
    loading.value = false;
  }
};

// 搜索文档
const handleSearch = () => {
  pagination.page = 1; // 重置到第一页
  fetchDocuments(true);
};

// 重置搜索
const resetSearch = () => {
  searchForm.keyword = '';
  searchForm.category = '';
  searchForm.tags = [];
  searchForm.fileType = '';
  searchForm.dateRange = null;
  pagination.page = 1;
  fetchDocuments(false);
};

// 处理页码变化
const handlePageChange = (page: number) => {
  pagination.page = page;
  fetchDocuments(false);
};

// 处理每页大小变化
const handlePageSizeChange = (pageSize: number) => {
  pagination.size = pageSize;
  pagination.page = 1; // 重置到第一页
  fetchDocuments(false);
};

// 处理文件上传成功
const handleUploadSuccess = (documents: any) => {
  message.success('文件上传成功');
  showUploadModal.value = false;
  // 刷新文档列表
  fetchDocuments(false);
};

// 处理文件上传错误
const handleUploadError = (error: any) => {
  message.error('文件上传失败');
  console.error('文件上传错误:', error);
};

// 编辑文档
const editDocument = (doc: Document) => {
  editingDocument.value = doc;
  documentForm.title = doc.title;
  documentForm.content = doc.content;
  showAddModal.value = true;
};

// 确认删除
const confirmDelete = (doc: Document) => {
  documentToDelete.value = doc;
  showDeleteModal.value = true;
};

// 删除文档
const deleteDocument = async () => {
  if (!documentToDelete.value) return;
  
  try {
    await documentApi.deleteDocument(documentToDelete.value.id);
    message.success('文档删除成功');
    await fetchDocuments(false);
  } catch (error) {
    message.error('删除文档失败');
    console.error('删除文档错误:', error);
  } finally {
    showDeleteModal.value = false;
    documentToDelete.value = null;
  }
};

// 保存文档
const saveDocument = async () => {
  if (!formRef.value) return;
  
  try {
    await formRef.value.validate();
    saving.value = true;
    
    if (editingDocument.value) {
      // 更新文档
      await documentApi.updateDocument(editingDocument.value.id, {
        title: documentForm.title,
        content: documentForm.content
      });
      message.success('文档更新成功');
    } else {
      // 添加文档
      await documentApi.addDocument({
        title: documentForm.title,
        content: documentForm.content
      });
      message.success('文档添加成功');
    }
    
    showAddModal.value = false;
    resetForm();
    await fetchDocuments(false);
  } catch (error) {
    message.error(editingDocument.value ? '更新文档失败' : '添加文档失败');
    console.error('保存文档错误:', error);
  } finally {
    saving.value = false;
  }
};

// 重置表单
const resetForm = () => {
  editingDocument.value = null;
  documentForm.title = '';
  documentForm.content = '';
  if (formRef.value) {
    formRef.value.restoreValidation();
  }
};

// 查看版本历史
const viewVersions = (doc: Document) => {
  selectedDocument.value = doc;
  showVersionModal.value = true;
};

// 格式化日期
const formatDate = (dateString: string) => {
  if (!dateString) return '';
  const date = new Date(dateString);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
};

// 确认重建向量索引
const confirmRebuildIndex = () => {
  showRebuildModal.value = true;
};

// 重建向量索引
const rebuildVectorIndex = async () => {
  rebuilding.value = true;
  try {
    const response = await documentApi.rebuildVectorIndex() as any;
    if (response.success) {
      message.success(`${response.message}，共处理 ${response.totalChunks} 个文档块`);
    } else {
      message.error(response.message || '重建失败');
    }
  } catch (error: any) {
    console.error('重建向量索引错误:', error);
    // 根据规范，对于可能因未登录导致的401/403错误，静默失败并记录日志
    if (error.response && (error.response.status === 401 || error.response.status === 403)) {
      console.warn('重建向量索引权限不足或未登录，错误已忽略');
      message.warning('操作需要管理员权限，请先登录');
    } else {
      message.error('重建向量索引失败，请稍后重试');
    }
  } finally {
    rebuilding.value = false;
    showRebuildModal.value = false;
  }
};

onMounted(() => {
  fetchDocuments(false);
  fetchCategoriesAndTags();
});
</script>

<style scoped>
.document-container {
  height: 100%;
  width: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px;
  box-sizing: border-box;
  overflow: hidden;
  margin: 0;
}

.document-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 0 4px;
}

.search-section {
  margin-bottom: 20px;
}

.document-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.document-item {
  margin-bottom: 16px;
  transition: all 0.3s ease;
  border-radius: 8px;
}

.document-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.document-title {
  font-weight: bold;
  font-size: 16px;
  color: var(--primary-color);
}

.document-content {
  color: var(--n-text-color-2);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  margin-top: 8px;
  max-height: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
}

.document-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.document-footer {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.document-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.document-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--n-text-color-3);
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  padding: 10px 0;
}
</style>