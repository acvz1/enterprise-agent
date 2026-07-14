<template>
  <div class="chat-container">
    <div class="chat-messages" ref="messagesContainer">
      <div
        v-for="(message, index) in messages"
        :key="index"
        :class="['message', message.type]"
      >
        <div class="message-avatar">
          <n-icon size="24" :color="message.type === 'user' ? '#2080f0' : '#18a058'">
            <person-outline v-if="message.type === 'user'" />
            <chatbubbles-outline v-else />
          </n-icon>
        </div>
        <div class="message-content">
          <div class="message-text">
            {{ message.content }}
            <n-spin v-if="loading && message.type === 'assistant' && index === messages.length - 1 && !message.content" size="small" style="display: inline-block; margin-left: 8px;" />
          </div>
          
          <!-- 评分显示（仅助手回答显示） -->
          <div v-if="message.type === 'assistant' && message.evaluation" class="evaluation-section">
            <div class="evaluation-header">
              <n-icon size="16" style="margin-right: 4px; color: #f0a020;">
                <stats-chart-outline />
              </n-icon>
              <span class="evaluation-title">问答质量评估</span>
              <n-tag 
                :type="getEvaluationTagType(message.evaluation.overallScore)" 
                size="small" 
                round
                style="margin-left: 8px;"
              >
                {{ message.evaluation.level }}
              </n-tag>
            </div>
            <div class="evaluation-scores">
              <div class="score-item">
                <span class="score-label">相关性</span>
                <n-progress 
                  type="line" 
                  :percentage="message.evaluation.relevanceScore * 100" 
                  :height="8"
                  :border-radius="4"
                  :show-indicator="false"
                  :color="getScoreColor(message.evaluation.relevanceScore)"
                />
                <span class="score-value">{{ (message.evaluation.relevanceScore * 100).toFixed(0) }}%</span>
              </div>
              <div class="score-item">
                <span class="score-label">完整性</span>
                <n-progress 
                  type="line" 
                  :percentage="message.evaluation.completenessScore * 100" 
                  :height="8"
                  :border-radius="4"
                  :show-indicator="false"
                  :color="getScoreColor(message.evaluation.completenessScore)"
                />
                <span class="score-value">{{ (message.evaluation.completenessScore * 100).toFixed(0) }}%</span>
              </div>
              <div class="score-item">
                <span class="score-label">幻觉程度</span>
                <n-progress 
                  type="line" 
                  :percentage="message.evaluation.hallucinationScore * 100" 
                  :height="8"
                  :border-radius="4"
                  :show-indicator="false"
                  :color="getHallucinationColor(message.evaluation.hallucinationScore)"
                />
                <span class="score-value">{{ (message.evaluation.hallucinationScore * 100).toFixed(0) }}%</span>
              </div>
              <div class="score-item overall">
                <span class="score-label">综合评分</span>
                <n-progress 
                  type="line" 
                  :percentage="message.evaluation.overallScore * 100" 
                  :height="12"
                  :border-radius="6"
                  :show-indicator="false"
                  :color="getScoreColor(message.evaluation.overallScore)"
                />
                <span class="score-value overall-value">{{ (message.evaluation.overallScore * 100).toFixed(0) }}%</span>
              </div>
            </div>
          </div>
          
          <div class="message-time">{{ formatTime(message.timestamp) }}</div>
        </div>
      </div>
    </div>
    <div class="chat-input">
      <n-select
        v-model:value="selectedModel"
        :options="modelOptions"
        placeholder="选择AI模型"
        style="width: 150px"
      />
      <n-input
        v-model:value="inputMessage"
        placeholder="请输入您的问题..."
        @keyup.enter="sendMessage"
        :disabled="loading"
      />
      <n-button
        type="primary"
        @click="sendMessage"
        :disabled="loading || !inputMessage.trim()"
        :loading="loading"
      >
        发送
      </n-button>
      <n-button @click="clearCache" :disabled="loading">
        <template #icon>
          <n-icon><trash-outline /></n-icon>
        </template>
        清除缓存
      </n-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue';
import { NInput, NButton, NIcon, NSpin, NSelect, NProgress, NTag, useMessage } from 'naive-ui';
import { PersonOutline, ChatbubblesOutline, TrashOutline, StatsChartOutline } from '@vicons/ionicons5';
import { aiApi } from '@/services/api';

const message = useMessage();

interface ChatMessage {
  content: string;
  type: 'user' | 'assistant';
  timestamp: Date;
  evaluation?: {  // 新增：评分数据
    evaluationId: number;
    relevanceScore: number;  // 相关性 0-1
    completenessScore: number;  // 完整性 0-1
    hallucinationScore: number;  // 幻觉 0-1
    overallScore: number;  // 综合评分 0-1
    level: string;  // 级别：优秀/良好/一般/较差
  };
}

interface ModelOption {
  label: string;
  value: string;
}

const messages = ref<ChatMessage[]>([]);
const inputMessage = ref('');
const loading = ref(false);
const messagesContainer = ref<HTMLElement>();
const selectedModel = ref('deepseek');
const modelOptions = ref<ModelOption[]>([]);
const sessionId = ref(`session-${Date.now()}`); // 生成会话ID
const messageUpdateScheduler = ref<ReturnType<typeof setTimeout> | null>(null); // 消息更新调度器

// 获取可用模型列表
const fetchAvailableModels = async () => {
  try {
    const response: any = await aiApi.getAvailableModels();
    // 确保response.models是一个数组
    const models = response.models || [];
    modelOptions.value = models.map((model: string) => ({
      label: model,
      value: model
    }));
    // 设置默认选中的模型
    if (modelOptions.value.length > 0) {
      selectedModel.value = response.default || modelOptions.value[0]?.value || 'deepseek';
    }
  } catch (error: any) {
    // 如果是 401/403 错误，静默失败（可能是未登录）
    if (error.response?.status === 401 || error.response?.status === 403) {
      console.log('未登录，跳过模型列表加载');
      return;
    }
    console.error('获取模型列表失败:', error);
    message.error('获取模型列表失败');
  }
};

// 发送消息
const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return;

  // 添加用户消息
  const userMessage: ChatMessage = {
    content: inputMessage.value,
    type: 'user',
    timestamp: new Date()
  };
  messages.value.push(userMessage);
  
  const question = inputMessage.value;
  inputMessage.value = '';
  loading.value = true;
  
  // 添加空的助手消息，用于流式显示
  const assistantMessage: ChatMessage = {
    content: '',
    type: 'assistant',
    timestamp: new Date()
  };
  messages.value.push(assistantMessage);
  
  // 滚动到底部
  await nextTick();
  scrollToBottom();
  
  try {
    // 使用流式API获取回答
    await aiApi.askQuestionStream(
      question,
      sessionId.value, // 传递sessionId
      // 处理流式消息 - 使用debounce减少DOM更新
      (message: string) => {
        // 更新最后一条助手消息
        const lastMessage = messages.value[messages.value.length - 1];
        if (lastMessage && lastMessage.type === 'assistant') {
          // 确保消息不为空且不是纯空白字符
          if (message && message.trim()) {
            lastMessage.content += message;
            
            // 优化：使用debounce免得频繁更新
            if (messageUpdateScheduler.value) {
              clearTimeout(messageUpdateScheduler.value);
            }
            
            messageUpdateScheduler.value = setTimeout(() => {
              // 批量更新Vue响应式数据
              messages.value = [...messages.value];
              
              // 滚动到下部以显示新内容
              nextTick(() => {
                scrollToBottom();
              });
              
              messageUpdateScheduler.value = null;
            }, 50); // 50ms批量更新一次以降低DOM更新频率
          }
        }
      },
      // 处理完成事件
      (fromCache: boolean, model?: string) => {
        loading.value = false;
        if (fromCache) {
          message.info('回答来自缓存');
        }
      },
      // 处理评分数据
      (evaluation: any) => {
        const lastMessage = messages.value[messages.value.length - 1];
        if (lastMessage && lastMessage.type === 'assistant') {
          lastMessage.evaluation = evaluation;
          // 强制更新
          messages.value = [...messages.value];
        }
      },
      // 传递选中的模型
      selectedModel.value
    );
  } catch (error) {
    message.error('发送消息失败，请重试');
    // 无法移除空的助手消息
    messages.value.pop();
    loading.value = false;
    
    // 清空消息更新调度器
    if (messageUpdateScheduler.value) {
      clearTimeout(messageUpdateScheduler.value);
      messageUpdateScheduler.value = null;
    }
  }
};

// 清除缓存
const clearCache = async () => {
  try {
    await aiApi.clearCache();
    
    // 重置sessionId
    sessionId.value = `session-${Date.now()}`;
    
    // 重置聊天记录到初始状态
    messages.value = [
      {
        content: '你好！我是AI助手，有什么可以帮助你的吗？',
        type: 'assistant',
        timestamp: new Date()
      }
    ];
    
    message.success('缓存已清除，聊天记录已重置');
  } catch (error) {
    message.error('清除缓存失败');
    console.error('清除缓存错误:', error);
  }
};

// 格式化时间
const formatTime = (date: Date) => {
  return date.toLocaleTimeString('zh-CN', { 
    hour: '2-digit', 
    minute: '2-digit' 
  });
};

// 滚动到底部
const scrollToBottom = () => {
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
  }
};

// 获取评分标签类型
const getEvaluationTagType = (score: number): 'success' | 'warning' | 'error' | 'info' => {
  if (score >= 0.8) return 'success';  // 优秀
  if (score >= 0.6) return 'info';     // 良好
  if (score >= 0.4) return 'warning';  // 一般
  return 'error';                      // 较差
};

// 获取评分颜色（绿色－黄色－红色）
const getScoreColor = (score: number): string => {
  if (score >= 0.8) return '#18a058';  // 绿色 - 优秀
  if (score >= 0.6) return '#2080f0';  // 蓝色 - 良好
  if (score >= 0.4) return '#f0a020';  // 黄色 - 一般
  return '#d03050';                     // 红色 - 较差
};

// 获取幻觉程度颜色（倒序：幻觉越低越好）
const getHallucinationColor = (score: number): string => {
  if (score >= 0.6) return '#d03050';  // 红色 - 严重幻觉
  if (score >= 0.4) return '#f0a020';  // 黄色 - 中等幻觉
  if (score >= 0.2) return '#2080f0';  // 蓝色 - 轻微幻觉
  return '#18a058';                     // 绿色 - 无幻觉
};

onMounted(async () => {
  // 获取可用模型列表
  await fetchAvailableModels();
  
  // 添加欢迎消息
  messages.value = [
    {
      content: '你好！我是AI助手，有什么可以帮助你的吗？',
      type: 'assistant',
      timestamp: new Date()
    }
  ];
});
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  background-color: var(--n-color);
  overflow: hidden;
  margin: 0;
  padding: 20px;
  box-sizing: border-box;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message {
  display: flex;
  max-width: 80%;
}

.message.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message.assistant {
  align-self: flex-start;
}

.message-avatar {
  margin: 0 12px;
  display: flex;
  align-items: flex-start;
  flex-shrink: 0;
}

.message-content {
  padding: 12px 16px;
  border-radius: 12px;
  background-color: var(--n-color-modal);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.message.user .message-content {
  background-color: #2080f0 !important;
  color: #ffffff !important;
}

.message.assistant .message-content {
  background-color: var(--n-color-modal);
  color: var(--n-text-color);
}

.message-text {
  margin-bottom: 8px;
  word-wrap: break-word;
  white-space: pre-wrap;
  line-height: 1.5;
  min-height: 1em;
  color: inherit;
}

.message.user .message-text {
  color: #ffffff !important;
}

.message.assistant .message-text {
  color: var(--n-text-color);
}

.message-time {
  font-size: 12px;
  opacity: 0.7;
  text-align: right;
  color: inherit;
}

.message.user .message-time {
  text-align: left;
  color: rgba(255, 255, 255, 0.8) !important;
}

.message.assistant .message-time {
  color: var(--n-text-color-disabled);
}

.chat-input {
  display: flex;
  padding: 16px 20px;
  border-top: 1px solid var(--n-divider-color);
  gap: 12px;
  background-color: var(--n-color);
}

.chat-input .n-select {
  flex-shrink: 0;
  width: 160px;
}

.chat-input .n-input {
  flex: 1;
}

.chat-input .n-button {
  border-radius: 6px;
}

/* 评分区域样式 */
.evaluation-section {
  margin-top: 12px;
  padding: 12px;
  background-color: rgba(0, 0, 0, 0.02);
  border-radius: 8px;
  border-left: 3px solid #f0a020;
}

.evaluation-header {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
  font-weight: 500;
}

.evaluation-title {
  font-size: 13px;
  color: var(--n-text-color-2);
}

.evaluation-scores {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.score-item {
  display: grid;
  grid-template-columns: 60px 1fr 45px;
  align-items: center;
  gap: 8px;
}

.score-item.overall {
  margin-top: 4px;
  padding-top: 8px;
  border-top: 1px solid rgba(0, 0, 0, 0.06);
}

.score-label {
  font-size: 12px;
  color: var(--n-text-color-3);
  white-space: nowrap;
}

.score-value {
  font-size: 12px;
  font-weight: 600;
  color: var(--n-text-color-2);
  text-align: right;
}

.score-value.overall-value {
  font-size: 14px;
  font-weight: 700;
}
</style>