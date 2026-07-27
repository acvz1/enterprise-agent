<template>
  <div class="file-upload-container">
    <n-upload
      ref="uploadRef"
      v-model:file-list="fileList"
      :max="5"
      :disabled="uploading"
      :show-file-list="true"
      :on-before-upload="beforeUpload"
      :on-change="handleChange as any"
      :on-remove="handleRemove as any"
      :on-finish="handleFinish as any"
      :on-error="handleError as any"
      :default-upload="false"
      :custom-request="customUpload"
      accept=".pdf,.doc,.docx,.xls,.xlsx,.txt,.md,.csv"
    >
      <n-upload-dragger>
          <div style="margin-bottom: 12px">
            <n-icon size="48" :depth="3">
              <document-text-outline />
            </n-icon>
          </div>
          <n-text style="font-size: 16px">
            点击或拖动文件到该区域来上传
          </n-text>
          <n-p depth="3" style="margin: 8px 0 0 0">
            支持单个或批量上传，文件类型：PDF、Word、Excel、TXT，单次最多5个文件
          </n-p>
        </n-upload-dragger>
    </n-upload>
    
    <!-- 手动上传按钮 -->
    <div v-if="fileList.length > 0" class="manual-upload-button">
      <n-button type="primary" @click="submitUpload" :loading="uploading" :disabled="uploading">
        <template #icon>
          <n-icon><cloud-upload-outline /></n-icon>
        </template>
        开始上传
      </n-button>
    </div>
    
    <!-- 异步上传进度跟踪 -->
    <div v-if="asyncUploading" class="upload-progress">
      <n-space vertical style="width: 100%">
        <div v-for="upload in uploadingFiles" :key="upload.uploadId" class="progress-item">
          <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
            <n-text strong>{{ upload.fileName }}</n-text>
            <n-tag :type="getProgressTagType(upload.statusCode)">{{ upload.status }}</n-tag>
          </div>
          <n-progress type="line" :percentage="upload.percentage" :height="6" />
          <n-text depth="3" style="font-size: 12px;">{{ formatFileSize(upload.fileSize) }} - {{ upload.percentage }}%</n-text>
        </div>
      </n-space>
    </div>
    
    <!-- 同步上传进度 -->
    <div v-if="uploading && !asyncUploading" class="upload-progress">
      <n-progress type="line" :percentage="uploadProgress" :height="2" />
      <n-text depth="3">正在上传并解析文件...</n-text>
    </div>
    
    <div v-if="uploadResult" class="upload-result">
      <n-alert :type="uploadResult.success ? 'success' : 'error'" :title="uploadResult.title" closable>
        {{ uploadResult.message }}
      </n-alert>
    </div>
    
    <div v-if="parsedDocuments.length > 0" class="parsed-documents">
      <h3>解析结果</h3>
      <n-card v-for="(doc, index) in parsedDocuments" :key="index" class="parsed-document-item">
        <template #header>
          <div class="document-title">{{ doc.title }}</div>
        </template>
        <div class="document-meta">
          <n-tag :type="getFileTypeTagType(doc.fileType)" size="small">
            {{ doc.fileType }}
          </n-tag>
          <n-text depth="3" style="margin-left: 8px">
            {{ doc.fileSize }}
          </n-text>
        </div>
        <div class="document-content-preview">
          {{ doc.contentPreview }}
        </div>
        
        <!-- 分类和标签选择 -->
        <div class="category-tag-selection">
          <n-form-item label="分类" path="categoryIds">
            <n-select
              v-model:value="doc.categoryIds"
              multiple
              :options="categoryOptions"
              placeholder="请选择分类"
              clearable
            />
          </n-form-item>
          <n-form-item label="标签" path="tagIds">
            <n-select
              v-model:value="doc.tagIds"
              multiple
              :options="tagOptions"
              placeholder="请选择标签"
              clearable
            />
          </n-form-item>
        </div>
      </n-card>
      
      <!-- 添加确认提交按钮 -->
      <div class="upload-actions">
        <n-space justify="end">
          <n-button type="primary" @click="confirmUpload" :loading="uploading">
            <template #icon>
              <n-icon><checkmark-outline /></n-icon>
            </template>
            确认上传
          </n-button>
        </n-space>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import {
  NUpload,
  NUploadDragger,
  NIcon,
  NText,
  NP,
  NProgress,
  NAlert,
  NCard,
  NTag,
  NSpace,
  NButton,
  NFormItem,
  NSelect,
  useMessage
} from 'naive-ui';
import { CloudUploadOutline, DocumentTextOutline, CheckmarkOutline } from '@vicons/ionicons5';
import { documentApi } from '../services/api';

const message = useMessage();
const emit = defineEmits(['upload-success', 'upload-error']);

interface UploadRecord {
  uploadId: string;
  fileName: string;
  fileSize: number;
  percentage: number;
  status: string;
  statusCode: string;
  file?: File;
  errorMessage?: string;
}

interface ParsedDocument {
  uploadId?: string;
  id?: number;
  title: string;
  contentPreview?: string;
  fileType: string;
  fileSize: string;
  content?: string;
  categoryIds?: number[];
  tagIds?: number[];
}

const uploadRef = ref();
const fileList = ref([]);
const uploading = ref(false);
const asyncUploading = ref(false);
const uploadProgress = ref(0);
const uploadResult = ref<any>(null);
const parsedDocuments = ref<ParsedDocument[]>([]);
const categoryOptions = ref<any[]>([]);
const tagOptions = ref<any[]>([]);
const uploadingFiles = ref<UploadRecord[]>([]);
const pollIntervals = ref<Map<string, ReturnType<typeof setInterval>>>(new Map());

// 上传前检查
const beforeUpload = async ({ file }: any) => {
  // 检查文件类型是否支持
  try {
    const response = await documentApi.checkFileType(file.file);
    if (!(response as any).supported) {
      message.error(`不支持的文件类型: ${file.name}`);
      return false;
    }
    return true;
  } catch (error) {
    message.error('检查文件类型失败');
    return false;
  }
};

// 异步上传文件（推荐使用）
const uploadFileAsync = async (file: any) => {
  try {
    // 先创建一个临时记录，立即显示进度条（不等待文件上传完成）
    const tempUploadId = `temp-${Date.now()}`;
    const tempRecord: UploadRecord = {
      uploadId: tempUploadId,
      fileName: file.name,
      fileSize: file.size,
      percentage: 0,
      status: '文件上传中...',
      statusCode: 'UPLOADING',
      file: file.file
    };
    
    uploadingFiles.value.push(tempRecord);
    asyncUploading.value = true;
    console.log(`[上传开始] 文件: ${file.name}, 临时ID: ${tempUploadId}`);
    
    // 调用异步上传API（这里会等待文件传输完成）
    const response = await documentApi.uploadFileAsync(file.file);
    
    if ((response as any).uploadId) {
      const realUploadId = (response as any).uploadId;
      console.log(`[上传完成] 获得真实uploadId: ${realUploadId}`);
      
      // 更新临时记录为真实uploadId
      const recordIndex = uploadingFiles.value.findIndex(f => f.uploadId === tempUploadId);
      if (recordIndex !== -1) {
        const updatedRecord: UploadRecord = {
          ...tempRecord,
          uploadId: realUploadId,
          status: '上传中',
          statusCode: 'UPLOADING'
        };
        uploadingFiles.value[recordIndex] = updatedRecord;
        
        // 开始轮询进度
        pollUploadProgress(updatedRecord, realUploadId);
        
        return updatedRecord;
      }
    }
  } catch (error: any) {
    message.error('异步上传启动失败: ' + (error?.message || '未知错误'));
    console.error('异步上传错误:', error);
    // 删除临时记录
    const failedIndex = uploadingFiles.value.findIndex(f => f.fileName === file.name && f.statusCode === 'UPLOADING');
    if (failedIndex !== -1) {
      uploadingFiles.value.splice(failedIndex, 1);
    }
    if (uploadingFiles.value.length === 0) {
      asyncUploading.value = false;
    }
    throw error;
  }
};

// 轮询上传进度
const pollUploadProgress = (uploadRecord: UploadRecord, uploadId: string) => {
  // 如果已经有该uploadId的轮询，先清除
  if (pollIntervals.value.has(uploadId)) {
    clearInterval(pollIntervals.value.get(uploadId));
  }
  
  let failureCount = 0; // 连续失败次数
  let lastStatus = ''; // 记录上一次的状态，用于检测进度更新
  let lastPercentage = 0; // 记录上一次的百分比
  
  // 立即执行一次轮询（不等待200ms），确保能捕获早期进度
  const fetchProgress = async () => {
    try {
      const progress = await documentApi.getUploadProgress(uploadId);
      
      console.log(`[轮询] uploadId=${uploadId}, 响应数据:`, JSON.stringify(progress));
      
      // 如果响应是空Map，不漄乚轮询（表示后端还没有改一次进度）
      if (!progress || Object.keys(progress).length === 0) {
        console.debug(`[轮询] uploadId=${uploadId}, 后端数据不可用，轮询订阯续续，不更新UI`);
        return; // 不更新UI，但不订阯续续
      }
      
      // 重置失败计数（因为这次请求成功了）
      failureCount = 0;
      
      // 更新进度信息 - 使用对象赋值强制 Vue 更新
      // 注意：保留原有的 fileName 和 fileSize，后端响应不包含这些字段
      const updatedRecord = {
        ...uploadRecord,
        fileName: uploadRecord.fileName, // 保留原有值
        fileSize: uploadRecord.fileSize, // 保留原有值
        status: (progress as any).status,
        statusCode: (progress as any).statusCode,
        percentage: (progress as any).percentage,
        errorMessage: (progress as any).errorMessage
      };
      
      // 找到对应的上传记录并更新整个对象
      const recordIndex = uploadingFiles.value.findIndex(f => f.uploadId === uploadId);
      if (recordIndex !== -1) {
        uploadingFiles.value[recordIndex] = updatedRecord;
      }
      
      // 同步本地引用
      Object.assign(uploadRecord, updatedRecord);
      
      // 如果状态或百分比有更新，打印日志便于调试
      if (lastStatus !== uploadRecord.statusCode || lastPercentage !== uploadRecord.percentage) {
        console.log(`[进度更新] uploadId=${uploadId}, 状态: ${lastStatus} → ${uploadRecord.statusCode}, 百分比: ${lastPercentage}% → ${uploadRecord.percentage}%`);
        lastStatus = uploadRecord.statusCode;
        lastPercentage = uploadRecord.percentage;
      }
      
      // 如果上传完成或失败，停止轮询
      if ((progress as any).statusCode === 'COMPLETED' || (progress as any).statusCode === 'FAILED') {
        clearInterval(pollInterval);
        pollIntervals.value.delete(uploadId);
        
        if ((progress as any).statusCode === 'COMPLETED') {
          message.success(`${uploadRecord.fileName} 上传完成！`);
          
          // 文件处理完成，添加到解析结果
          // 仅检查是否已经存在，避免重复添加
          const existingDoc = parsedDocuments.value.find(d => d.uploadId === uploadId);
          if (!existingDoc) {
            const fileName = uploadRecord.fileName.split('.')[0] || 'document';
            const fileExt = uploadRecord.fileName.split('.').pop() || 'UNKNOWN';
            parsedDocuments.value.push({
              uploadId: uploadId,
              title: fileName,
              contentPreview: `${uploadRecord.fileName} 已成功上传和处理`,
              fileType: fileExt.toUpperCase(),
              fileSize: formatFileSize(uploadRecord.fileSize),
              categoryIds: [],
              tagIds: []
            });
          }
          
          uploadResult.value = {
            success: true,
            title: '上传完成',
            message: `${uploadRecord.fileName} 已成功上传、解析、分块并向量化`
          };
        } else if ((progress as any).statusCode === 'FAILED') {
          message.error(`${uploadRecord.fileName} 上传失败: ${(progress as any).errorMessage}`);
          uploadResult.value = {
            success: false,
            title: '上传失败',
            message: (progress as any).errorMessage || '文件处理失败'
          };
        }
        
        // 检查是否还有进行中的上传
        const hasActiveUploads = uploadingFiles.value.some(f => f.statusCode !== 'COMPLETED' && f.statusCode !== 'FAILED');
        if (!hasActiveUploads) {
          asyncUploading.value = false;
        }
      }
    } catch (error) {
      failureCount++;
      console.warn(`[轮询错误] uploadId=${uploadId}, 错误 (${failureCount}次): ${error}`);
      
      // 如果连续失败超过 10 次，则停止轮询
      if (failureCount > 10) {
        console.error(`[轮询停止] uploadId=${uploadId}, 因为持续失败`);
        clearInterval(pollInterval);
        pollIntervals.value.delete(uploadId);
      }
    }
  };
  
  // 立即执行一次
  fetchProgress();
  
  // 然后每50ms执行一次（改为更高频，以捕获快速的进度更新）
  const pollInterval = setInterval(fetchProgress, 50);
  
  pollIntervals.value.set(uploadId, pollInterval);
};

// 自定义上传方法（支持异步和同步两种方式）
const customUpload = async ({
  file,
  onFinish,
  onError
}: {
  file: any;
  onFinish: (file: any, response: any) => void;
  onError: (error: any) => void;
  onProgress?: (progress: any) => void;
}) => {
  // 使用新的异步上传方式
  try {
    const uploadRecord = await uploadFileAsync(file);
    onFinish(file, { uploadId: uploadRecord?.uploadId });
  } catch (error) {
    onError(error);
    emit('upload-error', error);
  }
};

// 手动提交上传
const submitUpload = () => {
  if (fileList.value.length === 0) {
    message.warning('请先选择要上传的文件');
    return;
  }
  
  // 使用uploadRef的submit方法触发上传
  if (uploadRef.value) {
    uploadRef.value.submit();
  }
};

// 文件变化
// @ts-ignore
const handleChange = ({ file, fileList: newFileList, event }) => {
  // 为文件对象设置ID，如果还没有ID的话
  if (!file.id) {
    file.id = `file-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
  
  // 更新文件列表，确保所有文件都有ID
  // @ts-ignore
  fileList.value = newFileList.map(f => {
    if (!f.id) {
      f.id = `file-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    }
    return f;
  });
  
  // 不再自动触发上传，等待用户点击"开始上传"按钮
};

// 文件移除
// @ts-ignore
const handleRemove = ({ file, fileList: newFileList }) => {
  fileList.value = newFileList;
};

// 处理上传完成事件
// @ts-ignore
const handleFinish = ({ file, response }) => {
  console.log('上传完成:', file.name, response ? response : '无响应数据');
  console.log('文件ID:', file.id);
  console.log('文件对象详情:', file);
  console.log('响应对象详情:', response);
  
  // 更新文件列表中的文件状态
  // @ts-ignore
  const fileIndex = fileList.value.findIndex(f => f.id === file.id);
  if (fileIndex !== -1) {
    (fileList.value as any)[fileIndex].status = 'finished';
    // 如果响应存在且包含URL，则更新文件URL
    if (response && response.url) {
      (fileList.value as any)[fileIndex].url = response.url;
    }
  }
};

// 上传错误
// @ts-ignore
const handleError = ({ file, event }) => {
  uploading.value = false;
  uploadProgress.value = 0;
  uploadResult.value = {
    success: false,
    title: '上传失败',
    message: '文件上传过程中发生错误'
  };
  emit('upload-error', event);
};

// 格式化文件大小
// @ts-ignore
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 Bytes';
  
  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
};

// 获取文件类型标签样式
// @ts-ignore
const getFileTypeTagType = (fileType) => {
  switch (fileType.toLowerCase()) {
    case 'pdf':
      return 'error';
    case 'doc':
    case 'docx':
      return 'info';
    case 'xls':
    case 'xlsx':
      return 'success';
    case 'txt':
      return 'warning';
    default:
      return 'default';
  }
};

// 根据上传状态获取进度条颜色
// @ts-ignore
const getProgressTagType = (statusCode) => {
  switch (statusCode) {
    case 'UPLOADING':
      return 'info';
    case 'PARSING':
      return 'default';
    case 'CHUNKING':
      return 'default';
    case 'EMBEDDING':
      return 'default';
    case 'COMPLETED':
      return 'success';
    case 'FAILED':
      return 'error';
    default:
      return 'default';
  }
};

// 确认上传
// @ts-ignore
const confirmUpload = async () => {
  // 发送上传成功事件，通知父组件刷新文档列表
  if (parsedDocuments.value.length > 0) {
    try {
      // 为every个文档设置分类和标签
      for (const doc of parsedDocuments.value) {
        if ((doc.categoryIds && doc.categoryIds.length > 0) || (doc.tagIds && doc.tagIds.length > 0)) {
          const docId = doc.id || 0;
          if (docId > 0) {
            await documentApi.setDocumentCategoriesAndTags(docId, {
              categoryIds: doc.categoryIds || [],
              tagIds: doc.tagIds || []
            });
          }
        }
      }
      
      message.success('文档已成功上传并保存到知识库');
      emit('upload-success', parsedDocuments.value);
      // 清空解析结果和文件列表
      parsedDocuments.value = [];
      fileList.value = [];
      uploadResult.value = null;
    } catch (error) {
      message.error('设置文档分类和标签失败');
      console.error('设置文档分类和标签错误:', error);
    }
  } else {
    message.warning('没有可确认的文档');
  }
};

// 获取所有分类
// @ts-ignore
const fetchCategories = async () => {
  try {
    const response = await documentApi.getAllCategories();
    categoryOptions.value = (response as any).map((category: any) => ({
      label: category.name,
      value: category.id
    }));
  } catch (error) {
    message.error('获取分类列表失败');
    console.error('获取分类列表错误:', error);
  }
};

// 获取所有标签
// @ts-ignore
const fetchTags = async () => {
  try {
    const response = await documentApi.getAllTags();
    tagOptions.value = (response as any).map((tag: any) => ({
      label: tag.name,
      value: tag.id
    }));
  } catch (error) {
    message.error('获取标签列表失败');
    console.error('获取标签列表错误:', error);
  }
};

// 组件挂载时获取分类和标签列表
onMounted(() => {
  fetchCategories();
  fetchTags();
});
</script>

<style scoped>
.file-upload-container {
  width: 100%;
}

.file-upload-container :deep(.n-upload-dragger) {
  padding: 32px 24px;
  color: #49665e;
  background:
    linear-gradient(rgba(248, 251, 250, 0.96), rgba(248, 251, 250, 0.96)),
    radial-gradient(circle at center, #cfe5de 1px, transparent 1px);
  background-size: auto, 12px 12px;
  border: 1px dashed #abcfc5;
  border-radius: 15px;
}

.file-upload-container :deep(.n-upload-dragger:hover) {
  background-color: #f0f8f5;
  border-color: var(--kb-primary);
}

.upload-progress {
  margin-top: 16px;
}

.progress-item {
  padding: 12px 14px;
  background: #f6f9f8;
  border: 1px solid #e0e9e5;
  border-radius: 11px;
}

.upload-result {
  margin-top: 16px;
}

.parsed-documents {
  margin-top: 24px;
}

.parsed-documents h3 {
  margin-bottom: 16px;
  font-size: 16px;
  font-weight: 500;
}

.parsed-document-item {
  margin-bottom: 16px;
  border: 1px solid var(--kb-line);
  box-shadow: none;
}

.document-title {
  font-weight: bold;
  font-size: 16px;
}

.document-meta {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.document-content-preview {
  color: var(--n-text-color-2);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.category-tag-selection {
  margin-top: 12px;
}

.upload-actions {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.manual-upload-button {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}
</style>
