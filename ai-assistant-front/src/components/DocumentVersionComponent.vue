<template>
  <div class="document-version-container">
    <div class="version-header">
      <h3>文档版本历史</h3>
      <n-button type="primary" @click="showCreateVersionModal = true">
        <template #icon>
          <n-icon><add-outline /></n-icon>
        </template>
        创建新版本
      </n-button>
    </div>
    
    <n-spin :show="loading">
      <n-empty v-if="versions.length === 0 && !loading" description="暂无版本历史" />
      
      <n-timeline v-if="versions.length > 0">
        <n-timeline-item
          v-for="version in versions"
          :key="version.id"
          :type="version.versionNumber === currentVersion ? 'success' : 'default'"
          :title="`版本 ${version.versionNumber}`"
          :content="version.changeSummary || '无变更摘要'"
          :time="formatDateTime(version.createdAt)"
        >
          <template #header>
            <div class="version-header-item">
              <span>版本 {{ version.versionNumber }}</span>
              <n-tag v-if="version.versionNumber === currentVersion" type="success" size="small">
                当前版本
              </n-tag>
            </div>
          </template>
          
          <div class="version-content">
            <div class="version-title">{{ version.title }}</div>
            <div class="version-summary">{{ version.changeSummary || '无变更摘要' }}</div>
            <div class="version-meta">
              <span>创建者: {{ version.createdBy || '未知' }}</span>
              <span>创建时间: {{ formatDateTime(version.createdAt) }}</span>
            </div>
            
            <div class="version-actions">
              <n-button size="small" @click="viewVersion(version)">
                <template #icon>
                  <n-icon><eye-outline /></n-icon>
                </template>
                查看
              </n-button>
              <n-button 
                size="small" 
                type="warning" 
                @click="revertToVersion(version)"
                :disabled="version.versionNumber === currentVersion"
              >
                <template #icon>
                  <n-icon><refresh-outline /></n-icon>
                </template>
                恢复到此版本
              </n-button>
              <n-button size="small" @click="compareVersion(version)">
                <template #icon>
                  <n-icon><git-compare-outline /></n-icon>
                </template>
                比较
              </n-button>
            </div>
          </div>
        </n-timeline-item>
      </n-timeline>
    </n-spin>
    
    <!-- 创建新版本模态框 -->
    <n-modal
      v-model:show="showCreateVersionModal"
      title="创建新版本"
      :mask-closable="false"
      preset="card"
      style="max-width: 500px"
    >
      <n-form
        ref="formRef"
        :model="versionForm"
        :rules="rules"
        label-placement="left"
        label-width="80"
        require-mark-placement="right-hanging"
      >
        <n-form-item label="变更摘要" path="changeSummary">
          <n-input
            v-model:value="versionForm.changeSummary"
            type="textarea"
            placeholder="请输入变更摘要"
            :rows="3"
          />
        </n-form-item>
        <n-form-item label="创建者" path="createdBy">
          <n-input
            v-model:value="versionForm.createdBy"
            placeholder="请输入创建者名称"
          />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCreateVersionModal = false">取消</n-button>
          <n-button type="primary" @click="createVersion" :loading="saving">
            创建
          </n-button>
        </n-space>
      </template>
    </n-modal>
    
    <!-- 查看版本模态框 -->
    <n-modal
      v-model:show="showViewVersionModal"
      :title="`查看版本 ${viewingVersion?.versionNumber}`"
      :mask-closable="false"
      preset="card"
      style="max-width: 800px"
    >
      <div v-if="viewingVersion">
        <n-form label-placement="left" label-width="80">
          <n-form-item label="标题">
            <n-input :value="viewingVersion.title" readonly />
          </n-form-item>
          <n-form-item label="内容">
            <n-input
              :value="viewingVersion.content"
              type="textarea"
              readonly
              :rows="10"
            />
          </n-form-item>
          <n-form-item label="变更摘要">
            <n-input :value="viewingVersion.changeSummary || '无变更摘要'" readonly />
          </n-form-item>
          <n-form-item label="创建者">
            <n-input :value="viewingVersion.createdBy || '未知'" readonly />
          </n-form-item>
          <n-form-item label="创建时间">
            <n-input :value="formatDateTime(viewingVersion.createdAt)" readonly />
          </n-form-item>
        </n-form>
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showViewVersionModal = false">关闭</n-button>
        </n-space>
      </template>
    </n-modal>
    
    <!-- 版本比较模态框 -->
    <n-modal
      v-model:show="showCompareModal"
      title="版本比较"
      :mask-closable="false"
      preset="card"
      style="max-width: 900px"
    >
      <div v-if="compareResult">
        <n-input
          :value="compareResult"
          type="textarea"
          readonly
          :rows="15"
        />
      </div>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showCompareModal = false">关闭</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue';
import {
  NButton,
  NIcon,
  NSpin,
  NEmpty,
  NTimeline,
  NTimelineItem,
  NModal,
  NForm,
  NFormItem,
  NInput,
  NSpace,
  NTag,
  useMessage
} from 'naive-ui';
import { AddOutline, EyeOutline, RefreshOutline, GitCompareOutline } from '@vicons/ionicons5';
import { documentApi } from '@/services/api';

const message = useMessage();

interface Props {
  documentId: number;
}

const props = defineProps<Props>();

interface DocumentVersion {
  id: number;
  versionNumber: number;
  title: string;
  content: string;
  changeSummary?: string;
  createdBy?: string;
  createdAt: string;
}

const versions = ref<DocumentVersion[]>([]);
const loading = ref(false);
const saving = ref(false);
const currentVersion = ref(0);
const showCreateVersionModal = ref(false);
const showViewVersionModal = ref(false);
const showCompareModal = ref(false);
const viewingVersion = ref<DocumentVersion | null>(null);
const compareResult = ref('');

const formRef = ref();
const versionForm = reactive({
  changeSummary: '',
  createdBy: ''
});

const rules = {
  changeSummary: {
    required: true,
    message: '请输入变更摘要',
    trigger: 'blur'
  },
  createdBy: {
    required: true,
    message: '请输入创建者名称',
    trigger: 'blur'
  }
};

// 获取文档版本列表
const fetchVersions = async () => {
  if (!props.documentId) return;
  
  loading.value = true;
  try {
    const response = await documentApi.getDocumentVersions(props.documentId);
    versions.value = (response as any) || [];
    
    // 获取当前版本号
    try {
      const currentVersionResponse = await documentApi.getCurrentVersion(props.documentId);
      currentVersion.value = (currentVersionResponse as any) || 0;
    } catch (error) {
      console.error('获取当前版本号失败:', error);
      currentVersion.value = 0;
    }
  } catch (error) {
    message.error('获取版本列表失败');
    console.error('获取版本列表错误:', error);
  } finally {
    loading.value = false;
  }
};

// 创建新版本
const createVersion = async () => {
  if (!formRef.value || !props.documentId) return;
  
  try {
    await formRef.value.validate();
    saving.value = true;
    
    await documentApi.createDocumentVersion(props.documentId, {
      changeSummary: versionForm.changeSummary,
      createdBy: versionForm.createdBy
    });
    
    message.success('版本创建成功');
    showCreateVersionModal.value = false;
    resetForm();
    await fetchVersions();
  } catch (error) {
    message.error('创建版本失败');
    console.error('创建版本错误:', error);
  } finally {
    saving.value = false;
  }
};

// 查看版本
const viewVersion = (version: DocumentVersion) => {
  viewingVersion.value = version;
  showViewVersionModal.value = true;
};

// 恢复到指定版本
const revertToVersion = async (version: DocumentVersion) => {
  if (!props.documentId) return;
  
  try {
    await documentApi.revertToVersion(props.documentId, version.versionNumber, {
      createdBy: '当前用户'
    });
    
    message.success(`已成功恢复到版本 ${version.versionNumber}`);
    await fetchVersions();
  } catch (error) {
    message.error('恢复版本失败');
    console.error('恢复版本错误:', error);
  }
};

// 比较版本
const compareVersion = async (version: DocumentVersion) => {
  if (!props.documentId || currentVersion.value === 0) return;
  
  try {
    const response = await documentApi.compareVersions(
      props.documentId,
      version.versionNumber,
      currentVersion.value
    );
    
    compareResult.value = (response as any) || '无差异';
    showCompareModal.value = true;
  } catch (error) {
    message.error('比较版本失败');
    console.error('比较版本错误:', error);
  }
};

// 格式化日期时间
const formatDateTime = (dateTime: string) => {
  if (!dateTime) return '';
  return new Date(dateTime).toLocaleString();
};

// 重置表单
const resetForm = () => {
  versionForm.changeSummary = '';
  versionForm.createdBy = '';
  if (formRef.value) {
    formRef.value.restoreValidation();
  }
};

// 监听documentId变化
watch(() => props.documentId, (newVal) => {
  if (newVal) {
    fetchVersions();
  }
}, { immediate: true });

onMounted(() => {
  if (props.documentId) {
    fetchVersions();
  }
});
</script>

<style scoped>
.document-version-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.version-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.version-header-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.version-content {
  margin-top: 8px;
}

.version-title {
  font-weight: bold;
  margin-bottom: 4px;
}

.version-summary {
  color: var(--n-text-color-2);
  margin-bottom: 8px;
}

.version-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--n-text-color-3);
  margin-bottom: 12px;
}

.version-actions {
  display: flex;
  gap: 8px;
}
</style>