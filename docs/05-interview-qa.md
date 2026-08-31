# 企业知识库 Agent：面试问答记录

> 用途：这是一份“最小 MVP 面试主稿”。先能用自己的话讲清五题，再按面试追问扩展；不把未验证的能力写成项目成果。

---

## Q1：项目是干什么的？（30 秒）

这是一个面向企业内部文档的知识库问答系统。用户提问后，系统先从企业文档中检索相关分块，再把证据和问题一起交给大模型生成答案，并返回对应引用，避免模型只依赖通用知识回答企业制度和业务问题。

我在原有知识库问答的基础上，增加了 Redis 向量检索和 Elasticsearch BM25 的混合检索、RRF 排名融合、MySQL 权威原文补全，以及 Agent 工具调用和基础权限校验。

**代码依据**：`AiService.askQuestion()`、`HybridRetrievalService.searchHits()`、`KnowledgeAgentService.ask()`。

---

## Q2：一次请求完整怎么跑？（最重要，30～45 秒）

用户登录后携带 JWT 请求 `POST /api/ai/ask`。JWT 过滤器先解析令牌并把用户权限放入 `SecurityContextHolder`；`AiController` 将问题交给 `AiService`。

`AiService` 调用混合检索：Redis 做语义向量召回，Elasticsearch 做 BM25 关键词召回；两路结果统一成候选分块，按 `documentId + chunkIndex` 去重，用 RRF 融合排序，选出 Top K。随后只对 Top K 回 MySQL 一次批量查询，拿到最新标题和分块正文，组装成 `RetrievalHit`。最后把这些证据拼进 Prompt，调用 LLM，并把答案和引用一起返回。`/api/ai/ask-stream` 复用这条检索链，只是把答案以 SSE 分段推送。

```text
JWT 请求
→ JwtAuthenticationFilter
→ AiController
→ AiService
→ Redis 向量候选 + ES BM25 候选
→ RRF Top K
→ MySQL 批量补全
→ Prompt + LLM
→ answer + citations（或 SSE message + metadata）
```

**代码依据**：

- `JwtAuthenticationFilter.doFilterInternal()`
- `AiController.ask()` / `askStream()`
- `AiService.askQuestion()` / `askQuestionStream()`
- `HybridRetrievalService.search()` / `searchHits()`
- `RrfFusionService.fuse()`
- `RetrievalResultService.assembleHits()`

---

## Q3：你到底做了什么？

原项目已有基础的文档管理、Redis 向量检索、普通 RAG 问答和 SSE 接口。我二次开发的核心是把“单路向量检索后直接回答”改造成可追溯的混合检索链路：

1. 新增 Elasticsearch 分块索引和 BM25 检索；
2. 将 Redis、ES 的结果统一为 `RetrievalCandidate`，以 `documentId + chunkIndex` 标识同一分块；
3. 新增 RRF 融合，解决向量相似度和 BM25 原始分数不能直接相加的问题；
4. 在 Top K 确定后，通过 MySQL `JOIN FETCH` 批量补齐权威标题和正文，生成带来源、分数和引用信息的 `RetrievalHit`；
5. 将该检索能力封装成 LangChain4j 工具，新增 Agent 问答入口及 `qa:ask`、`document:read` 的基础校验；
6. 改造普通 RAG/SSE 返回引用，并补充了检索评测与关键服务测试。

**注意**：面试时要明确说“原项目已有基础 RAG 与 Redis 向量检索；混合检索、RRF、权威数据补全、Agent 工具链和引用改造是我本次二开完成的”。

---

## Q4：最难的一个问题是什么？

最难的是：**为什么检索命中后还要回 MySQL 补全，而不直接把 Redis 或 Elasticsearch 里的内容交给模型？**

Redis 和 Elasticsearch 在这里定位为可重建的检索索引，负责快速找候选；MySQL 才是文档标题和正文的权威来源。并且先融合选出少量 Top K，再一次批量查询 MySQL，既避免候选阶段逐条查库造成 N+1 查询，也能保证最终给模型和前端的引用来自当前权威数据。

对应实现：`RetrievalResultService.assembleHits()` 调用 `DocumentChunkRepository.findCandidateChunksWithDocument()`，最终生成 `RetrievalHit`。

---

## Q5：这个项目有什么不足？（准备两条）

1. **权限仍不是文档级 ACL，且公开注册没有管理员审批流程。** 当前已补齐 Agent 工具的检索前 `document:read` 检查、普通 RAG 的 `qa:ask + document:read` 入口校验，以及分类、标签、版本等业务接口的权限；公开注册只创建无业务权限的 GUEST。后续仍需基于部门、租户或文档密级把可见范围下推到 Redis/ES 过滤条件，并增加管理员授予 USER 的流程。

2. **检索仍有可优化空间。** Redis 与 Elasticsearch 现在串行执行，Top K、向量阈值为固定参数，也没有 Reranker。后续可改为并行召回，依据评测集调参，并在融合后增加重排以提升精度。

---

## 当前练习状态

- [ ] 能在 30 秒内独立回答 Q1。
- [ ] 能不看稿讲出 Q2 的八步主链路。
- [ ] 能明确区分原项目能力和二开内容。
- [ ] 能回答 Q4，并能说出 Q5 的两条不足。

---

## 给自己理解的版本：我实际上把什么做出来了？

这一节不用于直接背给面试官。目标是让自己能从“用户问一个问题”重新想起每一层为什么存在。

### 1. 原项目给了我什么起点？

原项目已经有文档管理、文档分块、Redis 向量检索、普通 RAG 问答和 SSE。也就是说，它已经能做到“把相似文档找出来，再让大模型回答”。

但它的检索主要依赖单路向量相似度。用户问的是企业制度、系统名、错误码、专有名词时，纯语义相似并不总是可靠；而且检索结果最终需要同时满足两件事：给模型正文证据，也给前端一条可定位的引用。

### 2. 我做的第一件事：让一份文档能被两种方式找到

文档正文会被切成多个 chunk（分块）。我让每个分块不仅进入 Redis 的向量索引，也进入 Elasticsearch 的文本索引。

```text
同一份文档正文
→ 分块
→ Redis：把分块变成向量，适合按“意思相近”查
→ Elasticsearch：保存分块文本，适合按“关键词命中”查
```

这里的关键不是把同一份数据存两遍，而是让同一个 chunk 同时拥有两个检索入口。更新文档时，旧 chunk 的 ES 索引需要先删除，再按当前正文重新建立；否则搜索可能拿到已被修改的旧内容。对应入口是 `DocumentChunkService` 调用 `ElasticsearchSearchService` 的索引同步逻辑。

### 3. 我做的第二件事：把两路搜索结果变成同一种“候选”

用户的问题进来后，Redis 与 ES 各自先返回候选。它们在这一阶段不需要带完整正文，只要能回答三件事：**这是哪个文档的第几块、它排第几、来自哪一路。**

所以我把它们统一为 `RetrievalCandidate`：

```text
documentId + chunkIndex + rank + source
```

- Redis 给出“语义相近”的候选；
- ES BM25 给出“关键词匹配”的候选；
- `documentId + chunkIndex` 是同一 chunk 的身份，不是只用 documentId。因为一份文档会有许多分块。

对应代码：`VectorSearchService.searchVectorCandidates()` 与 `ElasticsearchSearchService.searchBm25Candidates()`。

### 4. 我做的第三件事：决定到底信哪些候选

Redis 的向量相似度和 ES 的 BM25 `_score` 不是同一种分数，不能直接相加。因此我没有比较它们的原始分数，而是只看它们在各自列表中的名次。

`RrfFusionService.fuse()` 做了三件事：

1. 用 `documentId + chunkIndex` 把两路命中的同一 chunk 合并；
2. 对每一路名次累加 `1 / (60 + rank)`；
3. 按融合分数排序，只留下 Top K。

直觉上：一个分块若在两条检索路里都排得靠前，它比“只在某一路偶然排前面”的分块更值得进入下一步。

### 5. 我做的第四件事：候选胜出后，再取真正要给模型的证据

RRF 的结果仍只是“哪个 chunk 值得用”的候选身份和排名信息，还不是最终回答材料。

所以 `RetrievalResultService.assembleHits()` 收集 Top K 的文档 ID 和分块序号，用一次 MySQL `JOIN FETCH` 批量取回文档标题与 chunk 正文，再组装成 `RetrievalHit`。

```text
RetrievalCandidate：候选身份、来源、排名/融合分数
→ RRF Top K
→ MySQL
→ RetrievalHit：标题、正文、chunkId、融合分数、来源
```

这样设计是因为 MySQL 是权威数据源；Redis、ES 都是可重建索引。并且只在 Top K 后批量查询，避免“每个候选查一次库”的 N+1 问题。

### 6. 我做的第五件事：把证据真正用于回答，并让用户看见证据

`AiService.askQuestion()` 不是把用户问题直接发给模型，而是先调用 `HybridRetrievalService.searchHits()` 拿到 `RetrievalHit`。然后将标题和正文拼为 Prompt 上下文，并要求模型只根据这些内容回答；最终 JSON 同时返回 `answer` 与 `citations`。

流式接口 `askQuestionStream()` 复用同样的检索结果：答案按 SSE `message` 事件逐段发送，引用通过最后的 `metadata` 事件发送。因此用户既能先看到生成中的内容，也能知道答案引用了哪一段企业文档。

### 7. Agent 是在这条链路上额外加的一种入口

Agent 不是另一套检索系统。`KnowledgeAgentService` 用 LangChain4j 注册 `KnowledgeBaseTool.searchKnowledgeBase()`；模型在 `agent.chat(question)` 过程中自行决定是否调用这个工具。

一旦调用，工具内部仍然走同一个 `hybridRetrievalService.searchHits()`。不同点是：Agent 入口有 `qa:ask` 权限，工具在进入混合检索前检查 `document:read`；通过后才查询并从工具执行结果恢复引用，随答案返回。

所以可以把 Agent 理解为：**普通 RAG 是程序固定“先检索再回答”；Agent 是模型按问题决定要不要调用同一套检索能力。**

### 8. 一句话记忆

我不是从零做了一个聊天机器人，而是把原项目的单路向量 RAG 改造成了一条可追溯的混合检索链：**两路召回找候选，RRF 决定优先级，MySQL 提供权威证据，LLM 基于证据回答，前端拿到引用。**

---

## Q6：改为从 MySQL 查询 chunk 后，会不会拿到未及时更新的旧分块？批量补全是否会多查数据？

### 面试时的直接回答

原实现会出现旧分块：普通更新接口没有触发重新分块，Redis 旧向量也不能按文档删除；新建文档和版本回滚也没有统一进入检索同步链路。现已修复为正文变化、新建文档和版本回滚都调用 `processDocument()`，并额外用 Redis Set 登记每个文档写入的 embedding ID；重建前据此精确删除该文档旧向量，再重建 MySQL chunk 和 Elasticsearch 索引。同步失败不再被同步保存接口吞掉。当前 MySQL 批量补全仍使用两个独立 `IN` 条件，可能读出并非 Top K 请求的交叉组合；Java 最后按 `documentId + chunkIndex` 再过滤，因此结果通常正确，但仍存在额外查询和对象创建开销。

### 对应代码

- `DocumentService.updateDocumentWithVersion()` 在 `contentChanged` 时调用 `DocumentChunkService.processDocument()`；只修改标题不重新计算 Embedding。
- `RedisVectorIndexService.registerEmbedding()` 按文档登记 LangChain4j 返回的 embedding ID，`deleteByDocumentId()` 删除对应的 `embedding:*` key。
- `DocumentChunkService.processDocument()` 先删除 Redis 旧向量和 MySQL 旧分块，再写入当前分块，最后删除并重建 Elasticsearch 分块。
- `DocumentChunkRepository.findCandidateChunksWithDocument()` 使用 `documentId IN (...) AND chunkIndex IN (...)`。
- `RetrievalResultService.assembleHits()` 再用 `documentId + "_" + chunkIndex` 精确匹配候选。

### 实际数据流

```text
更新 documents.content
  -> 判断 contentChanged
  -> Redis 按登记的 embedding ID 删除该文档旧向量
  -> MySQL 删除旧 chunk，并根据当前正文重新分块写入
  -> Redis 写入新向量并登记 embedding ID
  -> Elasticsearch 删除旧分块后写入新分块

RRF Top K 精确候选对
  -> 分别收集 documentIds 与 chunkIndexes
  -> 一条 JOIN FETCH 查询可能返回交叉组合
  -> Java Map 按 documentId + chunkIndex 精确取回真正的 Top K
  -> RetrievalHit
```

例如真正需要 `(doc1, 0)` 和 `(doc2, 3)`，当前 SQL 条件也会匹配数据库中存在的 `(doc1, 3)` 与 `(doc2, 0)`。后两条不会进入最终 `RetrievalHit`，但已经产生了数据库读取和实体实例化开销。

### 为什么这样设计

这次改造的主要目标是先消除逐候选回表造成的 N+1 查询，所以先采用了实现简单的一次批量 `JOIN FETCH`。在当前默认 Top K 较小的 MVP 中，一次查询带来的少量冗余通常比执行多条 SQL 更可控，同时还能一起取回所属 `Document` 的标题。

### 替代方案

1. 使用 MySQL 行值条件：`(document_id, chunk_index) IN ((?, ?), (?, ?))`，精确查询每个候选对；可用原生 SQL、`JdbcTemplate` 或动态查询构造。
2. 在 Redis、Elasticsearch 元数据中保存稳定 `chunkId`，RRF 后直接 `WHERE id IN (...)`；但当前重建会删除再插入 chunk，数据库 ID 会变化，需要配合版本化或稳定 ID 方案。
3. 保存并索引独立的 `chunkKey`，例如 `documentId_chunkIndex`，再用 `chunkKey IN (...)` 查询。
4. 对数据同步增加 `documentVersion`、索引状态和 Outbox/异步任务；新版本的 MySQL chunk、Redis、ES 全部构建成功后再切换为可检索版本。

### 当前实现的不足与验证边界

- Redis embedding ID 注册表只能追踪修复后新写入的向量；已有旧向量需要在部署修复后执行一次全量向量索引重建完成迁移。
- `@Transactional` 只能覆盖 MySQL，不能让 Redis、Elasticsearch 一起原子回滚；生产级方案仍需要索引状态、版本切换和失败重试/补偿。
- `document_chunks` 实体目前没有声明 `(document_id, chunk_index)` 的联合唯一约束或联合索引。
- 两个独立 `IN` 条件最多可能把 K 个精确候选放大为接近 K² 个组合；当前 Top K 小时影响有限，K 增大后需要优化。
- 当前全量单元测试 45 个通过，覆盖权限边界、账号会话隔离、正文/新建/版本回滚触发重建、Redis 向量登记/删除，以及版本审计人必须取自 JWT 用户。2026-08-09 已完成真实 Redis、Elasticsearch、MySQL 集成验收：ES BM25、Redis 向量、RRF 融合和 15 问评测共 5 个测试通过；Testcontainers MySQL 验证 Top K 补全为 1 条 SQL；有效 GUEST JWT 的文档读取和普通 RAG 提问均返回 HTTP 403。前端 `npm run build` 已通过类型检查与 Vite 打包；当前主包约 918 kB，后续可按路由或组件拆包。

### 面试官可能继续追问

- 为什么 MySQL 是权威数据源，仍然会出现旧数据？
- 数据库事务能不能同时保证 Redis 和 Elasticsearch 一致？
- 文档更新过程中，旧索引和新索引应该如何切换？
- 为什么不直接用 `chunkId`？重建后 `chunkId` 变化怎么办？
- 联合索引只能提速，为什么不能消除交叉组合？
