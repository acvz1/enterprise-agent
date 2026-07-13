# 架构与调用链

## 1. 系统边界

系统当前有四类参与者：浏览器前端、Spring Boot 应用、MySQL/Redis 基础设施、外部大模型服务。

```text
Vue 3
  | REST / SSE + JWT
  v
Spring Boot
  |-- Security：身份认证与接口权限
  |-- Document：文档领域与入库
  |-- Retrieval：向量/关键词召回与排序
  |-- AI：上下文拼接与模型调用
  |-- Observability：Actuator/Micrometer
  |
  +--> MySQL：业务事实、文档、chunk、用户权限
  +--> Redis Stack：向量索引、会话记忆、回答缓存
  +--> LLM API：答案生成
```

第一性原理上，MySQL 保存可审计的业务事实；Redis 保存为了检索和性能构建的派生状态。派生状态丢失后应能由 MySQL 重建，这也是后续一致性设计的依据。

## 2. 当前模块职责

| 层 | 输入 | 输出 | 代表模块 |
|---|---|---|---|
| Controller | HTTP 参数、JWT 后的认证上下文 | HTTP JSON/SSE | `AiController`、`FileUploadController` |
| Application Service | 用例参数 | 用例结果 | `AiService`、`DocumentService` |
| Retrieval | query、topK、权重 | 有序文档列表 | `VectorSearchService` |
| Ingestion | 文件或文档 | 文档、chunk、向量 | `FileParseService`、`DocumentChunkService` |
| Repository | 实体查询条件 | 实体/分页 | Spring Data JPA repositories |
| Infrastructure | 配置 | 模型、Redis、线程执行器 | `ModelFactory`、config 包 |

当前包结构主要按技术层划分，适合先学习 Spring 请求链。等 Agent 模块出现后，只给 Agent 新模块建立清晰边界，不急着把全仓库重构成 DDD。

## 3. 关键调用链

### 3.1 普通问答

```text
POST /api/ai/ask
 -> JwtAuthenticationFilter
 -> AiController.ask
 -> AiService.askQuestion
 -> VectorSearchService.searchDocuments
    -> EmbeddingModel.embed(query)
    -> RedisEmbeddingStore.search
    -> MySQL keyword fallback / RRF
 -> ChatMemoryStore（读取最近对话）
 -> ModelFactory.createModel
 -> LLM.generate
 -> Redis answer cache
 -> JSON response
```

主要缺口：检索返回的是 `Document` 而不是携带 score、chunkId、citation、ACL 信息的 `SearchHit`，因此后续 ContextBuilder 无法做精细选择。

### 3.2 流式问答

```text
POST /api/ai/ask-stream
 -> AiController 创建 SseEmitter
 -> executor 执行 AiService.askQuestionStream
 -> 检索 + 历史 + prompt
 -> streaming model callback
 -> emitter.send(event)
 -> complete / completeWithError
```

后续要把“文本 token 流”升级为“Agent 事件流”：`run.started`、`llm.delta`、`tool.started`、`tool.finished`、`run.completed`、`run.failed`。

### 3.3 文档入库

```text
MultipartFile
 -> FileUploadController
 -> FileParseService：抽取文本
 -> DocumentService：写 MySQL 文档
 -> DocumentChunkService：切分 chunk
 -> EmbeddingModel：生成向量
 -> RedisEmbeddingStore：写向量索引
```

异步接口当前不是可靠任务系统。正确方向是先把上传文件持久化到受控临时区/对象存储，再提交只含 `jobId` 和稳定文件地址的任务；任务状态必须持久化，并支持幂等、重试、失败原因和恢复。

## 4. 核心数据结构

当前必须先理解：

- `Document`：业务文档聚合根，MySQL 中的事实来源。
- `DocumentChunk`：检索粒度，必须稳定关联 `documentId/chunkId`。
- `SessionMessage`：对话历史候选，不等于最终送入 LLM 的上下文。
- `UploadProgress`：已有进度表，但还不是可靠任务状态机。

计划新增并由学习者手写：

- `SearchHit`：`documentId`、`chunkId`、`content`、`score`、`source`、`citation`、`permissions`。
- `ContextPacket`：统一表示 memory、retrieval、tool result 等候选信息。
- `AgentRunState`：runId、轮次、消息、待执行工具、预算、状态、错误。
- `ToolSpec<TInput, TOutput>` 与 `ToolResult<T>`：强类型工具边界。
- `AgentEvent`：SSE 与持久化共用的运行事件。

## 5. 目标 Agent 调用链

```text
请求
 -> AgentOrchestrator 创建 AgentRunState
 -> ContextBuilder.gather/select/structure/compress
 -> AgentLoop 调 LLM
 -> 若 finish：保存结果并结束
 -> 若 tool_calls：ToolRegistry 校验名称和参数
 -> ToolExecutionPolicy 做权限、超时、重试、幂等检查
 -> 执行 KnowledgeSearchTool / DocumentTool / MCPTool
 -> ToolResult 追加到 state
 -> 进入下一轮，直到完成、失败或达到预算
```

Agent Loop 是控制流；ContextBuilder 是每次模型调用前的信息编辑器；Tool Registry 是能力目录和调度入口。三者职责不能合并进一个巨型 `AgentService`。
