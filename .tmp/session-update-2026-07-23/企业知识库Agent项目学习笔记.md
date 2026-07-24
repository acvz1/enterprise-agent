# 企业知识库 Agent 项目学习笔记

> 只记录已经接触并能帮助理解项目主线的内容。一个概念保留一条因果链、必要代码和最多一个重要易错点。

## 快速索引

***项目整体***

- [000. 项目地图：系统骨架、调用链与文件职责](#000-项目地图系统骨架调用链与文件职责)

***文档异步入库主线***

- [001. MultipartFile：Spring 交给 Controller 的上传文件](#001-multipartfilespring-交给-controller-的上传文件)
- [002. uploadFileAsync：Controller 的四个职责](#002-uploadfileasynccontroller-的四个职责)
- [003. uploadId：一次后台任务的编号](#003-uploadid一次后台任务的编号)
- [004. DocumentProcessingService：先创建任务，再提交后台处理](#004-documentprocessingservice先创建任务再提交后台处理)
- [005. 线程池：让请求线程不必等待文档处理完成](#005-线程池让请求线程不必等待文档处理完成)
- [006. @Async：Spring 怎样把 Worker 方法提交给线程池](#006-asyncspring-怎样把-worker-方法提交给线程池)
- [013. DocumentChunkService：从正文到 MySQL 分块与 Redis 向量索引](#013-documentchunkservice从正文到-mysql-分块与-redis-向量索引)
- [014. 为什么 MySQL 和 Redis 都保存 chunk 信息](#014-为什么-mysql-和-redis-都保存-chunk-信息)

***向量检索主线***

- [007. 懒加载：为什么先保存 documentId 再查 Document](#007-懒加载为什么先保存-documentid-再查-document)
- [008. @PreAuthorize：进入接口前检查用户权限](#008-preauthorize进入接口前检查用户权限)

***二次开发：chunk 级检索与混合检索***

- [015. RetrievalHit DTO：普通 Java 对象、构造方法与 Spring 注入边界](#015-retrievalhit-dto普通-java-对象构造方法与-spring-注入边界)
- [016. Redis 分块元数据与 EmbeddingMatch 泛型链](#016-redis-分块元数据与-embeddingmatch-泛型链)
- [017. 从需求反推检索 DTO：一次真实的 N+1 发现与方案修正](#017-从需求反推检索-dto一次真实的-n1-发现与方案修正)
- [018. 双路检索中的 score、rank 与 RRF](#018-双路检索中的-scorerank-与-rrf)
- [019. Redis chunk 候选检索：从元数据写入到真实集成测试](#019-redis-chunk-候选检索从元数据写入到真实集成测试)
- [020. Elasticsearch BM25 候选检索：Mapping、写入、刷新与真实排名](#020-elasticsearch-bm25-候选检索mapping写入刷新与真实排名)

***RAG 问答主线***

- [009. askQuestion：从用户问题到知识库回答](#009-askquestion从用户问题到知识库回答)
- [010. ModelFactory 与 ModelConfig：从配置到模型客户端](#010-modelfactory-与-modelconfig从配置到模型客户端)
- [011. SSE 流式问答：从模型片段到浏览器事件](#011-sse-流式问答从模型片段到浏览器事件)
- [012. buildEnhancedPrompt：把历史、证据和当前问题组织为模型输入](#012-buildenhancedprompt把历史证据和当前问题组织为模型输入)



## 当前进度

**面试所需的项目阅读阶段已经完成，不需要继续无目的地逐文件阅读。**

已经掌握并在本笔记中形成逻辑链的内容：

```text
登录与权限
文档异步上传、解析、分块与向量入库
Redis 向量检索与当前混合检索
同步 / SSE 流式 RAG 问答
多模型客户端、会话记忆、回答缓存和规则评分
文档 CRUD、分类标签、版本、统计与监控
前端页面如何调用后端接口
```

下一阶段直接开始二次开发；只有遇到具体改造点时，才回到对应文件做定点阅读：

```text
Elasticsearch 文本检索与元数据过滤
  -> 统一 chunk 级检索结果
  -> Redis 向量召回 + Elasticsearch BM25 + RRF 融合
  -> 回答引用来源与检索过程可观测
  -> 最后再增加有限、可解释的 Agent 工具编排
```

---

### 000. 项目地图：系统骨架、调用链与文件职责

#### 先用一句话定义项目

这是一个 `Vue 3 + Spring Boot + MySQL + Redis Stack + LangChain4j` 实现的企业知识库 RAG 系统：用户把企业文档导入知识库，系统解析并切成 chunk，建立向量索引，问答时检索证据、拼接提示词并调用大模型，再通过 SSE 把答案逐段返回。

当前代码是**有完整业务外壳的 RAG 应用，不是真正的 Agent**。它已经具备文档管理、可靠异步入库、向量检索、会话记忆、JWT/RBAC、多模型、回答缓存、评分和监控；但模型只能执行后端预先写死的一条问答链，还不能自主选择工具、观察结果并循环决策。

#### 一张图记住系统边界

```text
浏览器 / Vue
  -> api.ts 统一发 HTTP、携带 JWT、解析 SSE
  -> Controller 处理 HTTP 边界和权限入口
  -> Service 编排业务流程
       -> Repository -> MySQL：文档、chunk 身份、用户、版本、任务、评分
       -> Redis Stack：384 维向量、回答缓存、会话记忆、统计计数
       -> 文件目录：异步任务可继续读取的原始上传文件
       -> 大模型 API / Ollama：根据 prompt 生成回答
  -> Controller / SseEmitter 把结果返回前端
```

分层不是为了背名词，而是为了定位问题：

```text
接口参数或状态码不对       -> Controller / DTO
业务步骤、事务和一致性不对 -> Service
SQL 查询或实体关系不对     -> Repository / Entity
线程池、Redis、JWT 不对     -> Config / Security
页面状态或请求不对         -> Vue 组件 / store / api.ts
```

#### 四条端到端业务主线

**1. 登录与权限：确定“你是谁、能做什么”**

```text
LoginView.vue
  -> auth.ts.login()
  -> api.ts /api/auth/login
  -> AuthController.login()
  -> AuthService.login()
  -> AuthenticationManager
  -> CustomUserDetailsService.loadUserByUsername()
  -> UserRepository 查询 User、Role、Permission
  -> PasswordEncoder 校验密码
  -> JwtTokenProvider 生成 accessToken / refreshToken
  -> 前端 localStorage 保存 token

后续请求
  -> api.ts 自动加 Authorization: Bearer ...
  -> JwtAuthenticationFilter 验证 token 并写入 SecurityContext
  -> @PreAuthorize 检查 document:read / write / delete
  -> 通过后才进入 Controller
```

这里要区分：JWT 解决身份，`Role -> Permission` 解决授权。当前 `DocumentController` 和 `VectorSearchController` 的权限控制较完整，但 `AiController`、分类、标签和版本接口缺少同等级的方法权限，是实际的权限边界缺口。

**2. 文档异步入库：把上传文件加工成可检索知识**

```text
FileUploadComponent.vue
  -> api.ts.uploadFileAsync(file)
  -> FileUploadController.uploadFileAsync()
  -> DocumentProcessingService.uploadFileAsync()
       -> DocumentFileStorage.store() 保存稳定文件
       -> UploadProgressRepository.save(PENDING)
       -> DocumentProcessingWorker.processFileAsync(uploadId)
  -> Controller 返回 HTTP 202、uploadId
  -> 前端根据 uploadId 轮询进度

taskExecutor 工作线程
  -> Worker 根据 uploadId 找到任务和稳定文件
  -> FileParseService.parseFile() 提取正文
  -> DocumentService.saveDocument(..., false) 保存 Document
  -> DocumentChunkService.processDocumentWithProgress()
       -> 递归切分：500 字符、50 字符重叠
       -> MySQL 保存 DocumentChunk 身份、顺序和原文
       -> AllMiniLmL6V2 把每段转成 384 维向量
       -> Redis document-embeddings 保存 Embedding + TextSegment
  -> UploadProgress 更新为 COMPLETED 或 FAILED
  -> Worker 删除临时稳定文件
```

这条链的核心不是 `@Async`，而是“HTTP 请求返回以后，后台仍能依靠 `uploadId + 持久文件 + UploadProgress` 独立完成工作”。当前没有失败重试和启动恢复，处理到一半失败时也可能留下部分 Document/chunk。

**3. 检索：把问题变成能够交给模型的证据**

```text
query 文本
  -> VectorSearchService.searchDocuments()
  -> AllMiniLmL6V2 把 query 转成同样的 384 维向量
  -> Redis HNSW 近似最近邻搜索
  -> 返回相似 TextSegment
  -> DocumentChunkRepository.findByContentContaining(segment.text)
  -> 找回 documentId
  -> DocumentRepository.findById(documentId)
  -> 返回完整 Document 列表
```

`hybridSearch()` 当前只是“Redis 向量结果 + MySQL 标题/正文模糊匹配 + 加权 RRF”，并没有 Elasticsearch。最关键的结构缺陷是 Redis `TextSegment` 没有 `documentId/chunkId/chunkIndex` 元数据，导致命中后要按正文反查 MySQL；重建文档时又只删除 MySQL chunk，没有删除旧 Redis 向量。

**4. RAG 问答：检索证据以后生成并返回答案**

```text
ChatComponent.vue
  -> api.ts.askQuestionStream() 手动解析 SSE
  -> AiController.askStream()
  -> AiService.askQuestionStream()
       -> Redis 检查回答缓存
       -> VectorSearchService 检索相关 Document
       -> ChatMemoryStore 读取最近 3 轮会话
       -> buildEnhancedPrompt() 拼接历史、证据、问题和规则
       -> ModelFactory 创建 StreamingChatLanguageModel
       -> 模型 onNext(token)
       -> SseEmitter 发送 message 事件
       -> 完成后缓存回答、保存会话
       -> ResponseEvaluationService 计算规则分数
       -> 发送 evaluation、metadata，关闭连接
  -> 页面持续更新答案
```

当前 RAG 的最大质量问题不是模型，而是检索结果已经退化成完整 `Document`，`buildEnhancedPrompt()` 又只截取每篇正文前 1500 字符：真正命中的 chunk 可能在文档后半段，模型最终反而看不到。二开时应让检索层直接返回包含分数和来源的 chunk 级结果。

#### 必须能够讲清的十个重点文件

| 重点文件 | 为什么存在 | 重要方法 / 决策 |
| --- | --- | --- |
| **[FileUploadController.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/FileUploadController.java)** | 文档上传的 HTTP 入口 | `uploadFileAsync()` 返回 202 和 `uploadId`；`getUploadProgress()` 供前端轮询；同步上传是旧入口 |
| **[DocumentProcessingService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentProcessingService.java)** | 可靠受理异步任务 | `uploadFileAsync()` 按“保存文件 → 创建 PENDING 记录 → 提交 Worker”的顺序执行，提交失败会标记 FAILED |
| **[DocumentProcessingWorker.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentProcessingWorker.java)** | 在独立线程执行耗时入库 | `processFileAsync()` 驱动 `PARSING → CHUNKING → EMBEDDING → COMPLETED/FAILED`，并负责清理稳定文件 |
| **[DocumentChunkService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentChunkService.java)** | 把 Document 加工成 MySQL chunk 和 Redis 向量 | `processDocumentWithProgress()` 是入库核心；`rebuildAllVectorIndex()` 全量重建；当前不能按文档删除 Redis 旧向量 |
| **[VectorSearchService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/VectorSearchService.java)** | 把用户查询变成相关证据 | `searchDocuments()` 向量召回；`hybridSearch()` 用 RRF 融合向量和 MySQL 关键词；`getRelevantSegments()` 在 Java 中逐块算余弦相似度 |
| **[AiService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java)** | RAG 问答总编排 | `askQuestion()` 同步问答；`askQuestionStream()` 串缓存、检索、记忆、模型、SSE、评分；`buildEnhancedPrompt()` 决定模型实际能看到什么 |
| **[ModelFactory.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java)** | 隔离不同模型客户端的创建差异 | `createModel()` / `createStreamingModel()` 统一支持 Qwen、DeepSeek、Kimi、Ollama；未知名称回退 Qwen |
| **[DocumentService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentService.java)** | 文档领域的 CRUD 和关联操作中心 | `save/update/delete` 有多套重载；`advancedSearch()`；`deleteDocumentWithRelatedData()`；部分版本/向量异常被吞掉，存在数据一致性风险 |
| **[SecurityConfig.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/SecurityConfig.java)** | 定义整个 API 的安全规则 | 无状态 Session、JWT Filter、BCrypt、公开路径、CORS、方法级权限开关 |
| **[api.ts](D:/Project/enterprise-agent/ai-assistant-front/src/services/api.ts)** | 前端所有后端调用的统一出口 | Axios JWT 拦截器、文档/检索/统计/认证 API；`askQuestionStream()` 用 Fetch 解析 SSE；支持文件类型的路径目前与后端不一致 |

面试时不需要背所有 Controller 方法。只要能围绕这十个文件讲出“输入、关键决策、状态变化、输出、失败边界”，项目就不再是一堆类名。

#### 后端全部文件职责

##### 启动与配置

| 文件 | 职责 |
| --- | --- |
| [AiKnowledgeBaseApplication.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/AiKnowledgeBaseApplication.java) | 程序入口；尝试读取 `.env` 到系统属性，再启动 Spring Boot |
| [AsyncConfig.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/AsyncConfig.java) | 开启异步；创建 `taskExecutor`：核心 5、最大 10、队列 100、拒绝策略 AbortPolicy，并传播 SecurityContext |
| [VirtualThreadConfig.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/VirtualThreadConfig.java) | 创建全局 `applicationTaskExecutor` 虚拟线程执行器并提供线程统计；文档 Worker 明确使用的仍是 `taskExecutor` |
| [SecurityConfig.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/SecurityConfig.java) | 配置 JWT Filter、公开/受保护路径、无状态认证、BCrypt、AuthenticationManager 和 CORS |
| [RedisConfig.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/RedisConfig.java) | 创建字符串 RedisTemplate 和对象 RedisTemplate，供缓存、会话和统计使用 |
| [ModelConfig.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/ModelConfig.java) | 把 `langchain4j.*` YAML 配置绑定为默认模型及四套 `ModelProperties` |
| [JpaConfig.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/JpaConfig.java) | 开启 JPA 审计，使创建/更新时间字段自动维护 |
| [CorsConfig.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/CorsConfig.java) | 额外的 MVC CORS 配置；与 SecurityConfig 的 CORS 规则重复且范围不一致 |

##### Controller：HTTP 接口入口

| 文件 | 负责的接口 |
| --- | --- |
| **[AiController.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/AiController.java)** | `/api/ai`：同步/流式问答、缓存清理、模型列表、会话信息、回答评分和用户反馈；自身创建缓存线程池执行 SSE，未使用项目统一执行器 |
| **[FileUploadController.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/FileUploadController.java)** | `/api/files`：同步上传、带分类标签上传、类型检查、异步上传、进度查询和支持类型 |
| **[DocumentController.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/DocumentController.java)** | `/api/documents`：CRUD、分页/高级搜索、分类标签关系、版本号、单篇/全量向量化和索引重建；文档权限注解最完整 |
| **[VectorSearchController.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/VectorSearchController.java)** | `/api/vector-search`：向量检索、分页、混合检索、单文档相关片段和统计 |
| [AuthController.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/AuthController.java) | `/api/auth`：登录、注册、刷新 token 和公开测试接口 |
| [AnalyticsController.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/AnalyticsController.java) | `/api/analytics`：仪表盘统计和管理员重置缓存统计 |
| [DocumentCategoryController.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/DocumentCategoryController.java) | `/api/categories`：分类 CRUD；只有全局“已登录”保护，没有细粒度方法权限 |
| [DocumentTagController.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/DocumentTagController.java) | `/api/tags`：标签 CRUD；权限边界与分类接口相同 |
| [DocumentVersionController.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/DocumentVersionController.java) | `/api/documents/{documentId}/versions`：创建、查询、回滚和比较版本；没有方法级权限注解 |

##### Service：业务逻辑与外部系统编排

| 文件 | 重要职责与方法 |
| --- | --- |
| **[AiService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java)** | RAG 总编排；`askQuestion`、`askQuestionStream`、`buildEnhancedPrompt`、缓存/会话管理 |
| **[VectorSearchService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/VectorSearchService.java)** | 向量召回、MySQL 关键词搜索、加权 RRF 混合、单文档片段相似度 |
| **[DocumentChunkService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentChunkService.java)** | 分块、384 维嵌入、MySQL/Redis 双写、批处理和索引重建 |
| **[DocumentProcessingService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentProcessingService.java)** | 创建异步上传任务，查询进度，处理任务提交失败 |
| **[DocumentProcessingWorker.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentProcessingWorker.java)** | 后台解析、保存、分块、更新阶段进度及失败信息 |
| **[DocumentFileStorage.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentFileStorage.java)** | 以 `uploadId` 隔离稳定文件；`store/resolve/delete` 并校验路径和扩展名 |
| **[DocumentService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentService.java)** | 文档 CRUD、分页、分类标签/日期搜索、版本协作、可选分块与关联数据删除 |
| [FileParseService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/FileParseService.java) | Markdown 直接按 UTF-8 读取，其余 PDF/Office/TXT/RTF/HTML/CSV 等交给 Tika；限制正文最多 200 万字符 |
| [ChatMemoryStore.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ChatMemoryStore.java) | Redis 保存会话消息与元数据，15 分钟 TTL，按轮数和估算 token 裁剪，提供最近 3 轮上下文 |
| [ModelFactory.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java) | 创建同步/流式模型客户端；当前日志会输出 API Key 前几位，应删除 |
| [AuthService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AuthService.java) | 登录认证、注册用户、刷新 token，并更新最后登录时间 |
| [CustomUserDetailsService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/CustomUserDetailsService.java) | 按用户名加载 `UserDetails`，交给 Spring Security 认证链 |
| [DocumentCategoryTagService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentCategoryTagService.java) | 整体替换文档的分类/标签关系，并读取对应 ID 或名称 |
| [DocumentCategoryService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentCategoryService.java) | 分类 CRUD、名称唯一性校验、Entity/DTO 转换 |
| [DocumentTagService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentTagService.java) | 标签 CRUD、名称唯一性校验、Entity/DTO 转换 |
| [DocumentVersionService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentVersionService.java) | 当前文档快照、版本列表/查询、回滚、简易比较及删除 |
| [ResponseEvaluationService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ResponseEvaluationService.java) | 按答案长度、关键词和召回文档数计算相关性/完整性/幻觉规则分，并保存用户反馈；不能证明事实正确 |
| [AnalyticsService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AnalyticsService.java) | 聚合回答评分、模型响应时间、缓存命中计数和最近问答供仪表盘展示；`todayAvgScore` 实际使用了全量数据 |
| [MetricsService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/MetricsService.java) | 用 Micrometer 记录问答、上传、检索、缓存、模型和评分指标，供 Actuator/Prometheus 抓取 |

##### Repository：MySQL 查询边界

| 文件 | 主要查询 |
| --- | --- |
| **[DocumentRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/DocumentRepository.java)** | 标题/正文模糊搜索、分类/标签/日期筛选、组合高级搜索和全部文档 ID |
| **[DocumentChunkRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/DocumentChunkRepository.java)** | 按文档顺序读 chunk、删除、计数、原生插入，以及当前检索使用的 `findByContentContaining()` 反查 |
| [DocumentCategoryRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/DocumentCategoryRepository.java) | 按名称查分类、按名称排序列出 |
| [DocumentTagRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/DocumentTagRepository.java) | 按名称查标签、按名称排序列出 |
| [DocumentCategoryRelationRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/DocumentCategoryRelationRepository.java) | 查/删文档分类关系，查询分类 ID |
| [DocumentTagRelationRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/DocumentTagRelationRepository.java) | 查/删文档标签关系，查询标签 ID |
| [DocumentVersionRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/DocumentVersionRepository.java) | 版本列表、最新版本、指定版本、最大版本号和删除 |
| [UserRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/UserRepository.java) | 按用户名/邮箱查用户及唯一性判断 |
| [RoleRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/RoleRepository.java) | 按角色名查询角色 |
| [PermissionRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/PermissionRepository.java) | 按权限名查询权限 |
| [UploadProgressRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/UploadProgressRepository.java) | 按 `uploadId` 查异步任务、按状态清理 |
| [AnswerEvaluationRepository.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/repository/AnswerEvaluationRepository.java) | 按会话/等级/当天查询评分并查询高分回答 |

Repository 接口通常没有实现类，因为 Spring Data JPA 会根据方法名或 `@Query` 在运行时生成实现。

##### Entity：MySQL 数据模型与 Redis 会话对象

| 文件 | 表示的数据 |
| --- | --- |
| **[Document.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/Document.java)** | 文档标题、全文、类型、当前版本、时间及与 chunk/版本/分类/标签的关系 |
| **[DocumentChunk.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/DocumentChunk.java)** | 文档分块的 `documentId + chunkIndex + content`；`embeddingVector` BLOB 字段当前未被向量流程使用 |
| [DocumentVersion.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/DocumentVersion.java) | 某篇文档一个版本的标题、正文、版本号、创建人和变更摘要 |
| [DocumentCategory.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/DocumentCategory.java) | 分类名称、描述、颜色及反向关系 |
| [DocumentTag.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/DocumentTag.java) | 标签名称、描述、颜色及反向关系 |
| [DocumentCategoryRelation.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/DocumentCategoryRelation.java) | Document 与 Category 的中间关系实体 |
| [DocumentTagRelation.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/DocumentTagRelation.java) | Document 与 Tag 的中间关系实体 |
| **[User.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/User.java)** | 用户账号并实现 `UserDetails`；`getAuthorities()` 把角色和权限转换为 Spring Security authority |
| [Role.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/Role.java) | 角色及其 Permission 集合 |
| [Permission.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/Permission.java) | `document:read/write/delete` 等原子权限 |
| [UploadProgress.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/UploadProgress.java) | 异步上传任务状态、百分比、文件信息和错误；核心状态是 PENDING 到 COMPLETED/FAILED |
| [AnswerEvaluation.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/AnswerEvaluation.java) | 问题、回答、模型、耗时、规则评分和用户反馈 |
| [SessionMessage.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/SessionMessage.java) | Redis 中的一条 user/assistant 消息，不是 JPA Entity；`estimateTokens()` 做粗略 token 估算 |

核心关系只需记住：

```text
Document 1 -> N DocumentChunk
Document 1 -> N DocumentVersion
Document N -> N Category / Tag（通过关系实体）
User N -> N Role N -> N Permission
UploadProgress 独立记录一次异步任务
AnswerEvaluation 独立记录一次回答评分
SessionMessage 只存在 Redis 会话中
```

##### DTO：接口数据形状

| 文件 | 用途 |
| --- | --- |
| [LoginRequest.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/LoginRequest.java) | 登录用户名和密码及校验规则 |
| [RegisterRequest.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/RegisterRequest.java) | 注册用户名、密码、邮箱、手机号和昵称 |
| [JwtResponse.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/JwtResponse.java) | access/refresh token、过期时间和用户信息 |
| [DocumentCreateDTO.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/DocumentCreateDTO.java) | 同时创建文档并指定分类/标签 ID |
| [DocumentCategoryTagDTO.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/DocumentCategoryTagDTO.java) | 整体设置一篇文档的分类/标签关系 |
| [DocumentCategoryDTO.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/DocumentCategoryDTO.java) | 分类接口的输入输出 |
| [DocumentTagDTO.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/DocumentTagDTO.java) | 标签接口的输入输出 |
| [UploadProgressDTO.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/UploadProgressDTO.java) | 上传任务进度响应；当前 Service 实际主要返回 Map |
| [AnswerEvaluationDTO.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/AnswerEvaluationDTO.java) | 回答评估的分数、等级和反馈数据 |

##### 安全与启动数据

| 文件 | 职责 |
| --- | --- |
| **[JwtAuthenticationFilter.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/security/JwtAuthenticationFilter.java)** | 从 Authorization 取 Bearer token，校验后加载用户并设置 SecurityContext |
| [JwtTokenProvider.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/security/JwtTokenProvider.java) | 生成/解析/验证 access 和 refresh token |
| [RolePermissionInitializer.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/init/RolePermissionInitializer.java) | 启动时创建权限、ADMIN/USER/GUEST 角色和默认 `admin/admin123`；默认凭证是必须整改的安全问题 |
| [DataInitializer.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/init/DataInitializer.java) | 数据库为空时创建 3 篇演示文档；其中一条示例内容声称使用 Elasticsearch，但当前依赖和代码并没有 ES |

#### 前端全部文件职责

##### 启动、路由、状态和请求层

| 文件 | 职责 |
| --- | --- |
| **[main.ts](D:/Project/enterprise-agent/ai-assistant-front/src/main.ts)** | 创建 Vue 应用，安装 Pinia、Router 和 Naive UI |
| [App.vue](D:/Project/enterprise-agent/ai-assistant-front/src/App.vue) | 根组件，仅提供消息上下文和 `RouterView` |
| **[router/index.ts](D:/Project/enterprise-agent/ai-assistant-front/src/router/index.ts)** | 定义登录、注册、首页、关于、检索和仪表盘路由；导航守卫阻止未登录访问业务页 |
| **[stores/auth.ts](D:/Project/enterprise-agent/ai-assistant-front/src/stores/auth.ts)** | 登录、注册、刷新、退出；把 token 和用户信息保存在 Pinia 与 localStorage |
| **[services/api.ts](D:/Project/enterprise-agent/ai-assistant-front/src/services/api.ts)** | Axios 基础地址、JWT 请求拦截、错误处理、所有后端 API 和 SSE 解析 |

##### 页面和业务组件

| 文件 | 职责 |
| --- | --- |
| **[views/HomeView.vue](D:/Project/enterprise-agent/ai-assistant-front/src/views/HomeView.vue)** | 登录后的主壳，组合问答、文档、向量检索、仪表盘标签页和退出入口 |
| [views/LoginView.vue](D:/Project/enterprise-agent/ai-assistant-front/src/views/LoginView.vue) | 登录表单、校验、调用 auth store 并跳转首页 |
| [views/RegisterView.vue](D:/Project/enterprise-agent/ai-assistant-front/src/views/RegisterView.vue) | 注册表单、确认密码校验和注册请求 |
| [views/DashboardView.vue](D:/Project/enterprise-agent/ai-assistant-front/src/views/DashboardView.vue) | `DashboardComponent` 的路由包装页 |
| [views/VectorSearchView.vue](D:/Project/enterprise-agent/ai-assistant-front/src/views/VectorSearchView.vue) | 一套独立的向量/混合检索页面；与 `VectorSearchComponent` 存在功能重复 |
| [views/AboutView.vue](D:/Project/enterprise-agent/ai-assistant-front/src/views/AboutView.vue) | Vue 模板遗留的占位关于页，不属于核心业务 |
| **[components/ChatComponent.vue](D:/Project/enterprise-agent/ai-assistant-front/src/components/ChatComponent.vue)** | 模型选择、问题发送、流式消息更新、评分展示和清缓存 |
| **[components/DocumentComponent.vue](D:/Project/enterprise-agent/ai-assistant-front/src/components/DocumentComponent.vue)** | 文档列表、筛选、分页、增删改、上传、分类标签、版本和索引重建入口 |
| **[components/FileUploadComponent.vue](D:/Project/enterprise-agent/ai-assistant-front/src/components/FileUploadComponent.vue)** | 文件预检查、异步上传、按 `uploadId` 轮询状态、完成后设置分类标签 |
| [components/VectorSearchComponent.vue](D:/Project/enterprise-agent/ai-assistant-front/src/components/VectorSearchComponent.vue) | 向量/混合检索表单、结果分页、文档详情、相关片段和统计 |
| [components/DocumentVersionComponent.vue](D:/Project/enterprise-agent/ai-assistant-front/src/components/DocumentVersionComponent.vue) | 版本列表、创建、查看、回滚和比较 |
| [components/DashboardComponent.vue](D:/Project/enterprise-agent/ai-assistant-front/src/components/DashboardComponent.vue) | 读取统计数据并用 ECharts 绘制评分、耗时、模型和指标图表 |

以下是 Vue 脚手架遗留，不需要投入阅读时间：`HelloWorld.vue`、`TheWelcome.vue`、`WelcomeItem.vue`、五个 `components/icons/*.vue`、`stores/counter.ts`、`assets/logo.svg`。`assets/base.css` 和 `assets/main.css` 只负责全局样式。

#### 配置、数据库、部署、测试与文档

##### 根目录与运行配置

| 文件 | 职责 |
| --- | --- |
| **[pom.xml](D:/Project/enterprise-agent/pom.xml)** | Java 21 / Spring Boot 3.2.10 的 Maven 依赖和构建；包含 JPA、Security、Redis、LangChain4j、Tika、JWT、Actuator、测试容器，目前没有 Elasticsearch 依赖 |
| **[application.yml](D:/Project/enterprise-agent/src/main/resources/application.yml)** | 端口、MySQL、Redis、上传目录、四种模型、会话、JWT、Actuator 配置；密钥从环境变量读取 |
| [.env.template](D:/Project/enterprise-agent/.env.template) | 本地环境变量样例，不应放真实密钥 |
| [readme.md](D:/Project/enterprise-agent/readme.md) | 项目运行说明、能力边界和未来 Agent 方向；已明确当前还不是真 Agent |
| [mvnw](D:/Project/enterprise-agent/mvnw) / [mvnw.cmd](D:/Project/enterprise-agent/mvnw.cmd) / [.mvn](D:/Project/enterprise-agent/.mvn) | Maven Wrapper，使项目不依赖机器预装的 Maven 版本 |
| [.gitignore](D:/Project/enterprise-agent/.gitignore) | 忽略构建产物、密钥和本地文件 |
| [LICENSE](D:/Project/enterprise-agent/LICENSE) / [UPSTREAM_NOTICE.md](D:/Project/enterprise-agent/UPSTREAM_NOTICE.md) | 开源许可及上游来源说明 |

##### 前端工程配置

| 文件 | 职责 |
| --- | --- |
| [package.json](D:/Project/enterprise-agent/ai-assistant-front/package.json) / [package-lock.json](D:/Project/enterprise-agent/ai-assistant-front/package-lock.json) | Vue、Pinia、Naive UI、Axios、ECharts、Vite 等依赖和锁定版本 |
| [vite.config.ts](D:/Project/enterprise-agent/ai-assistant-front/vite.config.ts) | Vite 开发服务器和 `/api -> localhost:8080` 代理 |
| [tsconfig.json](D:/Project/enterprise-agent/ai-assistant-front/tsconfig.json) / `tsconfig.app.json` / `tsconfig.node.json` / `env.d.ts` | TypeScript 编译范围和 Vite 类型声明 |
| [eslint.config.ts](D:/Project/enterprise-agent/ai-assistant-front/eslint.config.ts) / `.prettierrc.json` / `.editorconfig` | 代码检查与格式规范 |
| [tailwind.config.js](D:/Project/enterprise-agent/ai-assistant-front/tailwind.config.js) | Tailwind 扫描范围和主题配置 |
| [index.html](D:/Project/enterprise-agent/ai-assistant-front/index.html) / `public/favicon.ico` | 前端 HTML 入口和图标 |
| `.npmrc` / `.gitattributes` / `.gitignore` | 包管理、Git 文本规则和前端忽略规则 |

##### 数据库与部署

| 文件 | 职责 |
| --- | --- |
| **[mysql/init.sql](D:/Project/enterprise-agent/mysql/init.sql)** | 创建文档、chunk、版本、分类、标签和关系表 |
| [mysql/02-add-evaluation-tables.sql](D:/Project/enterprise-agent/mysql/02-add-evaluation-tables.sql) | 增加上传进度和回答评分表 |
| [mysql/add-file-type-column.sql](D:/Project/enterprise-agent/mysql/add-file-type-column.sql) | 给文档表补 `file_type` 字段的兼容脚本 |
| **[docker/docker-compose.yml](D:/Project/enterprise-agent/docker/docker-compose.yml)** | 编排 MySQL、Redis Stack；完整 profile 还声明应用、Prometheus、Grafana |
| [docker/Dockerfile](D:/Project/enterprise-agent/docker/Dockerfile) | Java 21 多阶段构建、非 root 运行和健康检查 |
| [docker/prometheus/prometheus.yml](D:/Project/enterprise-agent/docker/prometheus/prometheus.yml) | 抓取 Spring Actuator Prometheus 指标 |

当前 `docker-compose.yml` 引用了仓库中不存在的 Grafana 配置目录和 Prometheus 告警规则，因此 MySQL/Redis 基础环境可用，不应直接宣称完整监控栈已经开箱即用。数据库又同时使用 SQL 脚本和 `ddl-auto: update`，生产化前应统一迁移方案。

##### 测试

| 文件 | 验证内容 |
| --- | --- |
| [DocumentFileStorageTest.java](D:/Project/enterprise-agent/src/test/java/com/kb/demo/service/DocumentFileStorageTest.java) | 稳定文件保存、解析和非法 `uploadId` 防护 |
| [DocumentProcessingServiceTest.java](D:/Project/enterprise-agent/src/test/java/com/kb/demo/service/DocumentProcessingServiceTest.java) | 受理顺序、任务保存、Worker 提交和提交失败补偿 |
| [DocumentProcessingWorkerTest.java](D:/Project/enterprise-agent/src/test/java/com/kb/demo/service/DocumentProcessingWorkerTest.java) | Worker 成功/失败时的状态更新和文件清理 |
| [DocumentProcessingWorkerAsyncTest.java](D:/Project/enterprise-agent/src/test/java/com/kb/demo/service/DocumentProcessingWorkerAsyncTest.java) | `@Async` 确实在独立线程执行 |
| [VectorSearchServiceTest.java](D:/Project/enterprise-agent/src/test/java/com/kb/demo/service/VectorSearchServiceTest.java) | RRF 融合、去重、权重和排名边界 |
| [ResponseEvaluationServiceTest.java](D:/Project/enterprise-agent/src/test/java/com/kb/demo/service/ResponseEvaluationServiceTest.java) | 规则评分、等级、反馈和统计 |
| [IntegrationIT.java](D:/Project/enterprise-agent/src/test/java/com/kb/demo/IntegrationIT.java) | 使用 Testcontainers 验证 MySQL/Redis、文档 CRUD、搜索、缓存和 Actuator；当前没有 Failsafe 报告证明它已在本机运行 |

已有 Surefire 报告显示上述 6 个单元测试类共 23 个测试全部通过；这是仓库中的已有证据，不等于本次整理重新执行了测试。

##### `docs` 目录

| 文件 | 用途 |
| --- | --- |
| [docs/00-start-here.md](D:/Project/enterprise-agent/docs/00-start-here.md) | 学习与二开入口 |
| [docs/01-upstream-audit.md](D:/Project/enterprise-agent/docs/01-upstream-audit.md) | 上游项目审计和现状判断 |
| [docs/02-architecture-and-call-chain.md](D:/Project/enterprise-agent/docs/02-architecture-and-call-chain.md) | 旧版架构/调用链说明；其中“MultipartFile 直接交给同 Service 的 @Async”已经落后于当前 Worker 实现，不应继续当事实 |
| [docs/03-secondary-development-roadmap.md](D:/Project/enterprise-agent/docs/03-secondary-development-roadmap.md) | 二次开发路线图 |
| [docs/04-interview-ownership.md](D:/Project/enterprise-agent/docs/04-interview-ownership.md) | 面试中如何说明自己的改造和边界 |
| [docs/05-baseline-verification.md](D:/Project/enterprise-agent/docs/05-baseline-verification.md) | 基线运行和验证记录 |
| [docs/06-stage-1-reliable-ingestion.md](D:/Project/enterprise-agent/docs/06-stage-1-reliable-ingestion.md) | 第一阶段可靠异步入库的设计与实现说明 |
| [docs/adr/0001-use-java-for-ai-backend.md](D:/Project/enterprise-agent/docs/adr/0001-use-java-for-ai-backend.md) | 为什么 AI 后端继续使用 Java 的架构决策记录 |
| [docs/README.md](D:/Project/enterprise-agent/docs/README.md) | 文档目录索引 |
| [docs/SESSION_MEMORY_AND_CLAUDE_HANDOFF.md](D:/Project/enterprise-agent/docs/SESSION_MEMORY_AND_CLAUDE_HANDOFF.md) | 跨会话交接信息，不属于运行时代码 |

#### 现在怎样理解项目，而不是继续背文件

只需要把每个文件挂在下面四个问题之一：

```text
谁发起请求？      -> Vue 组件 / api.ts / Controller
谁决定业务步骤？  -> Service
状态保存在哪里？  -> MySQL Entity/Repository、Redis、稳定文件
结果怎样返回？    -> JSON、202 + 轮询、SSE
```

如果面试官问“这个项目最核心的链路是什么”，按下面顺序回答：

```text
先讲可靠异步入库，证明系统能把企业文件稳定加工成 chunk
  -> 再讲 Redis 语义召回与 MySQL 身份数据如何关联
  -> 再讲检索结果、会话历史怎样进入 prompt
  -> 最后讲 SSE、多模型、缓存、权限和监控怎样补全工程闭环
```

#### 当前核心竞争力和二开后的胜负手

当前项目的竞争力不是“实现了 RAG”，而是已经有一个可继续改造的企业应用骨架：异步任务状态、RBAC、文档生命周期、多模型、SSE、缓存和指标都已存在。它的短板也非常明确：检索身份不稳定、没有真正 Elasticsearch、返回完整 Document 而非 chunk、没有引用、重建存在脏向量、评分不可信、尚无 Agent 工具循环。

十天二开应把竞争力集中在一条可验证主线上：

```text
统一 RetrievalHit(documentId, chunkId, chunkIndex, content, score, source)
  -> Elasticsearch 保存 chunk 文本和元数据，提供 BM25 + 条件过滤
  -> Redis 提供向量召回
  -> RRF 融合并保留两路分数与命中来源
  -> prompt 直接使用命中的 chunk
  -> 回答返回引用文档、分块和分数
  -> 指标记录检索耗时、召回来源和缓存命中
```

这比“又做了一个 RAG Agent”更有面试辨识度，因为你能用代码、接口响应、测试和指标说明：原项目哪里错、为什么用 Elasticsearch、如何解决一致性和可解释性、效果怎样验证。Agent 工具调用放在检索闭环稳定之后，只做有限工具集合，避免十天内做成无法解释的演示壳。

**结论**

阅读阶段到这里已经足够。后面不再按目录从头读项目，而是围绕 Elasticsearch 混合检索这条改造主线，遇到问题再回看 `DocumentChunkService -> VectorSearchService -> AiService -> api.ts` 四个核心入口。

---

### 001. MultipartFile：Spring 交给 Controller 的上传文件

**需求**

浏览器上传 PDF、Word 等文件后，Controller 需要读取文件名、大小和内容。

**代码位置**

[FileUploadController.java 第 188 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/FileUploadController.java:188)

```java
public ResponseEntity<?> uploadFileAsync(
        @RequestParam("file") MultipartFile file) {
}
```

`MultipartFile` 是 Spring 解析 HTTP 上传请求后创建的对象，表示请求中名为 `file` 的文件部分。

可以把它记成：

```text
MultipartFile
  = 上传文件的信息
  + 读取或保存文件内容的方法
```

```java
file.getOriginalFilename(); // 原始文件名
file.getSize();             // 文件大小
file.getContentType();      // 客户端声明的类型
file.isEmpty();             // 是否为空
file.getInputStream();      // 读取内容
file.transferTo(...);       // 保存到指定位置
```

调用链：

```text
浏览器发送 multipart/form-data
  -> Spring 解析 file 部分
  -> 创建 MultipartFile
  -> 传给 Controller
```

**结论**

`MultipartFile` 是本次 HTTP 请求中的上传文件，不是数据库实体，也不是已经永久保存的磁盘文件。

**易错点**

请求结束后临时上传内容可能失效，因此交给后台线程前必须先保存到稳定位置。

---

### 002. uploadFileAsync：Controller 的四个职责

**需求**

上传接口需要接收文件、做入口校验、调用业务层，并告诉前端任务已经受理。

**代码位置**

[FileUploadController.java 第 186—207 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/FileUploadController.java:186)

核心代码：

```java
if (file.isEmpty()) {
    return ResponseEntity.badRequest().body("文件不能为空");
}

String uploadId = documentProcessingService.uploadFileAsync(file);

result.put("uploadId", uploadId);
result.put("statusUrl", "/api/files/upload-progress/" + uploadId);
return ResponseEntity.accepted().body(result);
```

四个职责：

```text
接收：@RequestParam 将上传内容绑定为 MultipartFile
校验：检查空文件和支持的文件类型
调用：把文件交给 DocumentProcessingService
返回：HTTP 202 + uploadId + 进度查询地址
```

完整接口路径由类路径与方法路径拼接：

```text
/api/files + /upload-async
  = POST /api/files/upload-async
```

**结论**

Controller 管 HTTP 边界；文件保存、任务创建、解析和向量化属于 Service。

**易错点**

`202 Accepted` 只表示后台任务已经受理，不表示文档已经处理成功。

---

### 003. uploadId：一次后台任务的编号

**需求**

HTTP 请求已经返回后，前端、后台 Worker 和数据库仍然需要确认它们正在处理同一次上传。

**代码位置**

[DocumentProcessingService.java 第 43—45 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentProcessingService.java:43)

```java
String uploadId = UUID.randomUUID().toString();
```

`UUID.randomUUID()` 生成一个很难重复的标识符，`toString()` 将其转换成适合放入数据库、JSON、URL 和日志的字符串。

项目中的两个 ID：

| ID | 用途 | 生成位置 |
| --- | --- | --- |
| `Long id` | 数据库内部主键 | MySQL |
| `String uploadId` | 对外查询上传任务 | Java 在任务创建前生成 |

调用链：

```text
生成 uploadId
  -> 写入 UploadProgress
  -> 传给 DocumentProcessingWorker
  -> 返回给前端
  -> 前端用它查询同一条任务记录
```

数据库还为 `uploadId` 设置了唯一约束：

[UploadProgress.java 第 19—20 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/UploadProgress.java:19)

```java
@Column(nullable = false, unique = true, length = 36)
private String uploadId;
```

**结论**

`uploadId` 是贯穿 HTTP 响应、后台处理、数据库进度和日志的任务编号。

**易错点**

`uploadId` 只负责标识任务，不是登录凭证，也不会自动防止用户重复提交同一个文件。

---

### 004. DocumentProcessingService：先创建任务，再提交后台处理

**需求**

后台线程开始处理前，原文件必须能够继续读取，数据库中也必须已经存在可查询的任务记录。

**代码位置**

[DocumentProcessingService.java 第 43—68 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentProcessingService.java:43)

```java
String uploadId = UUID.randomUUID().toString();
Path storedFile = documentFileStorage.store(uploadId, file);

UploadProgress progress = new UploadProgress();
progress.setUploadId(uploadId);
progress.setStatus(UploadProgress.UploadStatus.PENDING);
uploadProgressRepository.save(progress);

metricsService.recordDocumentUpload();
documentProcessingWorker.processFileAsync(uploadId);
return uploadId;
```

执行顺序：

```text
1. 生成 uploadId
2. 把临时 MultipartFile 保存到稳定目录
3. 保存 PENDING、0% 的 UploadProgress
4. 记录一次上传受理指标
5. 把 uploadId 提交给后台 Worker
6. 返回 uploadId
```

为什么必须先保存进度：

```text
后台线程可能立即开始
前端也可能立即查询
  -> 两边都必须先能根据 uploadId 找到任务
```

Worker 只接收 `uploadId`，再用它查任务和稳定文件；它不持有请求期间的 `MultipartFile`。

任务状态会继续变化：

```text
PENDING
  -> PARSING
  -> CHUNKING
  -> EMBEDDING
  -> COMPLETED 或 FAILED
```

**结论**

`DocumentProcessingService` 负责可靠受理任务；`DocumentProcessingWorker` 负责后台执行任务。

**易错点**

必须先创建任务记录再提交 Worker，否则后台更新进度时可能查不到对应任务。

---

### 005. 线程池：让请求线程不必等待文档处理完成

**需求**

解析、分块和向量化耗时较长，HTTP 请求不应该一直等待整个流程完成。

异步的第一性原理：

```text
普通方法调用
  -> 当前线程进入方法
  -> 必须等方法返回

把任务提交给线程池
  -> 请求线程完成提交后继续返回响应
  -> 工作线程执行耗时任务
```

线程池配置位置：

[AsyncConfig.java 第 25—54 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/AsyncConfig.java:25)

```java
executor.setCorePoolSize(5);
executor.setMaxPoolSize(10);
executor.setQueueCapacity(100);
executor.setThreadNamePrefix("file-processing-");
executor.setRejectedExecutionHandler(
        new ThreadPoolExecutor.AbortPolicy());
```

任务进入线程池的顺序：

```text
工作线程少于 5 个
  -> 创建线程执行

5 个核心线程都忙
  -> 最多 100 个任务进入队列

队列也满
  -> 继续创建线程，最多到 10 个

10 个线程和队列都满
  -> AbortPolicy 拒绝新任务
```

项目中的两条执行路线：

```text
HTTP 请求线程
  -> 保存文件和任务
  -> 提交 uploadId
  -> 返回 HTTP 202

file-processing-* 工作线程
  -> 解析
  -> 保存文档
  -> 分块和向量化
  -> 更新最终状态
```

**结论**

异步不会让单个文档处理得更快；它让请求线程先返回，并限制后台任务怎样并发、排队和拒绝。

**易错点**

任务提交成功不等于处理成功，最终结果仍要查看 `UploadProgress`。

---

### 006. @Async：Spring 怎样把 Worker 方法提交给线程池

**需求**

业务代码希望调用一个普通 Java 方法，同时由 Spring 自动把这次调用提交给指定线程池。

**代码位置**

[DocumentProcessingWorker.java 第 49—50 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentProcessingWorker.java:49)

```java
@Async("taskExecutor")
public void processFileAsync(String uploadId) {
}
```

`@Async` 是一个标记：外部调用这个方法时，应使用名为 `taskExecutor` 的线程池。

Spring 在概念上增加了一个外层对象：

```java
class WorkerProxy {
    void processFileAsync(String uploadId) {
        taskExecutor.execute(
                () -> realWorker.processFileAsync(uploadId)
        );
    }
}
```

这段代理代码不需要项目手写，但它表达了真正发生的动作：

```text
DocumentProcessingService
  -> 调用 Spring 注入的 Worker 代理
  -> 代理把任务提交给 taskExecutor
  -> 请求线程返回
  -> file-processing-* 线程调用真实 Worker
```

当前项目把代码拆成两个 Bean：

```text
DocumentProcessingService：受理任务
DocumentProcessingWorker：后台处理
```

这样 Service 调用 Worker 时，调用能够从外部进入 Worker 代理。

**结论**

`@Async` 本身不执行文档处理；代理负责提交任务，线程池负责安排工作线程，真实 Worker 负责业务代码。

**易错点**

同一个对象内部使用 `this.processFileAsync()` 会直接调用自己，不会重新进入外层代理，因此可能仍在当前线程同步执行。

---

### 007. 懒加载：为什么先保存 documentId 再查 Document

**需求**

向量检索先找到匹配的文档切片 `DocumentChunk`，最终需要拿到切片所属的完整 `Document`。

**第一步：先看 `DocumentChunk` 保存了什么**

[DocumentChunk.java 第 22—24、72—74 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/entity/DocumentChunk.java:22)

```java
@ManyToOne(fetch = FetchType.LAZY)
private Document document;

public Document getDocument() {
    return document;
}
```

一篇 `Document` 可以被切成多个 `DocumentChunk`。因此每个切片都要记录：自己属于哪篇完整文档。

```text
员工手册 Document（id = 12）
  -> DocumentChunk 1：第一段
  -> DocumentChunk 2：第二段
  -> DocumentChunk 3：第三段
```

字段 `document` 保存的就是这层所属关系。`chunk.getDocument()` 只是它的普通 getter：返回这个切片所属的 `Document`。

**第二步：为什么不在查询切片时立刻查询完整文档**

一次向量检索可能找到很多切片。如果查询每个切片时，都顺便读取文档标题、正文等全部字段，就可能产生很多暂时用不到的数据库查询。

所以这里使用：

```java
fetch = FetchType.LAZY
```

`LAZY` 的意思是“需要时再加载”：

```text
查询 DocumentChunk
  -> 先取得切片自身的数据
  -> 知道它关联的 Document id
  -> 暂时不读取 Document 的标题、正文等字段
```

此时 `document` 不是 `null`，标题等字段也不是“空字符串”；它们只是**尚未从数据库加载**。

**第三步：数据库访问窗口关闭后，Java 对象并不会消失**

Repository 查询数据库时，当前代码会暂时拥有继续读取数据库的条件。可以把这段时间理解成“数据库访问窗口”。查询返回后，这个窗口会关闭。

但查询得到的 `chunk` 仍然是内存中的 Java 对象：

```text
Repository 查询结束
  -> 数据库访问窗口关闭
  -> chunk 对象仍然存在
  -> 已经加载的字段仍然能读
  -> 尚未加载的字段不能再自动去数据库补查
```

例如：

```java
Document document = chunk.getDocument();
Long id = document.getId();       // ID 已知，可以读取
String title = document.getTitle(); // 标题若未加载，就需要再次查数据库
```

如果读取 `title` 时数据库访问窗口已经关闭，这次补查无法完成，就会出现 `LazyInitializationException`。

**第四步：当前代码怎样避开这个问题**

[VectorSearchService.java 第 123—149 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/VectorSearchService.java:123)

```java
DocumentChunk chunk = chunks.get(0);
Long docId = chunk.getDocument().getId();
Document doc = documentRepository.findById(docId).orElse(null);
```

这里先后拿到了两种状态的 `Document`：

```text
chunk.getDocument()
  -> 返回切片关联的 Document
  -> 此时可能只确定了 ID，其他字段尚未加载

documentRepository.findById(docId)
  -> 明确根据 ID 查询数据库
  -> 返回已经读取了普通字段的 Document
```

完整调用链：

```text
findByContentContaining(...) 查询匹配的 DocumentChunk
  -> chunk.getDocument() 找到切片所属的文档
  -> 只读取已经知道的 documentId
  -> 把 documentId 保存进 matchedDocumentIds
  -> documentRepository.findById(documentId)
  -> 明确查询完整 Document
  -> 保存进 matchedDocuments
```

**第五步：串起整个向量查询过程**

[VectorSearchService.java 第 84—149 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/VectorSearchService.java:84)

每次调用 `searchDocuments()`，当前代码都会执行下面这条主线：

```text
接收查询文本 query
  -> 创建 EmbeddingModel
  -> 创建连接 Redis 的 EmbeddingStore
  -> embeddingModel.embed(query)，把查询文本转成向量
  -> 用查询向量、结果数量和最低分数构造 EmbeddingSearchRequest
  -> embeddingStore.search(request)，执行 Redis 向量检索
  -> 得到相似的 TextSegment 文本片段
  -> 根据片段内容查询对应的 DocumentChunk
  -> 从 DocumentChunk 取得 documentId
  -> documentRepository.findById(documentId)
  -> 得到完整 Document
  -> 返回匹配文档列表
```

Redis 向量检索不会直接返回最终的 `Document`。它先返回相似的 `TextSegment`，后面的 Repository 查询负责把文本片段重新对应到数据库中的文档。

**结论**

`DocumentChunk` 中的 `document` 负责表示“切片属于哪篇文档”。由于它采用懒加载，第一次查询切片时可能只确定文档 ID。当前代码先保存稳定的 ID，再通过 Repository 明确查询完整 `Document`，避免数据库访问窗口关闭后才读取尚未加载的字段。

**易错点**

“尚未加载”不等于“字段为空”，Java 对象也没有消失。异常的真正原因是：读取尚未加载的字段需要再次查数据库，但数据库访问窗口已经关闭。

---

### 008. @PreAuthorize：进入接口前检查用户权限

**需求**

向量检索会读取企业文档，不能让没有文档读取权限的用户执行这个接口。

**代码位置**

[VectorSearchController.java 第 36—44 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/VectorSearchController.java:36)

```java
@GetMapping("/documents")
@PreAuthorize("hasAuthority('document:read')")
public ResponseEntity<List<Document>> searchDocuments(...) {
    return ResponseEntity.ok(
            vectorSearchService.searchDocuments(...));
}
```

`@PreAuthorize` 表示：在进入下面的方法以前，先检查括号中的条件。

```text
hasAuthority('document:read')
  -> 检查当前用户是否拥有 document:read 权限
```

可以先把它理解成：

```java
if (当前用户拥有("document:read")) {
    searchDocuments(...);
} else {
    拒绝请求;
}
```

执行顺序：

```text
请求到达 /api/vector-search/documents
  -> 检查当前用户是否拥有 document:read
  -> 有权限：进入 searchDocuments()
  -> 没有权限：直接拒绝，不执行方法内部代码
```

`document:read` 只是项目约定的权限名称，含义是“允许读取文档”。它不是 `Document` 类的字段，也不会执行文档查询。

**结论**

`@PreAuthorize("hasAuthority('document:read')")` 是这个接口入口处的权限门卫：先检查权限，通过后才允许 Controller 调用 `VectorSearchService`。

**易错点**

注解检查发生在方法执行之前；没有权限时，`searchDocuments()` 内部的第一行代码都不会运行。

---

### 009. askQuestion：从用户问题到知识库回答

**需求**

系统需要接收用户问题，从企业知识库找证据，把证据和问题一起交给大模型，并返回有依据的答案。

#### 第一步：Controller 接收 JSON

[AiController.java 第 42—58 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/AiController.java:42)

```java
@PostMapping("/ask")
public Map<String, Object> ask(
        @RequestBody Map<String, String> request) {

    String question = request.get("question");
    String sessionId = request.get("sessionId");
    String model = request.get("model");
}
```

请求体可以是：

```json
{
  "question": "公司的请假制度是什么？",
  "sessionId": "session-001",
  "model": "qwen"
}
```

`@RequestBody` 让 Spring 读取 HTTP 请求体中的 JSON，并转换成 `request`。三次 `request.get(...)` 再分别取出问题、会话编号和模型名称。

`model` 是可选参数：

```text
传了 model
  -> askQuestion(question, sessionId, model)

没传或传入空字符串
  -> askQuestion(question, sessionId)
  -> 后面补上默认模型
```

#### 第二步：两种入口汇合到同一套问答逻辑

[AiService.java 第 60—64 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:60)

```java
public Map<String, Object> askQuestion(
        String question, String sessionId) {
    return askQuestion(
            question,
            sessionId,
            modelConfig.getDefaultModel());
}
```

两个参数的方法只补上默认模型。无论是否指定模型，最终都会进入三个参数的 `askQuestion()`，项目不需要维护两套 RAG 逻辑。

#### 第三步：先检查 Redis 回答缓存

[AiService.java 第 65—72 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:65)

```java
String cacheKey = "ai:answer:"
        + sessionId + ":"
        + question.hashCode() + ":"
        + modelName;

String cachedAnswer =
        redisTemplate.opsForValue().get(cacheKey);

if (cachedAnswer != null) {
    return Map.of(
            "answer", cachedAnswer,
            "fromCache", true,
            "model", modelName);
}
```

缓存键的三部分分别区分会话、问题和模型。查到缓存就直接返回，不再检索知识库和调用模型。

`question.hashCode()` 只是把长问题转换成较短数字，不是加密，也不能保证绝对不重复。

#### 第四步：检索文档并转换成上下文

[AiService.java 第 74—80 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:74)

```java
List<Document> relevantDocuments =
        vectorSearchService.searchDocuments(question);

String context = relevantDocuments.stream()
        .map(doc ->
                "标题: " + doc.getTitle()
                + "\n内容: " + doc.getContent())
        .collect(Collectors.joining("\n\n"));
```

转换过程：

```text
question
  -> VectorSearchService 检索相关 Document
  -> map：逐个把 Document 转成“标题 + 内容”字符串
  -> joining：用空行连接多个字符串
  -> 得到一个 context
```

`doc -> ...` 表示“拿到当前 `Document`，按箭头右侧的规则转换”。这里的输入输出是：

```text
Document -> String
```

`.map()` 是逐个转换数据，不是保存键值对的 `Map` 类型，也不会修改原来的 `Document`。

#### 第五步：把规则、知识库内容和问题拼成提示词

[AiService.java 第 82—85 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:82)

```java
String prompt =
        "请仅根据以下知识库内容回答问题。" +
        "若证据不足，请明确说明知识库中没有足够信息，" +
        "不要使用模型自身知识补充事实。\n\n" +
        "知识库内容:\n" + context + "\n\n" +
        "问题: " + question;
```

大模型不能直接读取 Java 对象，所以必须把内容组织成普通文本：

```text
回答规则
  + context：检索到的知识库证据
  + question：用户问题
  = prompt
```

证据不足时要求模型明确说明不知道，是为了降低脱离知识库编造答案的概率。

#### 第六步：调用模型、缓存并返回答案

[AiService.java 第 87—96 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:87)

```java
ChatLanguageModel model =
        modelFactory.createModel(modelName);

String answer = model.generate(prompt);

redisTemplate.opsForValue().set(
        cacheKey, answer, 5, TimeUnit.MINUTES);

return Map.of(
        "answer", answer,
        "fromCache", false,
        "model", modelName);
```

`createModel()` 创建的是调用模型服务的 Java 客户端，不是在本地训练新模型。`generate(prompt)` 把提示词发送给模型，并得到字符串答案。

新答案会在 Redis 保存五分钟。`fromCache=false` 表示这次确实执行了检索和模型调用。

#### 当前模型错误怎样处理

```text
模型名称未知
  -> ModelFactory 记录警告
  -> 自动改用 Qwen

API Key 错误、网络失败或 model.generate() 报错
  -> AiService 没有 try-catch
  -> 异常继续返回给 Spring
  -> HTTP 500
```

未知名称自动回退还存在一个不一致：实际可能使用 Qwen，但响应中的 `model` 仍然是用户传入的错误名称。

#### 完整调用链

```text
POST /api/ai/ask
  -> @RequestBody 读取 question、sessionId、model
  -> 没指定模型时补上默认模型
  -> 查询 Redis 回答缓存
  -> 未命中缓存：VectorSearchService 检索相关文档
  -> Document 转成 context
  -> 规则 + context + question 组成 prompt
  -> ModelFactory 创建 ChatLanguageModel
  -> model.generate(prompt)
  -> 答案写入 Redis 五分钟
  -> 返回 answer、fromCache、model
```

**结论**

当前同步问答是一个最简 RAG 流程：检索一次、构建一次提示词、调用一次模型并返回答案。它还不是 Agent Loop，因为模型不会自主选择工具并循环执行。

**易错点**

知识库存放在数据库和 Redis 中，不代表大模型自动知道这些内容；只有检索出来并放进 `prompt` 的文本，模型本次调用才能看到。

---

### 010. ModelFactory 与 ModelConfig：从配置到模型客户端

**需求**

项目支持 Qwen、DeepSeek、Kimi 和 Ollama。`AiService` 不应该分别掌握四种客户端的创建细节，只需要根据模型名称取得统一、可调用的模型对象。

#### 第一步：ModelFactory 隔离不同模型的创建方式

[ModelFactory.java 第 32—50 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java:32)

```java
public ChatLanguageModel createModel(String modelName) {
    switch (modelName.toLowerCase()) {
        case "qwen":
            return createQwenModel();
        case "deepseek":
            return createDeepseekModel();
        case "kimi":
            return createKimiModel();
        case "ollama":
            return createOllamaModel();
        default:
            return createQwenModel();
    }
}
```

输入输出：

```text
输入：String modelName
  -> ModelFactory 选择具体创建方法
输出：统一的 ChatLanguageModel
```

`toLowerCase()` 让 `"QWEN"`、`"Qwen"` 和 `"qwen"` 都进入同一个分支。

上层 `AiService` 不需要区分具体模型，统一调用：

```java
model.generate(prompt);
```

#### 第二步：Builder 把配置组装成客户端

[ModelFactory.java 第 76—86 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java:76)

```java
ModelConfig.ModelProperties config =
        modelConfig.getQwen();

return QwenChatModel.builder()
        .apiKey(config.getApiKey())
        .modelName(config.getModelName())
        .build();
```

Builder 的执行过程：

```text
builder() 创建配置器
  -> apiKey(...) 填入访问凭证
  -> modelName(...) 填入具体模型名
  -> build() 生成最终 QwenChatModel
```

这里的“创建模型”不是训练新模型，而是创建一个能够调用远程模型服务的 Java 客户端。

#### 第三步：baseUrl 决定请求发到哪里

[ModelFactory.java 第 88—113 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java:88)

DeepSeek 和 Kimi 都使用：

```java
OpenAiChatModel.builder()
        .apiKey(config.getApiKey())
        .baseUrl(config.getBaseUrl())
        .modelName(config.getModelName())
        .build();
```

三个配置的职责：

```text
baseUrl   -> 请求发到哪个模型服务器
apiKey    -> 使用什么凭证访问
modelName -> 请求服务器上的哪个模型
```

DeepSeek 和 Kimi 支持与 OpenAI 相似的接口格式，因此可以复用 `OpenAiChatModel`。类名不表示请求一定发给 OpenAI，真正的目标由 `baseUrl` 决定。

#### 第四步：Ollama 创建本地模型客户端

[ModelFactory.java 第 116—127 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java:116)

```java
return OllamaChatModel.builder()
        .baseUrl(config.getBaseUrl())
        .modelName(config.getModelName())
        .timeout(Duration.ofMinutes(5))
        .build();
```

Ollama 通常运行在本机，默认不需要云平台 API Key。五分钟表示一次请求最多等待五分钟，不是每五分钟调用一次模型。

#### 第五步：同步客户端与流式客户端

[ModelFactory.java 第 54—72 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ModelFactory.java:54)

```text
ChatLanguageModel
  -> 等模型完成全部生成
  -> 一次得到完整答案

StreamingChatLanguageModel
  -> 模型生成一段，程序接收一段
  -> 多次收到答案片段
```

因此工厂提供两个入口：

```java
createModel(modelName)
createStreamingModel(modelName)
```

二者选择模型的逻辑相似，但创建的客户端类型不同。流式客户端只解决“从模型逐段接收”，答案怎样逐段发给浏览器由后面的 SSE 负责。

#### 第六步：ModelConfig 把 YAML 转成 Java 对象

[ModelConfig.java 第 11—20 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/config/ModelConfig.java:11)

```java
@Component
@ConfigurationProperties(prefix = "langchain4j")
public class ModelConfig {
    private String defaultModel;
    private ModelProperties qwen;
    private ModelProperties deepseek;
    private ModelProperties kimi;
    private ModelProperties ollama;
}
```

两个注解的分工：

```text
@Component
  -> 让 Spring 创建并管理 ModelConfig 对象

@ConfigurationProperties(prefix = "langchain4j")
  -> 读取 application.yml 中 langchain4j 下的配置
  -> 按属性名称和类型填入 ModelConfig
```

[application.yml 第 75—100 行](D:/Project/enterprise-agent/src/main/resources/application.yml:75) 与 Java 字段的对应关系：

```text
langchain4j.defaultModel
  -> modelConfig.defaultModel

langchain4j.qwen.apiKey
  -> modelConfig.qwen.apiKey

langchain4j.deepseek.baseUrl
  -> modelConfig.deepseek.baseUrl
```

环境变量的传递链：

```text
DEEPSEEK_API_KEY
  -> application.yml 的 ${DEEPSEEK_API_KEY}
  -> ModelConfig.deepseek.apiKey
  -> ModelFactory
  -> OpenAiChatModel
```

#### 第七步：static 内部类不等于静态数据

```java
public static class ModelProperties {
    private String apiKey;
    private String baseUrl;
    private String modelName;
}
```

`ModelProperties` 是四种模型共用的配置结构。Spring 仍然会创建四个不同实例：

```text
qwen     -> 一个 ModelProperties 对象
deepseek -> 另一个 ModelProperties 对象
kimi     -> 另一个 ModelProperties 对象
ollama   -> 另一个 ModelProperties 对象
```

`static` 只表示创建 `ModelProperties` 时不需要隐式绑定某个外层 `ModelConfig` 对象：

```java
new ModelConfig.ModelProperties();
```

它不表示四种模型共享同一份配置，也不表示 `apiKey`、`baseUrl` 和 `modelName` 是静态字段。

配置对象与模型客户端的先后顺序：

```text
Spring 启动
  -> 创建 ModelConfig 和各个 ModelProperties
  -> 从 YAML 填入配置

业务需要调用模型
  -> ModelFactory 读取 ModelProperties
  -> 创建真正的模型客户端
```

#### API Key 日志中的三元表达式

```java
config.getApiKey() != null
    ? config.getApiKey().substring(
            0,
            Math.min(8, config.getApiKey().length())) + "..."
    : "null"
```

等价逻辑：

```text
API Key 不为 null
  -> 记录前 8 位或实际长度中更短的一段，再加 ...

API Key 为 null
  -> 记录字符串 "null"
```

`Math.min(8, length)` 防止长度不足 8 时 `substring(0, 8)` 越界。

#### 完整调用链

```text
环境变量
  -> application.yml
  -> @ConfigurationProperties 绑定 ModelConfig
  -> ModelFactory 根据 modelName 选择创建方法
  -> Builder 填入 apiKey、baseUrl、modelName
  -> build() 创建具体模型客户端
  -> 以 ChatLanguageModel 或 StreamingChatLanguageModel 返回
  -> AiService 统一使用
```

**结论**

`ModelConfig` 负责把外部配置变成有类型的 Java 数据；`ModelFactory` 负责把模型名称和配置变成统一的模型客户端。两者共同把“配置管理”和“业务问答”分开。

**易错点**

即使只打印前几位，日志仍会泄露部分 API Key。更安全的做法是完全不记录密钥内容。

---

### 011. SSE 流式问答：从模型片段到浏览器事件

**需求**

同步问答必须等待模型生成完整答案后一次返回。流式问答需要让模型生成一部分，浏览器就看到一部分。

#### 第一步：Controller 创建并返回同一个 SseEmitter

[AiController.java 第 66—99 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/controller/AiController.java:66)

```java
SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

executor.execute(() ->
        aiService.askQuestionStream(
                question, sessionId, emitter, model));

return emitter;
```

`SseEmitter` 表示一条可以多次发送数据的 HTTP 响应通道。Controller 创建它，将同一个对象传给 `AiService`，再把它返回给 Spring：

```text
Controller 创建 emitter
  -> 工作线程拿同一个 emitter 持续发送数据
  -> Spring 使用同一个 emitter 向浏览器写响应
```

请求线程负责尽快返回通道；`executor` 中的工作线程负责后续问答流程。两边引用的是同一个 `SseEmitter` 对象，不是复制出来的两条通道。

#### 第二步：缓存命中时模拟流式返回

[AiService.java 第 110—132 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:110)

缓存中已经保存了完整答案，所以代码逐个字符调用 `emitter.send()`，并暂停 30 毫秒模拟打字效果：

```text
Redis 取出完整答案
  -> 每次发送一个字符
  -> 发送 metadata：fromCache=true
  -> emitter.complete() 关闭响应
```

这不是真正的模型流式生成，而是把已经存在的完整字符串分批发送。

#### 第三步：缓存未命中时接收模型生成片段

[AiService.java 第 163—190 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:163)

```java
StringBuilder fullAnswer = new StringBuilder();
StringBuilder tokenBuffer = new StringBuilder();

model.generate(prompt, new StreamingResponseHandler<AiMessage>() {
    @Override
    public void onNext(String token) {
        fullAnswer.append(token);
        tokenBuffer.append(token);
    }
});
```

模型客户端每收到一个生成片段，就调用一次 `onNext(token)`：

```text
fullAnswer
  -> 累积完整回答，供缓存和会话记忆使用

tokenBuffer
  -> 临时积攒待发送片段
  -> 长度达到 20 后发送 message 事件并清空
```

这里的 `tokenBuffer.length()` 计算的是 Java 字符数量，所以代码实际按约 20 个字符发送一次；注释中的“20 个 token”并不准确。

#### 第四步：由模型客户端通知生成结束

[AiService.java 第 189—258 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:189)

项目没有通过句号或回答长度判断完成。模型服务发出结束信号后，模型客户端调用项目重写的回调方法：

```text
仍在生成
  -> 多次调用 onNext(token)

正常生成结束
  -> 调用 onComplete(response)

模型生成失败
  -> 调用 onError(error)
```

`onComplete()` 的收尾顺序：

```text
发送 tokenBuffer 中不足 20 个字符的剩余内容
  -> 完整答案写入 Redis 五分钟
  -> 保存 user 问题和 assistant 回答到同一个 sessionId
  -> 计算并发送 evaluation 事件
  -> 发送 metadata：fromCache=false、model=modelName
  -> emitter.complete() 正常关闭 SSE 响应
```

事件名称让前端区分不同数据：

```text
message    -> 回答正文
evaluation -> 回答评分
metadata   -> 是否来自缓存、使用的模型
```

评分失败由内部 `try-catch` 隔离，因此不会破坏已经生成的回答。模型生成失败或 SSE 发送失败时，代码使用 `completeWithError(error)` 异常关闭响应。

**结论**

当前流式问答使用同一个 `SseEmitter` 连接 Controller、工作线程和浏览器；模型客户端通过 `onNext` 交付生成片段，通过 `onComplete` 或 `onError` 通知最终结果，项目再负责缓存、会话记忆和关闭连接。

**易错点**

当前 `ResponseEvaluationService` 不是让 LLM 对照原文评估，而是根据回答长度、关键词和检索文档数量进行规则评分，不能可靠证明回答正确或识别幻觉；这部分已经加入项目重构 TODO。

---

### 012. buildEnhancedPrompt：把历史、证据和当前问题组织为模型输入

**需求**

流式问答调用模型前，后端需要把对话历史、检索证据和本轮问题转换成一段文本；模型不能直接读取 Java 的 `Document` 或 `SessionMessage` 对象。

**代码位置**

[AiService.java 第 282—341 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/AiService.java:282)

```java
String prompt = buildEnhancedPrompt(
        question, recentHistory, relevantDocuments);
```

**核心逻辑**

```text
最近对话历史
  + 检索到的文档证据
  + 当前问题
  + 回答规则
  = 一次模型调用的 prompt
```

`recentHistory` 只取最近 3 轮，即最多 6 条用户/助手消息。它让模型理解“它、那个规则”等词指向前面的什么内容。对话历史每次写入 Redis 前还会按轮数和估算 token 做裁剪，因此不是无限增长。

如果检索到文档，代码把文档标题和正文片段写入 `【相关知识库信息】`；如果没有文档，则明确写入“暂无相关知识库内容”。模型因此能区分“没有证据”和“后端漏传了证据”。

最后单独写入 `【当前问题】`。历史和文档只是回答材料，本轮 `question` 才是模型需要完成的任务。

**结论**

当前流式 RAG 是后端先完成检索与上下文选择，再把准备好的文本一次性交给模型；模型本身没有重新发起检索。

**易错点**

`truncateContent(doc.getContent(), 1500)` 只保留每篇文档的前 1500 个字符。若关键规则在后半段，模型根本看不到它。这是当前 RAG 链路的真实缺陷，后续应以相关 chunk/证据片段替代“整篇文档取开头”。

---

### 013. DocumentChunkService：从正文到 MySQL 分块与 Redis 向量索引

**需求**

文档正文不能整篇直接参与检索：内容过长，而且问题通常只与其中一小段相关。系统需要把一篇 `Document` 重建为可管理、可定位的 MySQL 分块，并为每个分块建立能够按语义搜索的 Redis 向量记录。

**代码位置**

[DocumentChunkService.java 第 87—152 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentChunkService.java:87)

**核心逻辑**

```text
输入 documentId
  -> 从 MySQL 重新查询当前 Document
  -> 保存 documentId 与当前正文 content
  -> 删除该文档的旧 MySQL chunk
  -> 按 500 字符、50 字符重叠切成 TextSegment
  -> 每个 segment 写入 MySQL，保存 documentId、chunkIndex、content
  -> 同一段文本转换成 384 维 Embedding
  -> 把 Embedding 与 TextSegment 一起写入 Redis 向量索引
  -> 每 10 块或最后一块报告处理进度
  -> 返回本次生成的分块数量
```

关键代码：

```java
documentChunkRepository.insertChunk(
        docId, i, segment.text());

String embeddingId = embeddingStore.add(
        embeddingModel.embed(segment.text()).content(),
        segment);
```

MySQL 与 Redis 保存同一分块的不同用途：

```text
MySQL DocumentChunk
  -> 保存分块所属文档、顺序和正文
  -> 负责业务查询、来源定位和持久化

Redis 向量索引
  -> 保存 384 维向量
  -> 使用向量相似度快速寻找语义相近的分块
```

`dimension = 384` 表示模型输出的每条向量都由 384 个数字组成。查询向量和已存向量必须使用相同维度，才能按相同坐标计算相似度。当前 Redis 向量索引使用 HNSW 组织邻近向量，并以余弦相似度寻找近似最近邻，避免逐条扫描全部向量。

Redis 中同时保存 `Embedding` 和 `TextSegment` 很重要：`Embedding` 用于计算“哪一段更相似”，`TextSegment` 用于命中后返回对应原文。否则检索结果只有一串向量数字，无法直接成为 RAG 提示词中的证据。

**结论**

`DocumentChunkService` 把持久化正文加工成两套互补数据：MySQL chunk 保留分块身份与原文，Redis 向量索引提供语义召回；二者共同让问题能够先找到相关文本，再把原文交给大模型。

**易错点**

当前重建流程只删除旧 MySQL chunk，没有删除该文档在 Redis 中的旧向量；而 Redis 的 `TextSegment` 又没有携带稳定的 `documentId` 和 `chunkIndex`。因此文档重新处理后可能留下旧向量，检索还要依赖文本内容反查 MySQL。后续改造应给索引记录加入稳定的分块身份，并支持按 `documentId` 精确删除或重建。

---

### 014. 为什么 MySQL 和 Redis 都保存 chunk 信息

**需求**

系统既要可靠保存文档分块的业务身份和原文，又要快速计算“用户问题与哪些分块语义相近”。一份存储很难同时承担关系型业务数据和高效向量检索，因此当前项目使用 MySQL 保存主数据、Redis 保存可重建的向量索引。

**代码位置**

[DocumentChunkService.java 第 129—136 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentChunkService.java:129)

```java
documentChunkRepository.insertChunk(
        docId, i, segment.text());

String embeddingId = embeddingStore.add(
        embeddingModel.embed(segment.text()).content(),
        segment);
```

同一个 `segment` 被写入两套存储，但两边解决的问题不同：

| 存储 | 保存重点 | 系统中的角色 |
| --- | --- | --- |
| MySQL `DocumentChunk` | `chunkId`、`documentId`、`chunkIndex`、正文和关联关系 | 权威业务主数据：负责持久化、关系查询、版本和删除 |
| Redis 向量索引 | 384 维 `Embedding` 和对应 `TextSegment` | 检索索引：负责快速寻找语义相近的分块 |

可以把二者记成：

```text
MySQL 保存“这条知识究竟是什么、属于谁”
Redis 保存“怎样快速找到与问题相似的知识”
```

当前检索链位于 [VectorSearchService.java 第 120—145 行](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/VectorSearchService.java:120)：

```text
问题生成 384 维查询向量
  -> Redis 向量索引找到相似 TextSegment
  -> 根据 segment.text() 查询 MySQL DocumentChunk
  -> 取得 documentId
  -> 根据 documentId 查询完整 Document
```

为什么不只使用 MySQL：当前 MySQL 只是用 BLOB 字段表示向量数据，没有用于最近邻搜索的向量索引；如果把全部向量读到 Java 再逐条计算，数据量增加后查询成本会随分块数量增长。

为什么不只使用 Redis：文档、分块顺序、版本、分类标签和权限属于关系型业务数据。Redis 向量索引应当是可以从 MySQL 正文重新生成的派生数据，而不是唯一数据源：

```text
Redis 索引丢失
  -> 可以读取 MySQL chunk 重新生成向量

MySQL 主数据丢失
  -> 仅凭 Redis 向量无法可靠恢复文档关系、版本和权限
```

**二开决策（尚未实现）**

十天改造先保留已经工作的 Redis 向量召回，再增加 Elasticsearch BM25 关键词检索：

```text
MySQL          -> 权威业务主数据
Redis          -> 语义向量召回
Elasticsearch  -> BM25 关键词检索与元数据过滤
```

这样可以增量完成混合检索，但会增加三套数据的一致性成本。长期可以评估让 Elasticsearch 同时承担 BM25 与向量检索，Redis 只保留回答缓存和会话记忆；不能因为存储更多就认为架构更高级。

**结论**

MySQL 和 Redis 双存不是为了无意义地复制数据，而是将“权威业务数据”和“可重建的检索索引”分开：先用 Redis 快速定位相似知识，再以 MySQL 中的数据作为最终业务依据。

**易错点**

当前 Redis `TextSegment` 没有携带稳定的 `chunkId`、`documentId` 和 `chunkIndex`，所以命中后只能按正文反查 MySQL；正文重复或文档更新时可能定位错误，删除 MySQL chunk 也不会自动删除旧 Redis 向量。理想索引记录应携带稳定 ID，但这一改造目前尚未实现。

---

### 015. RetrievalHit DTO：普通 Java 对象、构造方法与 Spring 注入边界

**需求**

Redis 向量检索和后续 Elasticsearch BM25 检索返回的数据结构不同，但 RRF 融合需要在同一粒度上识别、去重和排序结果。因此二开首先定义统一的 chunk 级检索结果 `RetrievalHit`，让不同召回通道都输出同一种对象。

建议字段：

| 字段 | 含义 |
| --- | --- |
| `documentId` | 分块所属文档的稳定业务 ID |
| `chunkId` | 分块的稳定 ID，也是后续去重和融合的主要依据 |
| `chunkIndex` | 分块在原文中的顺序，用于定位上下文 |
| `documentTitle` | 展示引用来源时使用的文档标题 |
| `content` | 真正交给大模型作为证据的分块原文 |
| `score` | 当前检索通道给出的相关性分数 |
| `source` | 结果来自 Redis、MySQL 兜底还是 Elasticsearch |

`RetrievalSource` 使用枚举表达有限且明确的来源，例如：

```java
REDIS_VECTOR,
MYSQL_KEYWORD_FALLBACK,
ELASTICSEARCH_BM25
```

**为什么选择普通 class**

Java `record` 会自动生成全参构造方法和只读访问方法，适合不可变的结果对象；但当前项目普遍使用普通 Java class。为了保持代码风格统一，本次选择普通 class，并显式声明字段、构造方法和 getter/setter。这里的取舍是“项目一致性优先”，不是因为 record 不能使用。

普通 `RetrievalHit` 需要：

```text
7 个 private 字段
  + 无参构造方法
  + 包含全部字段的构造方法
  + 每个字段的 getter/setter
```

全参构造方法按照字段顺序接收：

```text
documentId, chunkId, chunkIndex, documentTitle,
content, score, source
```

VS Code 安装 Java 扩展后，可以通过 `Source Action -> Generate Constructors` 和 `Generate Getters and Setters` 批量生成样板代码。当前对象暂时不依赖 `equals/hashCode`。

**构造方法不等于 Spring 依赖注入**

`RetrievalHit` 是一次检索产生的普通数据对象，不是单例服务，也不应该注册为 Spring Bean。Service 在得到一条检索结果时主动创建它：

```java
new RetrievalHit(
        documentId, chunkId, chunkIndex,
        title, content, score, source);
```

两个构造方法的作用是：

- 无参构造：方便 Jackson 等框架先创建对象、再通过 setter 填充字段；如果 DTO 只作为响应输出，它通常不是硬性要求，但保留可以统一项目风格。
- 全参构造：方便 Service 一次性组装完整的检索结果，减少多次 setter 调用。

Spring 构造器注入解决的是 Bean 之间的依赖关系。例如 Spring 创建 `SearchService` 时，把 `DocumentRepository` 传给它；这和业务代码创建 `RetrievalHit` 是两件事：

```text
SearchService / Repository
  -> 由 Spring 容器创建和管理
  -> 构造器参数表示依赖，由 Spring 注入

RetrievalHit
  -> 由业务代码按每条搜索结果创建
  -> 构造器参数表示数据，由调用者传入
```

不要为了“自动注入”给 `RetrievalHit` 添加 `@Component` 或 `@Autowired`。如果把这种有状态的结果对象注册成默认单例 Bean，不同请求会共享和覆盖字段，反而会引入并发数据污染。

**面试表达**

> 我先抽象了统一的 chunk 级 RetrievalHit，让 Redis 语义召回与 Elasticsearch BM25 召回都输出稳定的文档和分块身份，后续 RRF 才能按 chunk 去重、融合并返回引用证据。RetrievalHit 只是请求过程中的数据载体，由业务代码创建；Service 和 Repository 才交给 Spring 容器进行依赖注入。

**易错点**

- 把 DTO 构造方法误认为 Spring 构造器注入。
- 给每次请求都不同的结果对象添加 `@Component`，导致默认单例共享可变状态。
- 只有正文和分数，没有 `chunkId`，使多个检索通道无法可靠判断是否命中了同一块内容。
- 把 Redis 相似度和 Elasticsearch BM25 分数直接相加；两者分数空间不同，后续应使用排名融合或先做归一化。

---

### 016. Redis 分块元数据与 EmbeddingMatch 泛型链

**需求**

原项目写入 Redis 的 `TextSegment` 只有正文。向量检索命中后只能按正文反查 MySQL；正文重复时可能关联到错误分块。二开需要让每条 Redis 向量同时携带 `documentId` 和 `chunkIndex`，命中后再通过这两个字段精确查询 MySQL `DocumentChunk`。

写入链路：

```text
原始 TextSegment
  -> Metadata 保存 documentId、chunkIndex
  -> TextSegment.from 生成带元数据的 indexedSegment
  -> embed 将 indexedSegment 正文转换为 384 维向量
  -> embeddingStore.add 同时保存向量与 indexedSegment
```

关键代码：

```java
Metadata metadata = new Metadata()
        .put("documentId", docId)
        .put("chunkIndex", i);

TextSegment indexedSegment =
        TextSegment.from(segment.text(), metadata);

embeddingStore.add(
        embeddingModel.embed(indexedSegment.text()).content(),
        indexedSegment);
```

**本轮第三方 API**

- `Metadata`：LangChain4j 的元数据容器，用于给正文附加业务身份。
- `Metadata.put(key, value)`：按具体数字或字符串类型保存元数据。项目使用的 LangChain4j 0.34.0 已弃用 `add(String, Object)`，应使用类型安全的 `put`。
- `TextSegment.text()`：返回该分块保存的原始正文，类型为 `String`。
- `TextSegment.from(text, metadata)`：用正文和元数据创建新的文本分块。
- `TextSegment.metadata()`：检索命中后取得写入时绑定的元数据。
- `embeddingModel.embed(text)`：把文本转换成嵌入向量响应。
- `content()`：从模型响应中取得真正的 `Embedding`。
- `embeddingStore.add(embedding, segment)`：将向量和对应的文本分块一起写入向量存储，并返回向量记录 ID。

读取时使用：

```java
Long documentId = metadata.getLong("documentId");
Integer chunkIndex = metadata.getInteger("chunkIndex");
```

保存和读取的 key 必须完全一致。

**EmbeddingMatch 中的泛型替换**

`Generic` 是“泛型”，`Type Parameter` 是“类型参数”。LangChain4j 中的 `EmbeddingMatch` 可以近似理解为：

```java
public class EmbeddingMatch<Embedded> {
    public Embedded embedded() { ... }
}
```

这里的 `Embedded` 不是具体类，而是“与向量绑定的对象是什么”的类型占位符。项目声明：

```java
EmbeddingMatch<TextSegment> match
```

因此编译器确定：

```text
Embedded = TextSegment
```

所以泛型类中原本的：

```java
public Embedded embedded()
```

在当前代码中等价于返回：

```java
public TextSegment embedded()
```

完整类型传递链：

```text
EmbeddingStore<TextSegment>
  -> EmbeddingSearchResult<TextSegment>
  -> List<EmbeddingMatch<TextSegment>>
  -> EmbeddingMatch<TextSegment>
  -> match.embedded() 返回 TextSegment
```

`Embedded` 不是在运行过程中被赋值，而是在写出 `EmbeddingMatch<TextSegment>` 时由泛型参数确定。

**embedding 与 embedded 的区别**

| API | 返回内容 | 用途 |
| --- | --- | --- |
| `match.embedding()` | 384 维 `Embedding` 数字向量 | 计算“问题与分块像不像” |
| `match.embedded()` | 当前为 `TextSegment` | 返回命中的正文和元数据 |

例如：

```text
embedding()
  -> [0.12, -0.37, 0.08, ...]
  -> 用于相似度计算

embedded()
  -> 正文：员工每年享有十天年假
  -> documentId：1
  -> chunkIndex：3
  -> 用于返回证据并定位 MySQL 分块
```

一句话记忆：

```text
embedding() 负责“像不像”
embedded() 负责“命中的是哪段原文”
```

**为什么 VS Code 只能跳到 EmbeddingMatch**

`EmbeddingMatch` 的完整包名是 `dev.langchain4j.store.embedding.EmbeddingMatch`，它本身就是 LangChain4j 类。本地 Maven 当前只有编译后的 `langchain4j-core-0.34.0.jar`，没有 `sources.jar` 源码包，因此 VS Code 可能只能展示类结构，不能精确进入方法源码。

需要第三方源码导航时可以执行：

```powershell
.\mvnw.cmd dependency:sources
```

或者启用 VS Code 设置：

```json
"java.maven.downloadSources": true
```

这只改善源码阅读体验，不影响项目编译和运行。

**面试表达**

> 原实现只把正文与向量写入 Redis，命中后依赖正文反查 MySQL，重复文本会造成歧义。我给 TextSegment 增加 documentId 和 chunkIndex 元数据，使向量命中能够精确映射到 MySQL 分块。Embedding 用于相似度计算，embedded TextSegment 用于保留原文和业务身份。

**易错点**

- 混淆 `embedding()` 数字向量与 `embedded()` 原始对象。
- 把泛型参数 `Embedded` 误认为一个具体的 LangChain4j 类。
- 使用已弃用的 `Metadata.add(String, Object)`，而不是类型安全的 `put`。
- 只给向量保存正文、不保存业务身份，导致重复正文无法精确定位。
- 更新索引记录结构后不执行全量索引重建，使 Redis 中继续存在缺少元数据的旧记录。

---

### 017. 从需求反推检索 DTO：一次真实的 N+1 发现与方案修正

这一节记录的重点不是某个 API，而是一次完整的工程分析过程：先做最小改造，随后从性能和最终输出重新审视，发现初版虽然解决了准确性，却没有减少数据库查询；最后按存储职责和数据流重新划分 DTO。面试中，“如何发现问题、为什么修正方案、最终如何取舍”通常比只背最终代码更有价值。

#### 1. 原项目的真实需求与实现

原项目希望根据用户问题返回相关 `Document`，再把文档标题和正文放入大模型提示词。

原始写入流程：

```text
Document 正文
  -> MySQL 保存 DocumentChunk
  -> Redis 保存 384 维向量 + TextSegment 正文
```

原始查询流程：

```text
问题生成查询向量
  -> Redis 找到相似 TextSegment
  -> 取得 segment.text()
  -> MySQL 按正文 findByContentContaining
  -> 取第一条 DocumentChunk
  -> 取得 documentId
  -> MySQL findById 查询完整 Document
  -> 返回 List<Document>
```

原作者的职责划分是：

```text
Redis 负责从全部向量中快速召回相似正文
MySQL 负责确认正文属于哪个文档，并返回完整业务实体
```

Redis 并不是没有发挥作用，它完成了向量召回；问题出在召回后的数据还原：

- 按正文 `contains` 查询可能扫描大量文本。
- 两篇文档正文相同时可能关联错误。
- `chunks.get(0)` 会静默选择第一条。
- 每条向量命中都继续查询 MySQL，产生多次 SQL。
- 最后返回整篇 `Document`，RAG 又把整篇正文截断后放入提示词，而不是直接使用命中的 chunk。

#### 2. 第一版改造解决了什么

第一版改造给 Redis 中的 `TextSegment` 增加：

```text
documentId
chunkIndex
```

注意：原来和现在使用的都是 `TextSegment`；变化不是把 `Segment` 换成 `TextSegment`，而是从“只有正文的 TextSegment”变成“正文 + Metadata 的 TextSegment”。

查询计划变成：

```text
Redis 命中 TextSegment
  -> Metadata 读取 documentId、chunkIndex
  -> MySQL 精确查询 DocumentChunk
  -> MySQL 查询 Document
  -> 组装 RetrievalHit
```

这一版确实解决了定位准确性：不再按正文猜测，而是按 `documentId + chunkIndex` 精确定位。

但是它没有减少查询次数：

```text
每条 Redis 命中
  -> 1 次查询 DocumentChunk
  -> 1 次查询 Document
```

如果 Redis 返回 10 条候选，最坏会产生约 20 次 SQL。这是典型的 `N+1 Query`（N+1 查询问题）：先得到一批 N 条结果，再为每条结果发起额外数据库查询。严格说这里甚至接近 `2N` 次补充查询。

因此第一版改造的准确表述是：

```text
提高了映射准确性
没有真正优化在线查询次数
```

#### 3. 为什么最初认为必须查 MySQL

第一次方案继续查询 MySQL，不是向量检索本身要求这样做，而是沿用了旧返回契约：

```java
List<Document> searchDocuments(...)
```

查询 `DocumentChunk` 是为了取得：

- MySQL `chunkId`
- 权威 chunk 正文
- 验证 Redis 记录是否仍有对应业务数据

查询 `Document` 是为了取得：

- 文档标题
- 完整 Document 实体
- 后续可能需要的版本、分类、权限等业务字段

但检查真实消费者 `AiService` 后发现，它当前主要使用：

```text
Document.title
Document.content
```

新的 RAG 需求并不需要先加载整篇 `Document`。它真正需要的是：

```text
命中的 chunk 正文
文档标题与身份
分块身份
相关性分数
检索来源
```

这正是 chunk 级检索结果，而不是完整 JPA 实体。

#### 4. Redis 实际保存和返回什么

LangChain4j `RedisEmbeddingStore.add(embedding, textSegment)` 会把一条记录写入 Redis JSON，其中包括：

| 数据 | 来源 | 用途 |
| --- | --- | --- |
| Redis 向量记录 ID | RedisEmbeddingStore 自动生成 | 标识 Redis 内部记录 |
| 384 维向量 | `Embedding` | 计算向量相似度 |
| 分块正文 | `TextSegment.text()` | 返回命中的证据原文 |
| 元数据字段 | `TextSegment.metadata()` | 保存 documentId、chunkIndex 等业务身份 |

向量搜索返回 `EmbeddingMatch<TextSegment>`，其中：

```text
score()     -> 相似度分数
embedding() -> 384 维向量
embedded()  -> 命中的 TextSegment 正文与元数据
```

因此只要索引配置正确，Redis 本身已经能够返回：

```text
documentId
chunkIndex
content
score
source = REDIS_VECTOR
```

它当前不能直接提供：

```text
MySQL chunkId
最新的文档标题
完整 Document
最新权限与版本状态
```

#### 5. 排查中发现的 metadataKeys 配置缺口

检查 LangChain4j 0.34.0 的 `RedisEmbeddingStore` 编译结构后发现：写入时会把 `TextSegment.metadata().asMap()` 放入 Redis JSON；但搜索时只会把构建 Redis 索引时配置过的 metadata key 加入返回字段，并据此重建 `TextSegment`。

当前项目写入端和查询端的 builder 都没有配置：

```java
.metadataKeys(...)
```

所以“代码调用 `Metadata.put` 成功”不等于“搜索结果一定能读到元数据”。当前需要在创建 RedisEmbeddingStore 的两个位置保持相同配置：

```java
.metadataKeys(List.of("documentId", "chunkIndex"))
```

然后执行一次全量向量索引重建。否则旧索引结构和新代码不一致，`match.embedded().metadata()` 可能不包含所需字段。

这也是一个真实工程经验：

```text
不能只看写入对象
还要确认存储适配器如何建 schema、搜索时返回哪些字段
```

#### 6. 为什么“按来源设计 DTO”是合理思路

不同存储天然返回不同数据：

```text
Redis
  -> 向量分数、正文、轻量元数据

Elasticsearch
  -> BM25 分数、索引文档字段、过滤信息

MySQL
  -> 权威 chunkId、Document 信息、权限和最新业务状态
```

如果让一个最终 `RetrievalHit` 同时承担“Redis 原始候选、Elasticsearch 原始候选、MySQL 权威数据、最终 API 响应”四种角色，就会出现大量阶段性 `null`，也会让调用方分不清对象是否已经补全。

因此可以按职责拆分：

```text
Redis 返回 RedisVectorCandidate
Elasticsearch 返回 ElasticsearchKeywordCandidate
两者映射成统一 RetrievalCandidate
RRF 对 RetrievalCandidate 做排名融合
MySQL 批量返回 DocumentChunkProjection
Assembler 组装最终 RetrievalHit
```

相关英文：

| 名称 | 英文 | 中文含义 |
| --- | --- | --- |
| DTO | Data Transfer Object | 数据传输对象 |
| Candidate | Candidate | 候选结果 |
| Projection | Projection | 只查询所需字段的数据库投影 |
| Adapter | Adapter | 适配器，把外部返回格式转成项目内部格式 |
| Assembler | Assembler | 组装器，把多份数据合成最终对象 |
| Hydration | Hydration | 数据补全，把轻量候选补成完整结果 |
| Late Materialization | Late Materialization | 延迟物化，最终排名确定后才加载完整数据 |

#### 7. 十天开发周期下的简化方案

理想分层很清楚，但不应为了架构图无节制增加类。LangChain4j 已经用 `EmbeddingMatch<TextSegment>` 表示 Redis 原始命中，Elasticsearch 客户端也会提供自己的 `SearchHit` 类型，因此没有必要机械地给每个第三方返回值再复制一份完全相同的 DTO。

十天内更实际的最小设计是：

```text
EmbeddingMatch<TextSegment>        Redis 第三方返回类型
  -> Redis adapter
  -> RetrievalCandidate            项目内部统一候选

Elasticsearch SearchHit            ES 第三方返回类型
  -> Elasticsearch adapter
  -> RetrievalCandidate            项目内部统一候选

RRF(List<RetrievalCandidate>)
  -> 最终候选 Top K

MySQL 批量 Projection
  -> RetrievalHitAssembler
  -> List<RetrievalHit>             对外完整结果
```

推荐新增的内部对象：

```text
RetrievalCandidate
  documentId
  chunkIndex
  content
  rawScore
  source

DocumentChunkProjection
  chunkId
  documentId
  chunkIndex
  documentTitle
  latestContent

RetrievalHit
  最终完整引用结果
```

`rawScore` 是原始通道分数。Redis 相似度和 Elasticsearch BM25 分数不在同一分数空间，不能直接相加；RRF 使用各自结果列表中的排名进行融合。

#### 8. 推荐的最终查询链路

```text
用户问题
  -> Redis 向量召回 RetrievalCandidate
  -> Elasticsearch BM25 召回 RetrievalCandidate
  -> 使用 documentId + chunkIndex 识别同一分块
  -> RRF 融合并选出最终 Top K
  -> MySQL 一次批量查询 DocumentChunkProjection
  -> 校验文档存在、版本与权限
  -> RetrievalHitAssembler 组装完整 RetrievalHit
  -> chunk 原文进入大模型 prompt
  -> documentTitle、chunkId 等作为引用返回用户
```

数据库访问从候选阶段的逐条查询：

```text
1 次 Redis + 最多 2N 次 MySQL
```

变为最终阶段的批量补全：

```text
Redis + Elasticsearch 并行召回
  + 1 次或少量批量 MySQL 查询
```

MySQL 仍然是 `Source of Truth`（权威数据源）；Redis 和 Elasticsearch 是可重建的检索索引。这样既发挥检索引擎的速度，又不会把可能过期的索引副本当作最终业务事实。

#### 9. 方案演进与当前状态

```text
第一步：发现正文反查存在歧义
  -> 增加 documentId、chunkIndex 元数据

第二步：实现精确 MySQL 回查
  -> 准确性提高，但仍有 2N 查询

第三步：从 Redis 实际字段和最终消费者反推需求
  -> RAG 需要 chunk 证据，不需要完整 Document

第四步：检查第三方存储实现
  -> 发现 metadataKeys 尚未配置

第五步：重新划分 DTO 和查询阶段
  -> 候选召回、排名融合、权威数据补全、最终输出分离
```

截至当前：

- 已实现：`RetrievalHit`、`RetrievalSource`、写入 TextSegment Metadata。
- 已写但需要调整：`toRetrievalHit` 当前仍逐条查询 MySQL。
- 尚未实现：`metadataKeys` 配置、`RetrievalCandidate`、批量 MySQL Projection、RRF 后补全、Elasticsearch 召回。
- 尚未执行：新 schema 对应的 Redis 全量索引重建。

#### 10. 面试表达

> 原项目在 Redis 完成向量召回后，按命中正文反查 MySQL 分块，再逐条加载 Document。这个实现既会在重复正文场景下错误关联，也会产生 N+1 查询。我先给 TextSegment 增加 documentId 和 chunkIndex，解决精确定位；随后沿实际返回字段和 RAG 消费需求复盘，发现逐条精确查询虽然正确但仍不高效。我进一步把检索拆为候选召回、RRF 融合和最终批量补全：Redis 与 Elasticsearch 只输出统一 RetrievalCandidate，排名完成后一次批量查询 MySQL 权威数据，再由 Assembler 组装 RetrievalHit。这样避免了候选阶段的 2N 查询，同时保留了 MySQL 的一致性和权限校验能力。

可能的面试追问：

1. 为什么不把所有字段都存进 Redis，完全不查 MySQL？
   - 可以做索引冗余，但标题、版本和权限更新后存在短暂不一致；MySQL 是权威数据源，最终批量校验更可靠。
2. 为什么不直接把 Redis 分数和 BM25 分数相加？
   - 两种分数含义和尺度不同，直接相加没有可比性，因此使用基于排名的 RRF。
3. 为什么需要 RetrievalCandidate 和 RetrievalHit 两层？
   - Candidate 表示检索阶段的轻量、不一定完整的数据；Hit 表示经过融合和权威补全后可以对外返回的完整证据。
4. metadata 写入成功为什么搜索时仍可能没有？
   - LangChain4j RedisEmbeddingStore 还需要在索引 builder 中声明 metadataKeys，搜索才会返回并重建这些字段。
5. 为什么不一开始就设计出最终架构？
   - 第一版优先以小改动修复错误关联；通过代码审查和数据流分析发现查询放大后再演进。真实工程通常是先验证假设，再根据性能和一致性约束迭代，而不是一次性过度设计。

---

### 018. 双路检索中的 score、rank 与 RRF

### 1. 先分清三种分数

双路检索中不能笼统地把所有数值都命名为 `score`。至少要区分：

```text
rawScore（原始分数）
  -> 某一路检索引擎自己计算的相关性分数

rank（单路排名）
  -> 候选在某一路本次查询结果中的名次

fusionScore（融合分数）
  -> 根据多路 rank 经过 RRF 计算出的最终排序依据
```

它们分别回答不同的问题：

- `rawScore`：这个检索引擎认为候选有多相关？
- `rank`：这个候选在当前检索源中排第几？
- `fusionScore`：综合多个检索源后，这个候选应该排在哪里？

### 2. Redis 与 Elasticsearch 的 rawScore 含义不同

`rawScore = raw score = 原始分数`。

Redis 向量检索的原始分数来自问题向量与分块向量的相似程度。在当前 LangChain4j API 中通过：

```java
match.score()
```

取得。它主要回答“用户问题和这段正文在语义上有多像”，因此擅长处理意思相近但用词不同的表达。

Elasticsearch 的 `_score` 来自 BM25 关键词相关性计算，会综合词频、逆文档频率和文档长度等因素。它主要回答“查询词在正文中匹配得有多强”，因此更擅长制度编号、产品型号、专有名词、部门名和精确关键词。

示例：

```text
Redis：
  A = 0.91
  B = 0.83

Elasticsearch：
  C = 14.7
  A = 8.2
```

这里不能直接计算 `A = 0.91 + 8.2`。两种分数来自不同算法、数值范围和统计口径，就像厘米和千克不能直接相加。即使都归一化到 0～1，不同查询下的分数分布也可能不同，归一化方式本身还会影响排序。

因此 `rawScore` 适合：

- 保留单路检索信息；
- 调试为什么某一路命中；
- 日志与离线评测；
- 以后尝试加权融合或归一化。

但它不适合直接跨检索源相加。

### 3. rank 是本次查询、单个检索源内的相对名次

`rank = ranking position = 排名位置`。

```text
Redis：
  rank 1 -> A，rawScore 0.91
  rank 2 -> B，rawScore 0.83
  rank 3 -> C，rawScore 0.76

Elasticsearch：
  rank 1 -> C，rawScore 14.7
  rank 2 -> A，rawScore 8.2
  rank 3 -> D，rawScore 5.4
```

于是：

```text
A：Redis rank = 1，Elasticsearch rank = 2
C：Redis rank = 3，Elasticsearch rank = 1
```

`rank` 一般从 1 开始。它只在“某一次查询的某一路结果”中有意义，不是分块的永久属性。同一个分块在 Redis 和 Elasticsearch 中可以拥有不同排名。

### 4. RRF 如何用 rank 融合双路结果

`RRF = Reciprocal Rank Fusion = 倒数排名融合`。

经典公式：

```text
某一路贡献分 = 1 / (k + rank)
fusionScore = 同一候选在所有命中来源中的贡献分之和
```

其中：

- `rank` 是候选在该来源中的排名；
- `k` 是平滑常数，常见取值为 60；
- 同一个分块被双路命中时，需要先用 `documentId + chunkIndex` 识别为同一候选，再累加两路贡献。

当 `k = 60` 时：

```text
A：Redis rank 1，ES rank 2
fusionScore(A) = 1/61 + 1/62 ≈ 0.03252

B：只有 Redis rank 2
fusionScore(B) = 1/62 ≈ 0.01613

C：Redis rank 3，ES rank 1
fusionScore(C) = 1/63 + 1/61 ≈ 0.03227
```

最终排名为 A、C、B。A 和 C 都得到双路认可，因此明显领先只被单路命中的 B。

RRF 的核心不是判断哪一路的原始分数更大，而是：

> 每个检索引擎分别给出自己最认可的候选；一个分块被多个检索源同时排在前面，就获得更高的融合分数。

### 5. fusionScore 不是概率

`fusionScore = fusion score = 融合分数`，只用于同一次查询内的最终相对排序。它：

- 不是向量相似度；
- 不是 BM25 分数；
- 不是正确率；
- 不是相关概率；
- 不能把 `0.032` 解释为“3.2% 相关”。

因此前端通常不需要展示 `fusionScore`。它主要用于后端排序、调试日志、检索评测和问题排查。

### 6. DTO 字段应该体现所处阶段

`RetrievalCandidate = 检索候选项`，属于单路召回阶段，推荐表达：

```text
documentId
chunkIndex
rawScore
rank
source
```

- `rawScore`：Redis 相似度或者 Elasticsearch BM25 分数；
- `rank`：在该检索源中的排名；
- `source`：本候选来自 Redis 还是 Elasticsearch。

`RetrievalHit = 最终检索命中结果`，属于 RRF 融合及 MySQL 批量补全之后，推荐表达：

```text
documentId
chunkId
chunkIndex
documentTitle
content
fusionScore
sources
```

这里应该使用复数 `sources`，因为一个最终命中可能同时来自 `REDIS_VECTOR` 和 `ELASTICSEARCH_BM25`。当前 `RetrievalHit` 中含义笼统的 `score` 和单数 `source`，后续应考虑分别调整为 `fusionScore` 和 `Set<RetrievalSource> sources`。

### 7. RRF 的局限与当前取舍

RRF 只保留名次，不保留候选之间的原始分数差距。例如下面两组结果在 RRF 看来都是第一名和第二名：

```text
A = 0.95，rank 1
B = 0.94，rank 2

A = 0.95，rank 1
B = 0.50，rank 2
```

它看不出第二组中 A 与 B 的差距明显更大。更复杂的系统可以继续尝试分数归一化、加权融合、Learning to Rank（排序学习）或 Cross-Encoder Reranker（交叉编码器重排）。

但当前项目只有十天，Redis 语义检索 + Elasticsearch BM25 精确检索 + RRF 排名融合已经形成完整、可靠且容易讲清楚的工程闭环。现阶段不应为了算法复杂度继续扩大范围。

### 8. 面试表达

> Redis 向量检索与 Elasticsearch BM25 的原始分数量纲不同：向量分数表示语义相似度，BM25 分数来源于词频、逆文档频率和文档长度，因此不能直接相加。我把两路结果转换成统一的 RetrievalCandidate，保留各自的 rawScore，并根据候选在单路结果中的 rank 使用 RRF 进行融合。双路同时靠前的分块会获得更高的 fusionScore。融合后只对 Top K 候选批量查询 MySQL，再组装最终 RetrievalHit，从而避免对所有召回候选逐条回表。

可能的面试追问：

1. 为什么保留 rawScore，既然 RRF 不使用它？
   - rawScore 可用于日志、可解释性、问题排查和离线评测，也为以后尝试归一化或加权融合保留数据。
2. fusionScore 能不能返回前端作为相关概率？
   - 不能。它只是本次查询中基于名次累加的排序值，不是经过概率校准的相关性概率。
3. RRF 有什么不足？
   - 它忽略原始分数之间的差距，以稳健和简单换取了一部分精细信息。
4. 为什么当前仍选择 RRF？
   - 两路原始分数不可直接比较，项目周期又只有十天；RRF 不需要训练和复杂标定，容易实现、验证和解释，更符合当前工程目标。

### 9. 当前代码怎样完成双路融合

**需求**

Redis 和 Elasticsearch 已经能各自返回 `RetrievalCandidate`，但 RAG 最终只需要一份排好序的候选列表。因此还需要解决三个问题：

```text
同一个 chunk 被双路召回时不能出现两份
  -> 两路 rawScore 不能直接相加
  -> 融合后只保留 Top K，后面再批量查询 MySQL
```

**代码入口**

- [FusedRetrievalCandidate.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/FusedRetrievalCandidate.java)：保存 `documentId + chunkIndex + fusionScore + sources`。
- [RrfFusionService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/RrfFusionService.java)：负责同一 chunk 的识别、RRF 累加、降序排列和 Top K 截断。
- [HybridRetrievalService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/HybridRetrievalService.java)：依次取得 Redis 候选和 Elasticsearch 候选，再交给 RRF 融合。

**核心执行链**

```text
HybridRetrievalService.search(query, candidateLimit, minVectorScore, topK)
  -> VectorSearchService.searchVectorCandidates(...)
  -> ElasticsearchSearchService.searchBm25Candidates(...)
  -> RrfFusionService.fuse(redisCandidates, elasticsearchCandidates, topK)
```

`RrfFusionService` 使用：

```java
Map<String, FusedRetrievalCandidate>
```

保存已经出现过的 chunk，键为：

```text
documentId + "_" + chunkIndex
```

处理一条候选时：

```text
Map 中不存在
  -> 创建 fusionScore = 0、sources = 空集合的融合候选

无论是否已存在
  -> fusionScore += 1 / (60 + rank)
  -> sources 加入当前 RetrievalSource
```

所以，同一 chunk 被 Redis 和 Elasticsearch 同时召回时，不会产生两条结果，而是在同一个对象上累加两路贡献并保留两个来源。全部处理完成后，按 `fusionScore` 降序排列，再安全截取：

```java
subList(0, Math.min(topK, result.size()))
```

**真实验收**

[RrfFusionServiceTest.java](D:/Project/enterprise-agent/src/test/java/com/kb/demo/service/RrfFusionServiceTest.java) 已验证：

```text
同一 chunk 双路去重并累加
单路候选仍被保留
fusionScore 降序排列
Top K 和非正数 topK 边界
```

结果为 2 条测试通过，0 失败、0 错误。

[HybridRetrievalServiceIT.java](D:/Project/enterprise-agent/src/test/java/com/kb/demo/service/HybridRetrievalServiceIT.java) 又把同一测试 chunk 真实写入本地 Redis Stack 和 Elasticsearch，随后调用 `HybridRetrievalService.search()`。实际观察到 Redis 返回候选，最终融合对象的 `sources` 同时包含：

```text
REDIS_VECTOR
ELASTICSEARCH_BM25
```

结果为 1 条集成测试通过，0 失败、0 错误。这证明的不是“公式能算”，而是：

```text
真实 Redis 向量召回
  + 真实 Elasticsearch BM25 召回
  -> 用同一 documentId + chunkIndex 识别 chunk
  -> RRF 合并为一个候选
```

**一次真实踩坑**

当前 LangChain4j 0.34.0 的 `RedisEmbeddingStore` 接口虽然暴露了 `remove(id)`，但该 Redis 实现没有支持它，测试清理时会抛出 `UnsupportedOperationException`。因此测试使用 Jedis 按测试专用 `documentId` 精确找到并删除 `embedding:*` 数据，不能把“接口上存在方法”误认为“所有实现都支持该能力”。

**结论**

现在第 2 天的核心检索闭环已经有运行证据：两路候选拥有统一身份，RRF 不混加异构 rawScore，而是按 rank 合并、排序并保留来源；下一步才是用融合后的 Top K 批量查询 MySQL，组装 `RetrievalHit` 给 RAG 使用。

---

### 019. Redis chunk 候选检索：从元数据写入到真实集成测试

**需求**

原项目的 Redis 向量命中只返回分块正文，随后按正文反查 MySQL，再逐条查询 Document。这既可能在重复正文场景下关联错误，也会产生候选数量放大后的 N+1 查询。

本轮改造的目标不是直接返回最终答案，而是先让 Redis 独立返回轻量、可精确定位的 `RetrievalCandidate`：

```text
Redis 向量命中
  -> documentId + chunkIndex 精确标识分块
  -> rawScore + rank 表示本路检索信息
  -> source = REDIS_VECTOR
  -> 候选阶段不查询 MySQL
```

**代码位置**

- [RetrievalCandidate.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/RetrievalCandidate.java)：Redis 与 Elasticsearch 共用的内部候选契约。
- [DocumentChunkService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/DocumentChunkService.java)：写入 `TextSegment` 正文、向量和分块元数据。
- [VectorSearchService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/VectorSearchService.java)：执行 Redis 向量搜索并转换候选。
- [VectorSearchServiceRedisIT.java](D:/Project/enterprise-agent/src/test/java/com/kb/demo/service/VectorSearchServiceRedisIT.java)：使用本地真实 Redis 的集成冒烟测试。

**核心数据对象**

```java
public class RetrievalCandidate {
    private final Long documentId;
    private final Integer chunkIndex;
    private final double rawScore;
    private final int rank;
    private final RetrievalSource source;
}
```

`RetrievalCandidate = Retrieval（检索）+ Candidate（候选项）`。它只在检索流水线内部流转，不是前端响应，也不承担 MySQL 权威数据补全。

当前不保存 `content`，因为候选阶段只需要识别、去重和排序；RRF 确定 Top K 后，再批量查询 MySQL 获取最新正文。

**写入链路**

```java
Metadata metadata = new Metadata()
        .put("documentId", docId)
        .put("chunkIndex", i);

TextSegment indexedSegment = TextSegment.from(segment.text(), metadata);

embeddingStore.add(
        embeddingModel.embed(indexedSegment.text()).content(),
        indexedSegment);
```

这里同时绑定三类数据：

```text
Embedding 向量
  -> 用于计算语义相似度

TextSegment.text
  -> 向量命中后对应的原文

TextSegment.metadata
  -> 原文属于哪个 documentId、哪个 chunkIndex
```

Redis builder 的写入端和查询端都声明：

```java
.metadataKeys(List.of("documentId", "chunkIndex"))
```

两者职责不同：

```text
Metadata.put(...)
  -> 给当前 TextSegment 写入具体元数据值

metadataKeys(...)
  -> 告诉 RedisEmbeddingStore 索引和查询需要处理哪些元数据字段
```

只调用 `Metadata.put` 不足以让既有 Redis 索引自动增加字段；schema 改变后必须删除旧索引并重新处理文档。

**查询与转换链路**

```text
query
  -> embeddingModel.embed(query)
  -> EmbeddingSearchRequest
  -> embeddingStore.search(request)
  -> EmbeddingSearchResult<TextSegment>
  -> result.matches()
  -> EmbeddingMatch<TextSegment>
  -> RetrievalCandidate
```

候选转换只读取 Redis 返回值：

```java
TextSegment textSegment = match.embedded();
Metadata metadata = textSegment.metadata();

Long documentId = metadata.getLong("documentId");
Integer chunkIndex = metadata.getInteger("chunkIndex");

return new RetrievalCandidate(
        documentId,
        chunkIndex,
        match.score(),
        rank,
        RetrievalSource.REDIS_VECTOR);
```

该方法内部不再出现 `DocumentRepository` 或 `DocumentChunkRepository`，因此 Redis 候选数量不会直接放大成逐条 MySQL 查询。

`EmbeddingMatch` 没有保存排名，所以外层遍历根据 Redis 已排序列表的位置生成：

```java
toRetrievalCandidate(matches.get(index), index + 1)
```

使用 `index + 1` 是因为 Java List 下标从 0 开始，而检索排名从 1 开始。无效 metadata 被过滤后，不应重新压缩后续候选的 rank，否则会人为提高它的 RRF 贡献。

**真实验证**

1. Maven 编译通过：77 个 Java 源文件编译成功，`BUILD SUCCESS`。
2. 管理员调用 `POST /api/documents/rebuild-vector-index`，根据新 schema 重建 Redis 向量和 MySQL 分块。
3. Redis `FT.INFO document-embeddings` 的实际结果：

```text
dimension = 384
documentId 字段存在
chunkIndex 字段存在
num_docs = 3
indexing failures = 0
percent_indexed = 1
```

4. Redis 抽样记录实际返回：

```text
documentId = 1
chunkIndex = 0
text = 对应分块原文
```

5. 新增真实 Redis 集成测试：

```java
List<RetrievalCandidate> candidates =
        service.searchVectorCandidates("智能搜索", 3, 0.0);

assertThat(candidates).isNotEmpty();
assertThat(candidates.get(0).getRank()).isEqualTo(1);
assertThat(candidates.get(0).getSource())
        .isEqualTo(RetrievalSource.REDIS_VECTOR);
```

执行：

```powershell
.\mvnw.cmd -Dtest=VectorSearchServiceRedisIT test
```

实际结果：

```text
Redis 向量结果数量 = 3
Tests run: 1
Failures: 0
Errors: 0
BUILD SUCCESS
```

测试使用：

```java
ReflectionTestUtils.setField(service, "redisHost", "localhost");
ReflectionTestUtils.setField(service, "redisPort", 6379);
```

`ReflectionTestUtils = Reflection Test Utilities = 反射测试工具`。测试中手动创建的 `VectorSearchService` 不是 Spring Bean，因此 `@Value` 不会自动注入 Redis 地址；这个工具只在测试里给私有配置字段设置值。

测试类使用 `IT = Integration Test = 集成测试` 后缀，表示它依赖真实外部组件。普通单元测试不应默认依赖本机 Redis，因此该测试通过 `-Dtest=VectorSearchServiceRedisIT` 显式运行。

**结论**

Redis chunk 检索链路已经从“按正文反查 MySQL”改为“索引携带精确元数据并直接返回轻量候选”，而且已经通过 Redis schema、样本记录和 Java 集成测试三层验证。下一步是让 Elasticsearch BM25 也返回相同的 `RetrievalCandidate`，再在第 2 天进行数据同步与 RRF 融合。

**面试表达**

> 原项目的向量命中只绑定正文，检索后需要按正文反查 MySQL，存在重复正文误关联和 N+1 查询。我给 TextSegment 增加 documentId、chunkIndex 元数据，并在 RedisEmbeddingStore 两端声明 metadataKeys；随后把 Redis 命中转换成只包含定位信息、原始分数、排名和来源的 RetrievalCandidate，候选阶段不再访问 MySQL。schema 修改后我执行全量索引重建，并通过 FT.INFO、样本记录和真实 Redis 集成测试验证字段与转换链路。最终 Redis 返回 3 个候选，测试 1 条通过、0 失败。

---

### 020. Elasticsearch BM25 候选检索：Mapping、写入、刷新与真实排名

**需求**

Redis 向量检索擅长召回“意思相近”的文本，但制度编号、产品型号、专有名词等精确关键词可能更适合文本检索。为了让后续 RRF 同时利用语义与关键词信号，本轮新增一条独立的 Elasticsearch BM25 候选链路：

```text
用户 query
  -> Elasticsearch 在 content 字段执行 BM25 匹配
  -> 返回命中 chunk 的身份与原始分数
  -> 按 Elasticsearch 结果顺序生成 rank
  -> 转成统一 RetrievalCandidate
  -> 候选阶段不查询 MySQL
```

`BM25 = Best Matching 25 = 一种文本相关性排序算法`。名称中的 25 是算法版本名称的一部分，不表示“返回 25 条”；返回数量由 `maxResults` 控制。

**代码位置**

- [pom.xml](D:/Project/enterprise-agent/pom.xml)：引入 Spring Data Elasticsearch Starter，由 Spring Boot 3.2.10 管理 Java Client 8.10.4 版本。
- [application.yml](D:/Project/enterprise-agent/src/main/resources/application.yml)：配置 `spring.elasticsearch.uris`。
- [docker-compose.yml](D:/Project/enterprise-agent/docker/docker-compose.yml)：提供本地 Elasticsearch 8.10.4 单节点容器。
- [ElasticsearchChunkDocument.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/dto/ElasticsearchChunkDocument.java)：定义 Elasticsearch `_source` 的 chunk 文档结构。
- [ElasticsearchSearchService.java](D:/Project/enterprise-agent/src/main/java/com/kb/demo/service/ElasticsearchSearchService.java)：负责建索引、写入、刷新和 BM25 候选检索。
- [ElasticsearchSearchServiceIT.java](D:/Project/enterprise-agent/src/test/java/com/kb/demo/service/ElasticsearchSearchServiceIT.java)：使用本地真实 Elasticsearch 的集成测试。

**索引结构与职责**

`document-chunks` 索引使用最小 Mapping：

```text
documentId  -> long     -> 分块所属文档的稳定 ID
chunkIndex  -> integer  -> 分块在文档中的位置
content     -> text     -> 需要分词并参与 BM25 搜索的正文
```

其中 `content` 必须是 `text`，因为 Elasticsearch 需要对正文进行分词并建立倒排索引；两个 ID 字段只负责定位，不参与本轮全文匹配。

`ElasticsearchChunkDocument` 保留无参构造方法和 setter，是因为 Java Client 收到搜索结果后，需要把 JSON `_source` 反序列化回这个对象。

**建索引与写入**

```java
boolean exists = elasticsearchClient.indices()
        .exists(e -> e.index(INDEX_NAME))
        .value();

if (exists) {
    return;
}
```

这段代码完成幂等判断：第一次调用时创建索引，之后重复调用只确认索引已存在，不会因重复创建而报错。`exists(...)` 返回响应对象，`.value()` 取得真正的布尔值。

写入时使用稳定的 Elasticsearch `_id`：

```java
.id(document.getDocumentId() + "_" + document.getChunkIndex())
.document(document)
```

同一 `documentId + chunkIndex` 再次写入会覆盖对应索引记录，而不是不断新增重复 chunk。

#### Index、Mapping、_id 和 document 不是同一层

`Index` 在 Elasticsearch API 中既可以作名词，也可以作动词：

```text
Index（名词）
  -> 可搜索的数据集合，本项目是 document-chunks

Index document（动词）
  -> 把一条 JSON 文档写入该集合，并建立搜索结构
```

`createIndexIfAbsent()` 创建的是容器和 Mapping 字段规则，不写入真实 chunk：

```text
documentId 只能按 long 处理
chunkIndex 只能按 integer 处理
content 按 text 分词并建立倒排索引
```

`indexChunk(document)` 才写入一条具体数据。假设 Java 对象中保存：

```text
documentId = 12
chunkIndex = 3
content = "员工每年享有十天年假"
```

最终 Elasticsearch 文档近似为：

```json
{
  "_index": "document-chunks",
  "_id": "12_3",
  "_source": {
    "documentId": 12,
    "chunkIndex": 3,
    "content": "员工每年享有十天年假"
  }
}
```

这里有两条独立的数据路径：

```text
.id(documentId + "_" + chunkIndex)
  -> 只生成 Elasticsearch 记录标识 _id = "12_3"

.document(document)
  -> 把 ElasticsearchChunkDocument 序列化为 JSON
  -> 独立写入 documentId、chunkIndex、content
```

Elasticsearch 不会从 `"12_3"` 中解析 `chunkIndex`。下划线只是让 `_id` 稳定且易于识别；真正的 `chunkIndex=3` 来自 `document` 对象自身字段。Day 1 的集成测试通过构造方法创建该对象；Day 2 的正式链路将使用 `docId、i、segment.text()` 创建真实分块对象。

Elasticsearch 是 `NRT = Near Real-Time = 近实时` 搜索系统。写入成功表示文档已被接收，但不保证立刻能被搜索；因此批量写入后统一调用：

```java
elasticsearchClient.indices()
        .refresh(r -> r.index(INDEX_NAME));
```

测试需要立即搜索，所以显式刷新；正式批量同步不应每写一条就刷新，否则会增加索引开销。

#### Bulk：为什么需要三层 Builder

一篇文档可能切出很多 chunk。逐条调用 `indexChunk()` 会为每块发送一次请求；`Bulk = 批量操作` 将多个写入操作放进一次请求：

```text
逐条写入：20 个 chunk -> 约 20 次请求
Bulk 写入：20 个 chunk -> 1 次包含 20 个操作的请求
```

当前已实现的批量结构：

```java
BulkResponse response = elasticsearchClient.bulk(bulkBuilder -> {
    for (ElasticsearchChunkDocument document : documents) {
        bulkBuilder.operations(operationBuilder ->
                operationBuilder.index(indexBuilder ->
                        indexBuilder
                                .index(INDEX_NAME)
                                .id(document.getDocumentId()
                                        + "_"
                                        + document.getChunkIndex())
                                .document(document)));
    }
    return bulkBuilder;
});
```

三层 Builder 的职责：

```text
bulkBuilder
  -> 代表整个 BulkRequest
  -> 装一批操作

operationBuilder
  -> 代表批量请求中的一个操作
  -> .index(...) 表示本操作的类型是“写入”

indexBuilder
  -> 配置这一条写入操作
  -> .index(INDEX_NAME) 指定写到哪里
  -> .id(...) 指定记录身份
  -> .document(document) 指定写入什么 JSON 数据
```

这里两个 `.index()` 同名但不属于同一个对象：

```text
operationBuilder.index(...)
  -> 选择操作类型：写入，而不是 delete/update

indexBuilder.index("document-chunks")
  -> 指定目标索引库
```

`operations()` 只把当前 chunk 追加到请求，不会立即访问 Elasticsearch。循环结束并 `return bulkBuilder` 后，外层 `bulk()` 才一次发送整批请求。

#### BulkResponse：整体请求成功不等于每条成功

Bulk 可能出现 9 条成功、1 条失败，所以必须在 `bulk()` 返回之后检查：

```java
if (response.errors()) {
    StringBuilder errorMessage = new StringBuilder();

    for (BulkResponseItem item : response.items()) {
        if (item.error() != null) {
            errorMessage.append("id=")
                    .append(item.id())
                    .append(", status=")
                    .append(item.status())
                    .append(", reason=")
                    .append(item.error().reason())
                    .append("; ");
        }
    }

    throw new IllegalStateException(
            "Elasticsearch 批量写入部分失败：" + errorMessage);
}
```

执行顺序不能颠倒：

```text
箭头函数组装 BulkRequest
  -> return builder
  -> bulk() 发送并等待 Elasticsearch 执行
  -> 得到 BulkResponse
  -> response.errors() 检查局部失败
```

`response.items()` 返回每个操作的结果；`item.id()`、`status()` 和 `error().reason()` 用于定位失败记录。抛出异常只能让上层知道同步未完整完成，不会撤销已经成功写入的其他 chunk；后续可依靠稳定 `_id` 重试覆盖，或通过索引重建修复。

**BM25 查询与候选转换**

核心查询：

```java
SearchResponse<ElasticsearchChunkDocument> response =
        elasticsearchClient.search(request -> request
                        .index(INDEX_NAME)
                        .size(maxResults)
                        .query(queryBuilder -> queryBuilder
                                .match(match -> match
                                        .field("content")
                                        .query(query))),
                ElasticsearchChunkDocument.class);
```

这段 Builder API 对应的查询结构是：

```json
{
  "size": 3,
  "query": {
    "match": {
      "content": "Redis 向量检索"
    }
  }
}
```

关键 API：

- `.index(INDEX_NAME)`：指定搜索 `document-chunks` 索引。
- `.size(maxResults)`：限制最多返回多少条命中。
- `.match(...)`：构造全文匹配查询。
- `.field("content")`：指定在正文列中搜索。
- `.query(query)`：传入用户的查询文本。
- `ElasticsearchChunkDocument.class`：告诉客户端把每条 JSON `_source` 反序列化成什么 Java 类型。
- `response.hits().hits()`：先取得本次搜索的命中集合信息，再取得真正的 `List<Hit<...>>`。
- `hit.source()`：取得 `_source`，即 `documentId`、`chunkIndex` 和 `content`。
- `hit.score()`：取得 Elasticsearch 计算的 BM25 原始 `_score`。

#### `.class`、泛型擦除与反射式反序列化

查询方法同时出现：

```java
SearchResponse<ElasticsearchChunkDocument>
ElasticsearchChunkDocument.class
```

两者作用不同。泛型主要帮助编译期类型检查；Java存在 `Type Erasure = 类型擦除`，运行时不能只依靠 `SearchResponse<ElasticsearchChunkDocument>` 确定 `_source` 应还原成什么类型。因此查询显式传入：

```java
ElasticsearchChunkDocument.class
```

`.class` 不创建业务对象，而是取得运行时的 `Class<ElasticsearchChunkDocument>` 类型信息。Java Client 的 JSON Mapper 据此完成：

```text
Elasticsearch 返回 JSON _source
  -> 检查 ElasticsearchChunkDocument 的构造方法与属性
  -> 调用无参构造创建空对象
  -> 按字段名调用 setter 填入 documentId、chunkIndex、content
  -> 放入 Hit<ElasticsearchChunkDocument>
  -> hit.source() 返回类型安全的 Java 对象
```

这就是反射在当前项目中的实际用途：框架在运行期间读取类的构造方法、字段和 setter，并创建、填充事先不知道具体类型的对象。`ElasticsearchChunkDocument` 保留无参构造和 setter，是这条常规反序列化路径最直接、兼容的写法。

写入与读取正好相反：

```text
.document(document)
  -> Java 对象序列化为 JSON _source

ElasticsearchChunkDocument.class
  -> JSON _source 反序列化为 Java 对象
```

完整转换链：

```text
SearchResponse<ElasticsearchChunkDocument>
  -> List<Hit<ElasticsearchChunkDocument>>
  -> hit.source() 取得 documentId、chunkIndex
  -> hit.score() 取得 BM25 rawScore
  -> candidates.size() + 1 生成连续 rank
  -> source = ELASTICSEARCH_BM25
  -> List<RetrievalCandidate>
```

这里使用 `candidates.size() + 1`，是因为无效命中会先被跳过，最终候选的排名仍需保持从 1 开始连续。BM25 `_score` 只保留为本路 `rawScore`，不能与 Redis 向量相似度直接相加；第 2 天的 RRF 会使用两路各自的 `rank` 融合。

**真实验证**

本地环境实际状态：

```text
Elasticsearch = 8.10.4
cluster health = green
Spring Elasticsearch Java Client = UP
```

集成测试向 `document-chunks` 写入 3 条 chunk，统一刷新后查询专用词 `bm25rankingprobe`。实际结果：

```text
总命中数 = 2
第 1 名：documentId = 920001，_score = 0.7385771
第 2 名：documentId = 920002，_score = 0.4700036
两条 source 均为 ELASTICSEARCH_BM25

Tests run: 1
Failures: 0
Errors: 0
```

第一个文档多次包含测试词，因此 BM25 分数和排名都高于只包含一次的第二个文档。这同时验证了 Mapping、写入、refresh、match 查询、`_source` 反序列化、`_score` 获取和候选转换。

**当前边界**

今天完成的是 Elasticsearch 独立候选检索，不是三套存储同步的完整闭环。文档上传、更新和删除时自动同步 Redis 与 Elasticsearch，以及已有 MySQL chunk 的全量重建，属于第 2 天。

Day 2 已完成 `indexChunks()` 的 Bulk 组装、局部失败提取和静态检查，并已接入 `DocumentChunkService`。正式分块流程现在会先收集一篇文档的全部 `ElasticsearchChunkDocument`，循环结束后统一调用 `indexChunks()` 和 `refreshIndex()`。这条正式同步链尚未执行运行验证，不能与 Day 1 的独立 Elasticsearch 集成测试混为一谈。

接入的业务原因是：

```text
DocumentChunkService 生成真实 segment
  -> 需要调用 ElasticsearchSearchService.indexChunks()
  -> 但实例方法必须通过一个 ElasticsearchSearchService 对象调用
  -> 该 Service 内部又依赖已配置好的 ElasticsearchClient
  -> 因此由 Spring 把现成的 Service Bean 注入 DocumentChunkService
```

DTO 与 Service 的创建方式不同：

```text
ElasticsearchChunkDocument
  -> 只承载 docId、chunkIndex、content
  -> 每个 chunk 由业务代码 new

ElasticsearchSearchService
  -> 持有连接外部系统的 ElasticsearchClient
  -> 是长期协作对象，由 Spring 创建并注入
```

注入的根本目的不是少写一个 `new`，而是让 `DocumentChunkService` 获得“已经带有正确客户端配置的 Elasticsearch 写入能力”，同时不承担客户端创建、配置读取和连接生命周期。字段注入时，Spring启动后会按类型找到 `ElasticsearchSearchService` Bean，再通过反射把对象引用写入 private 字段；构造器注入则在创建对象时直接传入依赖。

#### deleteByQuery：重新分块前按 documentId 清理旧 chunk

稳定 `_id = documentId + "_" + chunkIndex` 可以覆盖相同位置的 chunk，却不会自动删除本次已经不存在的位置：

```text
旧正文生成：12_0、12_1、12_2
新正文生成：12_0、12_1

只重新写入
  -> 12_0、12_1 被覆盖
  -> 旧 12_2 仍然存在
  -> BM25 可能召回已经失效的正文
```

因此重新写入一篇文档的 ES 分块前，需要先执行一次：

```text
按 documentId 删除全部旧 chunk
  -> 批量写入本次生成的全部新 chunk
  -> 最后统一 refresh
```

当前已实现的删除方法：

```java
public long deleteByDocumentId(Long documentId) throws IOException {
    DeleteByQueryResponse response =
            elasticsearchClient.deleteByQuery(request -> request
                    .index(INDEX_NAME)
                    .query(query -> query
                            .term(term -> term
                                    .field("documentId")
                                    .value(documentId)
                            )
                    )
            );

    return response.deleted() == null
            ? 0L
            : response.deleted();
}
```

对应的 Elasticsearch 请求语义是：

```json
{
  "query": {
    "term": {
      "documentId": 12
    }
  }
}
```

`term` 在这里按数值字段精确匹配。官网“按唯一属性删除一条文档”的示例还带有 `max_docs: 1`，但本项目不能照搬：Elasticsearch 中每个 chunk 都是一条文档，同一篇业务文档的多个 chunk 共享相同 `documentId`。限制为 1 只会删掉一块，仍然留下旧数据。

响应 JSON 中的 `deleted` 表示成功删除的 ES 文档数量，Java Client 将它映射为 `response.deleted()`。当前方法返回 `long`，所以字段缺失时以 `0L` 兜底。

当前状态必须准确区分：

```text
deleteByDocumentId()
  -> 代码已经写入并完成静态阅读
  -> 尚未执行真实 Elasticsearch 运行验证

processDocumentWithProgress()
  -> 尚未在 indexChunks() 前调用 deleteByDocumentId()
  -> 这是恢复开发后的下一个代码断点
```

删除必须在每篇文档的批量写入前调用一次，不能放进逐 chunk 循环；否则每写一块之前都会再次清空同一篇文档刚写入的块。

#### 怎样从官网推导陌生 Builder API

第三方文档不需要从头阅读英文正文。对 Builder API 使用下面的固定推导链：

```text
1. 先用一句业务动作定义目标
   -> 删除 documentId=12 的全部 ES chunk

2. 在与项目版本匹配的官方文档中找 Request example
   -> 先读 REST/JSON 请求，不先猜 Java Builder

3. 找 Java 标签中的调用外壳
   -> deleteByQuery -> index -> query

4. 按项目数据模型替换查询条件
   -> 官网 match_all 不可照搬
   -> 官网唯一属性示例的 term 可复用
   -> user.id 改为项目 Mapping 中真实存在的 documentId

5. 删除无业务作用的自动生成参数
   -> expandWildcards(List.of())、routing(List.of())、
      sort(List.of())、stats(List.of()) 都是空的可选配置

6. 查看 Response example
   -> deleted 决定 Java 方法需要读取和返回什么
```

Java Builder 的嵌套层级通常对应 JSON 的嵌套层级：

```text
"query"
  -> .query(...)

"term"
  -> .term(...)

"documentId": 12
  -> .field("documentId").value(documentId)
```

官网示例提供 API 结构，不负责替项目做业务决策。真正需要思考的是：哪些字段属于本项目、示例参数是否符合一篇文档对应多个 chunk 的数据模型、响应中的哪些结果会被调用方消费。

测试最初在 Codex 沙箱中运行时，进程在连接 Elasticsearch 前就因本机回环网络限制失败；随后在 VS Code 终端连接同一个本地 Elasticsearch 执行通过。这说明第一次失败属于执行环境限制，不是 BM25 业务逻辑错误。

**结论**

Elasticsearch BM25 已经能够从 `content` 的关键词匹配结果中读取稳定分块身份和原始分数，并转换成与 Redis 相同的 `RetrievalCandidate`。至此 Redis 语义召回和 Elasticsearch 关键词召回两条独立候选链路均已通过真实集成测试，下一步可以在统一 DTO 上实现数据同步与 RRF。

**面试表达**

> 我没有把 Elasticsearch 仅作为技术堆砌，而是让它补足向量检索对制度编号、型号和专有名词召回不稳定的问题。我为 chunk 建立显式 Mapping，用 documentId 和 chunkIndex 组成稳定 `_id`，通过 match 查询取得 BM25 `_score`，再转成与 Redis 共用的 RetrievalCandidate。两路原始分数量纲不同，所以只保留 rawScore 用于观测，后续根据各自 rank 做 RRF。该链路通过真实 Elasticsearch 集成测试验证：3 条数据写入后命中 2 条，分数降序、rank 和来源字段均符合预期。
