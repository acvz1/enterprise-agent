# 企业知识库 Agent：面试与 Demo 手册

## 1. 项目定位

- 目标岗位：Java 后端 / AI 应用后端实习
- 项目类型：已有 Spring Boot 知识库项目的二次开发
- 运行深度：本地完整跑通核心链路
- 核心问题：单一向量检索对专有名词和精确制度条款不稳定，检索结果缺少统一融合、权威数据补全、引用证据与 Agent 权限边界
- 当前状态：核心六天功能已实现并验证；生产级文档 ACL、死锁重试、Reranker 不在本轮范围

## 2. 45 秒项目介绍

> 我做的是一个 Java 企业知识库 Agent 二次开发项目。上游已经有文档 CRUD、文件解析、Redis 向量搜索和基础问答，但 Elasticsearch 只出现在配置里，没有真正进入检索链路，而且最终回答缺少稳定的 chunk 引用。我把 Redis 向量召回和 Elasticsearch BM25 都统一成 RetrievalCandidate，用 RRF 按排名融合，确定 Top K 后再通过一条 MySQL JOIN 查询补全权威原文，组装成 RetrievalHit。然后把这条混合检索封装成 LangChain4j 工具，让 Agent 自己判断是否需要查知识库，并在接口入口和工具结果返回前分别检查 qa:ask 和 document:read。最后用 15 个标注问题对比三种检索策略，并用 DOCX、PDF、TXT 真实上传验证回答与引用。

## 3. 两分钟讲解主线

### 背景

原项目的基础向量搜索能根据语义找文档，但存在三个问题：

1. 专有名词、制度编号、错误码等精确词不一定稳定命中。
2. Redis 和关键词结果没有统一候选模型，无法可靠融合和追踪来源。
3. 检索结果需要回到 MySQL 取得最新权威原文，否则索引数据可能过期。

### 核心改造

```text
Redis Vector -> RetrievalCandidate
Elasticsearch BM25 -> RetrievalCandidate
  -> documentId + chunkIndex 去重
  -> RRF 融合 rank
  -> Top K
  -> MySQL 一次 JOIN 补全
  -> RetrievalHit
  -> RAG / Agent + citations
```

### 工程取舍

- 不直接相加余弦相似度和 BM25 `_score`，因为量纲不同。
- 候选阶段不查 MySQL，只在 Top K 确定后批量补全，避免 N+1。
- MySQL 是 Source of Truth；Redis 和 Elasticsearch 都是可重建索引。
- 权限做了“进入 Agent 前”和“工具结果返回模型前”两道最小检查，但不夸大为完整文档 ACL。

### 结果

- Redis Hit@3：0.8000；Recall@3：0.7667。
- BM25 Hit@3 / Recall@3：1.0000 / 1.0000。
- RRF Hit@3 / Recall@3：0.9333 / 0.9333。
- MySQL Top K 补全实测只执行 1 条 SQL。
- 新上传 DOCX、PDF、TXT 的问答、双路来源和知识缺失拒答均验证成功。

## 4. 简历 4–5 行版本

**企业知识库 Agent｜Java / Spring Boot / LangChain4j / Redis / Elasticsearch / MySQL**

- 基于开源 Spring Boot 知识库系统进行二次开发，围绕企业制度与业务文档构建“混合检索—权威数据补全—Agent 工具调用—引用返回”的完整问答链路。
- 将 Redis 向量召回与 Elasticsearch BM25 统一为 `RetrievalCandidate`，基于 `documentId + chunkIndex` 去重并使用 RRF 融合异构排名，解决两路分数不可直接比较的问题。
- 在 RRF Top K 后通过单条 MySQL `JOIN FETCH` 批量补全文档标题与最新 chunk 原文，组装 `RetrievalHit`，实测补全阶段 SQL 查询次数为 1，并支持普通/SSE 问答返回引用证据。
- 基于 LangChain4j `AiServices` 封装 `searchKnowledgeBase` 工具，实现 Agent 按问题自主决定是否检索，并以 `qa:ask`、`document:read` 完成调用前后最小权限校验。
- 构建 15 问固定评测集：Redis、BM25、RRF 的 Hit@3 分别为 0.80、1.00、0.93；完成 DOCX/PDF/TXT 实际上传与双路引用 Demo，并定位批量异步入库的 MySQL 死锁边界。

如果简历空间只够四行，删除最后一行中的“并定位……”后半句，保留指标。

## 5. Ownership：哪些是上游，哪些是自己的改造

| 上游已有能力 | 本轮重点改造 |
|---|---|
| 用户登录、JWT 基础设施 | GUEST 默认无业务权限；Agent 入口和工具结果双层权限演示 |
| 文档 CRUD、分类、版本 | 修复并验证稳定文件存储后的异步处理链路 |
| Apache Tika 文件解析 | 用 DOCX/PDF/TXT 完成真实上传与解析验证 |
| Redis 基础向量搜索 | chunk metadata、统一候选、纯候选阶段不查 MySQL |
| 基础 RAG 和 SSE | RRF Top K、单 SQL 补全、普通/SSE citations |
| 原有 Vue 页面 | 重构成可演示的知识中枢、检索实验室和评测看板 |
| Elasticsearch 配置痕迹 | 真正创建 Mapping、同步 chunk、BM25 检索并进入融合链路 |
| 无完整 Agent 工具链 | LangChain4j Agent 自主选择 `searchKnowledgeBase` |

不能说“从零独立完成整套系统”。正确说法是“基于开源项目完成核心检索与 Agent 链路的二次开发，并能说明上游与个人贡献”。

## 6. 五分钟 Demo 脚本

### 启动前

```powershell
docker compose -f docker/docker-compose.yml up -d mysql redis elasticsearch
.\mvnw.cmd spring-boot:run

Set-Location ai-assistant-front
npm.cmd run dev -- --host 127.0.0.1
```

访问 `http://localhost:5173`，使用 `admin / admin123` 登录。

### 演示顺序

1. **知识库页面（40 秒）**
   展示员工手册 DOCX、差旅制度 PDF、故障流程 TXT，说明 MySQL 是权威数据，Redis/ES 是检索索引。

2. **精确制度问题（60 秒）**
   提问：“公司每周哪两天允许申请远程办公，最晚什么时候提交？”
   展示回答“周二、周四、前一个工作日 18:00 前”。

3. **引用与双路来源（60 秒）**
   展开引用，指出 `documentId`、`chunkIndex`、原文、`fusionScore`，以及同一 chunk 的 `REDIS_VECTOR + ELASTICSEARCH_BM25`。

4. **知识缺失拒答（40 秒）**
   提问：“公司的股票期权分几年归属？”
   展示 Agent 明确说知识库无资料，而不是编造数字。

5. **评测看板（60 秒）**
   展示 15 问结果，主动说明当前语料 BM25 最好，RRF 改善了向量单路但延迟更高。

6. **边界与下一步（40 秒）**
   说明并发上传出现过死锁，复合问题可能重复调用工具；下一步是死锁重试、并行双路召回、去重 citations 和文档级 ACL。

## 7. 核心代码讲解顺序

1. `AgentController.ask()`：接收 `question/model`，`@PreAuthorize` 检查 `qa:ask`。
2. `KnowledgeAgentService.ask()`：按 model 创建 `ChatLanguageModel`，用 `AiServices` 注册工具并执行 Agent。
3. `KnowledgeBaseTool.searchKnowledgeBase()`：调用混合检索，并在结果交给模型前检查 `document:read`。
4. `HybridRetrievalService.searchHits()`：Redis、ES、RRF、MySQL 补全的总编排。
5. `RrfFusionService.fuse()`：复合键去重、`1/(60+rank)` 累加、Top K。
6. `RetrievalResultService.assembleHits()`：一条 JOIN 查询、Map 精确过滤、保持 RRF 顺序。
7. `DocumentProcessingWorker.processFileAsync()`：解析、保存、分块、两路索引和进度状态。
8. `RetrievalEvaluationIT`：固定语料、标准相关结果、三种策略和指标计算。

## 8. 高频面试追问

### Q1：为什么已经有 Redis 向量检索，还要 Elasticsearch？

向量检索擅长语义近似，但制度名、产品名、错误码、P1 等精确关键词可能不稳定。Elasticsearch 的 BM25 提供词频、逆文档频率和文档长度归一化后的关键词排序。两路解决的是不同类型的相关性，不是重复堆技术。

### Q2：为什么不把余弦相似度和 BM25 `_score` 乘权重后直接相加？

两个分数的范围和分布不同，`0.8` 的余弦相似度与 `8.0` 的 BM25 分数没有直接可比性。RRF 只使用各自排名，避免手工归一化，同时能让同一 chunk 的双路命中累加贡献。

### Q3：`rank`、`rawScore` 和 `fusionScore` 分别是什么？

- `rawScore`：某一路检索自己的原始相关性分数。
- `rank`：候选在该路结果中的名次，从 1 开始。
- `fusionScore`：RRF 根据各路 rank 累加出的统一排序分数。

### Q4：为什么候选结果不直接给前端？

`RetrievalCandidate` 只有内部排序需要的 ID、排名和来源，索引中的正文可能不是权威最新版本。Top K 后要回 MySQL 取得 chunk 主键、标题和最新原文，再组装 `RetrievalHit` 给 RAG、Agent 和前端。

### Q5：批量查询为什么还要在 Java 中再次精确匹配？

SQL 用 `documentId IN (...) AND chunkIndex IN (...)` 会产生两个集合的交叉组合。Java 必须用 `documentId + chunkIndex` 复合键过滤，只保留 RRF 真正选中的候选，并按融合顺序组装。

### Q6：这个 Agent 与普通 RAG 有什么区别？

普通 RAG 的代码固定每次“先检索再调模型”；Agent 把 `searchKnowledgeBase` 注册为工具，模型可根据问题决定是否调用。实测知识库问题 `toolUsed=true`，普通问候 `toolUsed=false`。

### Q7：权限为什么检查两次？

`qa:ask` 在 Controller 前拦截没有提问权限的用户，避免不必要的模型调用；`document:read` 在工具结果返回模型前检查，避免有提问权限但无文档读取权限的用户看到检索原文。当前是全局权限，不是文档级 ACL。

### Q8：为什么评测中 BM25 反而比 RRF 好？

固定语料较小，问题包含大量制度名和精确词，BM25 天然占优；向量召回的弱排序进入 RRF 后会拉低部分结果。真实结论是 RRF 比向量单路稳健，但没有超过当前最强单路，后续需要更好的中文 Embedding、阈值、Reranker 或自适应路由。

### Q9：为什么混合检索延迟最高？

当前 `HybridRetrievalService` 依次调用 Redis 和 Elasticsearch，两路耗时相加，之后还有 RRF 和 MySQL 补全。可用 `CompletableFuture` 并行召回，并设置总超时和单路降级，但必须先保证 SecurityContext 和异常传播正确。

### Q10：文档修改时为什么要先删除旧 chunk？

修改后的正文可能产生不同数量和边界的 chunk。只覆盖当前 chunk 会留下已经不存在的旧切片，导致用户检索到过期内容。因此按 `documentId` 删除旧索引，再根据当前正文完整重建。

### Q11：这次批量上传的死锁说明什么？

三个异步任务并发写 `document_chunks` 时，一个任务在 80% 阶段遇到 MySQL 死锁。说明“接口返回 202”不等于后台任务可靠。当前用单文档 vectorize 恢复；生产方案要统一事务加锁顺序、控制并发并对可重试死锁做有限次数退避重试。

### Q12：哪些内容不能写进简历？

不能写逐文档 ACL、Reranker、MCP、多 Agent、生产级失败恢复，因为没有实现和验证。也不能说整套系统从零开发，必须明确是在开源底座上完成核心检索与 Agent 链路的二次开发。

### Q13：为什么 AI 应用后端仍然选择 Java？

本项目的主要难点不是训练模型，而是鉴权、事务、异步任务、三套存储的一致性、工具输入输出和可观测性。Java 21 与 Spring Boot 可以直接复用 Security、JPA、Validation、Actuator 和 Testcontainers，也更匹配 Java 后端岗位。若以后出现 Python 独占的重排模型或文档算法，可以通过 HTTP 或消息队列拆成辅助服务，不需要把整个企业后端改成 Python。

## 9. 当前限制与下一步

优先级从高到低：

1. 为异步文档任务增加死锁重试与并发限制。
2. 对重复工具调用产生的 citations 按 `documentId + chunkIndex` 去重。
3. Redis 与 Elasticsearch 并行召回，并设计超时/单路降级。
4. 基于租户、部门、文档密级实现检索前过滤和检索后复核。
5. 使用 Reranker 或动态 Top K 降低弱相关引用。

这些是后续方案，不属于本轮已经完成的简历成果。
