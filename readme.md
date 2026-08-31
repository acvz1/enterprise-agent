# Enterprise Knowledge Agent

基于 Java 21、Spring Boot、LangChain4j、Redis Stack、Elasticsearch 和 MySQL 的企业知识库 Agent 二次开发项目。

项目不是只把文档塞给大模型：它用 Redis 做语义召回、Elasticsearch 做 BM25 关键词召回，通过 RRF 融合排名，再从 MySQL 批量取得权威原文。Agent 根据问题决定是否调用知识库工具，并返回可追溯到文档分块的引用证据。

## 二次开发说明

本项目基于 [2518350LJL/ai-knowledge-base](https://github.com/2518350LJL/ai-knowledge-base) 进行二次开发，原项目采用 Apache License 2.0。

在保留原有文档管理、JWT/RBAC、文件上传与基础向量问答能力的基础上，本项目新增或改造了 Elasticsearch BM25 检索、Redis + ES 双路召回、RRF 融合、MySQL 权威数据补全、引用返回、LangChain4j Agent Tool、检索评测、部门数据范围与低相关证据拦截等能力。

## 核心能力

- 支持 PDF、Word、TXT、Markdown 等企业文档解析、分块和异步入库。
- Redis Stack 根据 Embedding 召回语义相近的 chunk。
- Elasticsearch 使用 BM25 召回关键词匹配的 chunk。
- 两路结果统一为 `RetrievalCandidate`，使用 RRF 按排名融合并去重。
- RRF Top K 确定后，通过一条 MySQL JOIN 查询补全文档标题和最新原文，组装 `RetrievalHit`。
- RAG 普通问答与 SSE 流式问答都能返回引用证据。
- LangChain4j Agent 可自主决定是否调用 `searchKnowledgeBase`。
- 使用 JWT、`qa:ask` 和 `document:read` 完成最小权限演示。
- 提供固定评测集，对比 Redis、Elasticsearch 和 RRF 的 Hit@3、Recall@3 与延迟。
- Vue 3 前端包含智能问答、知识库、检索实验室和评测看板。

## 架构

```mermaid
flowchart LR
    U["用户 / Vue 3"] -->|"JWT + 问题"| A["AgentController"]
    A --> P["qa:ask 权限检查"]
    P --> S["KnowledgeAgentService"]
    S --> L["LLM 决定是否调用工具"]
    L -->|需要企业知识| T["KnowledgeBaseTool"]
    L -->|普通问候| R["AgentResponse"]

    T --> D["document:read 权限检查"]
    D --> H["HybridRetrievalService"]
    H --> V["Redis Vector Search"]
    H --> E["Elasticsearch BM25"]
    V --> C1["RetrievalCandidate"]
    E --> C2["RetrievalCandidate"]
    C1 --> F["RRF Fusion"]
    C2 --> F
    F --> K["Top K 候选"]
    K --> M["MySQL 一次批量 JOIN\n补全权威 chunk + title"]
    M --> X["RetrievalHit / 引用证据"]
    X --> L
    L --> R
```

数据职责：

- **MySQL**：用户、文档、文档分块等权威业务数据。
- **Redis Stack**：可重建的向量索引、会话记忆和短期缓存。
- **Elasticsearch**：可重建的 BM25 关键词索引。
- **LLM**：基于检索证据生成回答，不作为企业事实的数据源。

## 检索评测

固定 4 篇文档、8 个 chunk、15 个标注问题的本地实测：

| 策略 | Hit@3 | Recall@3 | 平均延迟 | P95 延迟 |
|---|---:|---:|---:|---:|
| Redis 向量检索 | 0.8000 | 0.7667 | 116.059 ms | 524.068 ms |
| Elasticsearch BM25 | 1.0000 | 1.0000 | 52.373 ms | 70.848 ms |
| RRF 混合检索 | 0.9333 | 0.9333 | 214.430 ms | 348.513 ms |

这组小规模中文制度语料中 BM25 最好；RRF 相比单独向量检索提高了命中与召回，但没有超过 BM25，且当前两路串行执行导致延迟更高。项目不使用“混合检索一定更强”的虚假结论。

## 五分钟 Demo

演示语料位于 [`demo-data/knowledge-base`](demo-data/knowledge-base)，包含 DOCX、PDF、TXT 三种格式。

推荐演示顺序：

1. 使用 `admin / admin123` 登录，展示无 JWT 无法访问业务接口。
2. 在知识库页面查看三种格式的“星桥科技”演示文档。
3. 提问“公司每周哪两天允许申请远程办公，最晚什么时候提交？”。
4. 展示答案、具体 chunk 引用，以及 `REDIS_VECTOR + ELASTICSEARCH_BM25` 双路来源。
5. 提问“公司的股票期权分几年归属？”，展示知识缺失时拒绝编造。
6. 打开检索评测结果，解释为什么当前语料上 BM25 优于向量检索。

## 本地启动

要求：JDK 21、Docker Desktop、Node.js 22+。Hibernate 增强插件与当前依赖组合不支持使用 JDK 25 构建。

```powershell
# 1. 启动 MySQL、Redis Stack、Elasticsearch
docker compose -f docker/docker-compose.yml up -d mysql redis elasticsearch

# 2. 配置模型密钥
Copy-Item .env.template .env
# 至少填写默认模型所需的 DEEPSEEK_API_KEY

# 3. 后端
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run

# 4. 前端（新终端）
cd ai-assistant-front
npm.cmd install
npm.cmd run dev -- --host 127.0.0.1
```

访问：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080`
- Elasticsearch：`http://localhost:9200`
- RedisInsight：`http://localhost:8888`

## 关键代码

| 责任 | 入口 |
|---|---|
| Agent HTTP 接口 | `AgentController.ask()` |
| Agent 创建与工具结果提取 | `KnowledgeAgentService.ask()` |
| 知识库工具与读取权限 | `KnowledgeBaseTool.searchKnowledgeBase()` |
| 双路检索编排 | `HybridRetrievalService.searchHits()` |
| RRF 去重和融合 | `RrfFusionService.fuse()` |
| MySQL 批量补全 | `RetrievalResultService.assembleHits()` |
| 文档解析 | `FileParseService.parseFile()` |
| 异步入库 Worker | `DocumentProcessingWorker.processFileAsync()` |
