import axios from 'axios';

// 创建axios实例
const api = axios.create({
  baseURL: '/api',
  timeout: 30000,  // 30秒超时，文件上传需要有需较長的超时
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    // 自动添加 JWT Token
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    
    // 如果是上传文件（FormData），删除默认的 Content-Type，让浏览器自动设置
    if (config.data instanceof FormData) {
      delete config.headers['Content-Type'];
    }
    return config;
  },
  (error) => {
    // 对请求错误做些什么
    return Promise.reject(error);
  }
);

// 响应拦截器
api.interceptors.response.use(
  (response) => {
    // 对响应数据做点什么
    return response.data;
  },
  (error) => {
    // 对响应错误做点什么
    if (error.response) {
      // 服务器返回了错误状态码
      console.error(`API请求错误 [杂好 ${error.response.status}]:`, error.response.data || error.message);
    } else if (error.request) {
      // 已发起请求但一直没有接到响应
      console.error('API请求错误 未接收到响应:', error.message);
    } else {
      // 其他错误
      console.error('API请求错误:', error.message);
    }
    return Promise.reject(error);
  }
);

// AI问答相关API
export const aiApi = {
  // Agent 问答：返回工具调用信息与可追溯引用
  askAgent: (question: string, model?: string) => {
    const payload: { question: string; model?: string } = { question };
    if (model) {
      payload.model = model;
    }
    return api.post('/ai/agent/ask', payload);
  },

  // 发送问题
  askQuestion: (question: string, model?: string) => {
    const payload: any = { question };
    if (model) {
      payload.model = model;
    }
    return api.post('/ai/ask', payload);
  },
  
  // 流式发送问题
  askQuestionStream: (
    question: string, 
    sessionId: string,
    onMessage: (message: string) => void, 
    onComplete: (fromCache: boolean, model?: string) => void, 
    onEvaluation?: (evaluation: any) => void,  // 新增：评分回调
    model?: string
  ) => {
    return new Promise<void>((resolve, reject) => {
      const payload: any = { question, sessionId };
      if (model) {
        payload.model = model;
      }
      
      // 首先发送POST请求启动流式响应
      const token = localStorage.getItem('accessToken');
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      };
      if (token) {
        headers.Authorization = `Bearer ${token}`;
      }
      
      fetch('/api/ai/ask-stream', {
        method: 'POST',
        headers,
        body: JSON.stringify(payload),
      })
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        const reader = response.body?.getReader();
        const decoder = new TextDecoder();
        
        if (!reader) {
          throw new Error('无法获取响应流');
        }
        
        let buffer = '';
        let lastUpdateTime = Date.now();
        const MIN_BATCH_INTERVAL = 50;  // 最少1个更新，踦不每个正文都更新DOM
        let pendingMessage = '';
        
        const processStream = () => {
          reader.read().then(({ done, value }) => {
            if (done) {
              // 处理缓冲区中剩余的数据
              if (buffer.trim()) {
                processEvent(buffer);
              }
              // 发送最后残余的文本
              if (pendingMessage) {
                onMessage(pendingMessage);
                pendingMessage = '';
              }
              resolve();
              return;
            }
            
            const chunk = decoder.decode(value, { stream: true });
            buffer += chunk;
            
            // 按"\n\n"（事件分隔符）分割事件
            const events = buffer.split('\n\n');
            
            // 保留最后一个不完整的事件
            buffer = events.pop() || '';
            
            // 处理完整的事件
            for (const event of events) {
              if (event.trim()) {
                processEvent(event);
              }
            }
            
            // 优化：不是每个token都更新DOM，而是每50ms更新一次
            const now = Date.now();
            if (now - lastUpdateTime > MIN_BATCH_INTERVAL && pendingMessage) {
              onMessage(pendingMessage);
              pendingMessage = '';
              lastUpdateTime = now;
            }
            
            processStream();
          }).catch(error => {
            console.error('读取流错误:', error);
            reject(error);
          });
        };
        
        // 处理单个 SSE 事件
        const processEvent = (eventText: string) => {
          const lines = eventText.trim().split('\n');
          let currentEvent = '';
          let currentData = '';
          
          for (const line of lines) {
            if (line.startsWith('event:')) {
              currentEvent = line.substring(6).trim();
            } else if (line.startsWith('data:')) {
              currentData = line.substring(5).trim();
            }
          }
          
          if (currentEvent && currentData) {
            if (currentEvent === 'message') {
              // 缓冲收集，不是需需实时更新
              pendingMessage += currentData;
            } else if (currentEvent === 'metadata') {
              try {
                // 元数据，立即传给爸组件
                const parsed = JSON.parse(currentData);
                onComplete(parsed.fromCache, parsed.model);
              } catch (e) {
                console.error('解析metadata错误:', e, currentData);
              }
            } else if (currentEvent === 'evaluation') {
              try {
                // 评分数据，调用回调
                const evaluationData = JSON.parse(currentData);
                if (onEvaluation) {
                  onEvaluation(evaluationData);
                }
              } catch (e) {
                console.error('解析evaluation错误:', e, currentData);
              }
            }
          }
        };
        
        processStream();
      })
      .catch(error => {
        console.error('流式请求错误:', error);
        reject(error);
      });
    });
  },
  
  // 获取可用模型列表
  getAvailableModels: () => {
    return api.get('/ai/models');
  },
  
  // 清除缓存
  clearCache: () => {
    return api.post('/ai/clear-cache');
  }
};

// 文档管理相关API
export const documentApi = {
  // 获取所有文档
  getAllDocuments: (params?: { page?: number; size?: number; sort?: string; direction?: string }) => {
    return api.get('/documents', { params });
  },
  
  // 添加文档
  addDocument: (document: { title: string; content: string }) => {
    return api.post('/documents', document);
  },
  
  // 更新文档
  updateDocument: (id: number, document: { title: string; content: string }) => {
    return api.put(`/documents/${id}`, document);
  },
  
  // 删除文档
  deleteDocument: (id: number) => {
    return api.delete(`/documents/${id}`);
  },
  
  // 上传并解析文件
  uploadAndParseFile: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    
    return api.post('/files/upload', formData, {
      // 不提供 headers，让浏览器自动设置 Content-Type
    });
  },
  
  // 异步上传文件（推荐使用）
  uploadFileAsync: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    
    return api.post('/files/upload-async', formData, {
      // 不提供 headers，让浏览器自动设置 Content-Type
      // axios会自动检测 FormData 並设置正确的内容类型
    });
  },
  
  // 查询上传进度
  getUploadProgress: (uploadId: string) => {
    return api.get(`/files/upload-progress/${uploadId}`);
  },
  
  // 检查文件类型是否支持
  checkFileType: (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    
    return api.post('/files/check-type', formData, {
      // 不提供 headers，让浏览器自动设置 Content-Type
    });
  },
  
  // 获取支持的文件类型
  getSupportedFileTypes: () => {
    return api.get('/documents/supported-types');
  },
  
  // 按标题搜索文档
  searchByTitle: (title: string) => {
    return api.get('/documents/search/title', { params: { title } });
  },
  
  // 按内容搜索文档
  searchByContent: (content: string) => {
    return api.get('/documents/search/content', { params: { content } });
  },
  
  // 获取文档当前版本号
  getCurrentVersion: (documentId: number) => {
    return api.get(`/documents/${documentId}/current-version`);
  },
  
  // 获取文档版本列表
  getDocumentVersions: (documentId: number) => {
    return api.get(`/documents/${documentId}/versions`);
  },
  
  // 创建文档新版本
  createDocumentVersion: (documentId: number, changeSummary: string) => {
    return api.post(`/documents/${documentId}/versions`, null, { params: { changeSummary } });
  },
  
  // 恢复到指定版本
  revertToVersion: (documentId: number, versionNumber: number) => {
    return api.post(`/documents/${documentId}/versions/${versionNumber}/revert`);
  },
  
  // 比较两个版本
  compareVersions: (documentId: number, versionNumber1: number, versionNumber2: number) => {
    return api.get(`/documents/${documentId}/versions/compare`, {
      params: { versionNumber1, versionNumber2 }
    });
  },

  // 获取所有分类
  getAllCategories: () => {
    return api.get('/categories');
  },

  // 获取所有标签
  getAllTags: () => {
    return api.get('/tags');
  },

  // 创建文档并设置分类和标签
  createDocumentWithCategoriesAndTags: (data: { title: string; content: string; categoryIds?: number[]; tagIds?: number[] }) => {
    return api.post('/documents/with-categories-tags', data);
  },

  // 设置文档的分类和标签
  setDocumentCategoriesAndTags: (documentId: number, data: { categoryIds?: number[]; tagIds?: number[] }) => {
    return api.post(`/documents/${documentId}/categories-tags`, data);
  },

  // 获取文档的分类名称列表
  getDocumentCategoryNames: (documentId: number) => {
    return api.get(`/documents/${documentId}/category-names`);
  },

  // 获取文档的标签名称列表
  getDocumentTagNames: (documentId: number) => {
    return api.get(`/documents/${documentId}/tag-names`);
  },

  // 根据分类ID查找文档
  getDocumentsByCategories: (categoryIds: number[]) => {
    return api.get('/documents/categories', { params: { categoryIds: categoryIds.join(',') } });
  },

  // 根据标签ID查找文档
  getDocumentsByTags: (tagIds: number[]) => {
    return api.get('/documents/tags', { params: { tagIds: tagIds.join(',') } });
  },

  // 高级搜索：根据标题、内容、分类和标签进行搜索
  advancedSearch: (params: {
    keyword?: string;
    categoryIds?: string;
    tagIds?: string;
    fileType?: string;
    startDate?: string;
    endDate?: string;
    page?: number;
    size?: number;
    sort?: string;
    direction?: string;
  }) => {
    const searchParams: any = {};
    if (params.keyword) {
      // 将关键词同时用于标题和内容搜索
      searchParams.title = params.keyword;
      searchParams.content = params.keyword;
    }
    
    // 处理分类ID
    if (params.categoryIds) {
      searchParams.categoryIds = params.categoryIds;
    }
    
    // 处理标签ID
    if (params.tagIds) {
      searchParams.tagIds = params.tagIds;
    }
    
    // 处理文件类型
    if (params.fileType) {
      searchParams.fileType = params.fileType;
    }
    
    if (params.startDate) searchParams.startDate = params.startDate;
    if (params.endDate) searchParams.endDate = params.endDate;
    if (params.page !== undefined) searchParams.page = params.page;
    if (params.size !== undefined) searchParams.size = params.size;
    if (params.sort) searchParams.sort = params.sort;
    if (params.direction) searchParams.direction = params.direction;

    return api.get('/documents/advanced-search', { params: searchParams });
  },
  
  // 重建向量索引
  rebuildVectorIndex: () => {
    return api.post('/documents/rebuild-vector-index');
  }
};

// 向量检索相关API
export const vectorSearchApi = {
  // 向量检索文档
  searchDocuments: (query: string, maxResults?: number, minScore?: number) => {
    const params: any = { query };
    if (maxResults !== undefined) params.maxResults = maxResults;
    if (minScore !== undefined) params.minScore = minScore;
    
    return api.get('/vector-search/documents', { params });
  },
  
  // 分页向量检索文档
  searchDocumentsPage: (query: string, page?: number, size?: number, sort?: string, direction?: string) => {
    const params: any = { query };
    if (page !== undefined) params.page = page;
    if (size !== undefined) params.size = size;
    if (sort !== undefined) params.sort = sort;
    if (direction !== undefined) params.direction = direction;
    
    return api.get('/vector-search/documents/page', { params });
  },
  
  // 混合检索文档
  hybridSearch: (query: string, maxResults?: number, vectorWeight?: number, keywordWeight?: number) => {
    const params: any = { query };
    if (maxResults !== undefined) params.maxResults = maxResults;
    if (vectorWeight !== undefined) params.vectorWeight = vectorWeight;
    if (keywordWeight !== undefined) params.keywordWeight = keywordWeight;
    
    return api.get('/vector-search/documents/hybrid', { params });
  },
  
  // 获取文档的相关段落
  getRelevantSegments: (documentId: number, query: string, maxSegments?: number) => {
    const params: any = { query };
    if (maxSegments !== undefined) params.maxSegments = maxSegments;
    
    return api.get(`/vector-search/documents/${documentId}/segments`, { params });
  },
  
  // 获取向量检索统计信息
  getStats: () => {
    return api.get('/vector-search/stats');
  }
};

// Analytics Dashboard 相关API
export const analyticsApi = {
  // 获取Dashboard统计数据
  getDashboardStats: () => {
    return api.get('/analytics/dashboard');
  },
  
  // 重置缓存统计
  resetCacheStats: () => {
    return api.post('/analytics/cache/reset');
  }
};

// 认证相关 API
export const authApi = {
  // 用户登录
  login: (username: string, password: string): Promise<any> => {
    return api.post('/auth/login', { username, password });
  },
  
  // 用户注册
  register: (data: { username: string; password: string; email: string; phone?: string; nickname?: string }): Promise<any> => {
    return api.post('/auth/register', data);
  },
  
  // 刷新Token
  refreshToken: (refreshToken: string): Promise<any> => {
    return api.post('/auth/refresh', { refreshToken });
  },
  
  // 测试公开接口
  testPublic: (): Promise<any> => {
    return api.get('/auth/test/public');
  }
};

export default api;
