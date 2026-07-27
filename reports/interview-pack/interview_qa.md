# 面试拷问 Q&A

## 1. 为什么使用 Elasticsearch，而不是只保留 Redis 向量检索？

向量检索解决语义相似，BM25 补足制度名、错误码、产品名等精确关键词。两路职责不同；Elasticsearch 已实际进入 chunk 同步和查询链路，不是只写在技术栈中。

## 2. 为什么选择 RRF？

余弦相似度与 BM25 `_score` 量纲不同，不能直接相加。RRF 只依赖各路 rank，统一按 `1/(60+rank)` 累加，同一 chunk 双路命中时贡献自然叠加。

## 3. 为什么 RRF 后还要查 MySQL？

索引用于召回，不是权威数据源。Top K 后才用一条 JOIN 查询取得最新标题、chunk 主键和原文，组装最终 `RetrievalHit`，避免候选阶段 N+1。

## 4. SQL 已经查出结果，为什么 Java 还要过滤？

`documentId IN (...) AND chunkIndex IN (...)` 会查出集合交叉组合。必须再用复合键精确匹配，并恢复 RRF 排序。

## 5. Agent 与固定 RAG 的差别是什么？

固定 RAG 每次都检索；Agent 把 `searchKnowledgeBase` 注册为工具，让模型按问题决定是否调用。知识问题实测调用工具，普通问候不调用。

## 6. 权限为什么分成 `qa:ask` 和 `document:read`？

前者阻止无提问权限的请求进入模型；后者在检索证据返回模型前阻止越权读取。当前只实现全局权限演示，不夸大为逐文档 ACL。

## 7. 为什么 BM25 指标高于 RRF？

小规模中文制度语料包含大量精确词，BM25 占优。RRF 改善了向量单路，但较弱的向量排序拖累部分结果，因此没有超过 BM25。

## 8. 为什么混合检索延迟更高？

当前两路串行调用，耗时相加。下一步可以并行召回，但要同时设计超时、单路降级、SecurityContext 传播和异常处理。

## 9. 文档更新为什么先删旧 chunk？

新正文的 chunk 数量和边界可能变化。只覆盖当前编号会残留旧切片，所以按 `documentId` 删除旧索引后完整重建。

## 10. 批量上传的死锁怎么解释？

三个异步任务并发写 `document_chunks` 时出现真实 MySQL 死锁。文档主记录已保存，使用单文档 vectorize 串行恢复。生产方案是统一锁顺序、限制并发和有限重试。

## 11. 如何证明 Elasticsearch 真的工作？

集成测试创建 Mapping、写入 chunk、refresh 后用 BM25 查询，候选来源为 `ELASTICSEARCH_BM25`；真实 Demo 引用的 `sources` 同时包含 Redis 与 Elasticsearch。

## 12. 哪些功能来自上游？

登录、文档 CRUD、Tika 解析、基础 Redis 向量搜索、基础问答和 Vue 页面来自上游；BM25、统一候选、RRF、批量补全、引用、Agent 工具、权限演示和评测是本轮重点改造。

完整回答见 `docs/04-interview-guide.md`。
