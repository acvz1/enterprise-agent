# 企业知识库 Agent：六天核心开发计划

> 本文件是当前唯一有效的开发排期和进度判断基线。后续判断“今天是否完成”“总进度多少”“下一步做什么”时，必须先以本文件为准，不再使用 `03-secondary-development-roadmap.md` 中 25～38 天的完整生产级路线估算。

## 一、六天计划

| 天数 | 核心任务 | 当天验收成果 |
| --- | --- | --- |
| 今天 | Redis chunk 检索 + Elasticsearch BM25 | Redis 与 Elasticsearch 都能独立返回统一的 `RetrievalCandidate` |
| 第 2 天 | 数据同步 + RRF 混合检索 | 文档分块可同步到 Redis/Elasticsearch；同一 chunk 可识别、去重并通过 RRF 融合 |
| 第 3 天 | 接入 RAG，返回引用证据 | RRF Top K 批量查询 MySQL；模型使用 chunk 证据回答；接口返回标题、分块和引用来源 |
| 第 4 天 | Agent 工具调用和权限过滤 | Agent 能选择知识库检索工具；检索前后执行最小可演示的权限校验 |
| 第 5 天 | 测试、检索评估、性能指标 | 对比 Redis、Elasticsearch、混合检索；记录 Hit@K/Recall@K、延迟和 SQL 查询次数 |
| 第 6 天 | Demo、简历描述和面试追问 | Demo 可稳定演示；完成架构图、README、STAR 简历描述和面试追问材料 |

## 二、今天的准确范围

今天只完成两路检索的独立候选召回，不提前混入第 2 天的数据同步和 RRF。

### Redis chunk 检索

完成标准：

```text
TextSegment 写入 documentId + chunkIndex
  -> RedisEmbeddingStore 声明 metadataKeys
  -> Redis 向量搜索返回 EmbeddingMatch<TextSegment>
  -> 根据列表位置生成从 1 开始的 rank
  -> 转换成 source = REDIS_VECTOR 的 RetrievalCandidate
  -> 候选阶段不查询 MySQL
```

当前状态：已完成。代码编译通过，已全量重建索引；Redis schema 实际包含 `documentId`、`chunkIndex`，真实集成测试返回 3 个候选，1 条测试通过、0 失败。

### Elasticsearch BM25

完成标准：

```text
Elasticsearch 容器可用
  -> Java 客户端能够连接
  -> 建立最小 chunk 索引 Mapping
  -> 写入少量测试 chunk
  -> BM25 查询得到有序结果
  -> 根据结果位置生成 rank
  -> 转换成 source = ELASTICSEARCH_BM25 的 RetrievalCandidate
```

当前状态：已完成。Elasticsearch 8.10.4 容器健康且集群为 `green`，Spring Java Client 连接状态为 `UP`；`document-chunks` 索引实际 Mapping 为 `documentId=long`、`chunkIndex=integer`、`content=text`。真实集成测试写入 3 条 chunk 并统一 refresh，BM25 查询命中 2 条，首条 `_score=0.7385771`、次条 `_score=0.4700036`，成功转换为连续 rank 和 `source=ELASTICSEARCH_BM25` 的 `RetrievalCandidate`；测试 1 条通过、0 失败、0 错误。

文档上传后的自动同步、更新、删除和全量重建属于第 2 天，不作为今天的阻塞项。

## 三、固定数据流

```text
用户问题
  -> Redis 向量召回 List<RetrievalCandidate>
  -> Elasticsearch BM25 召回 List<RetrievalCandidate>
  -> 按 documentId + chunkIndex 识别同一分块
  -> RRF 根据各路 rank 计算 fusionScore
  -> 选择最终 Top K
  -> MySQL 批量补全最新、权威的分块与文档数据
  -> 组装 RetrievalHit
  -> RAG / Agent 使用 chunk 证据回答
  -> 返回答案与引用
```

## 四、固定技术取舍

- MySQL 是 `Source of Truth`（权威数据源）。
- Redis 与 Elasticsearch 是可以从 MySQL 重建的检索索引。
- Redis `rawScore` 与 Elasticsearch BM25 `_score` 不直接相加。
- 使用 RRF 根据两路 `rank` 融合结果。
- 候选阶段不逐条查询 MySQL；只在 RRF Top K 确定后批量补全。
- `RetrievalCandidate` 是后端内部候选，不作为最终前端响应。
- `RetrievalHit` 是融合、权限校验和 MySQL 补全后的最终证据。

## 五、明确不进入本轮六天计划

- 制度审查
- MCP
- Cross-Encoder Reranker
- 重写完整异步上传系统
- 生产级复杂 ACL/RBAC 重构
- 大规模前端改版
- 与核心面试链路无关的原项目清理

## 六、进度判断规则

- 不能以“类已经创建”判断完成，必须达到当天验收成果。
- 子任务进度与当天整体进度分开计算。例如 Redis 完成 85%，不代表包含 Elasticsearch 的今天计划完成 85%。
- 编译通过只代表类型和 API 正确；需要真实依赖运行验证的链路，必须在运行成功后才算完成。
- 每完成一条完整链路，再统一更新学习笔记，避免笔记被零散语法和半成品设计淹没。
