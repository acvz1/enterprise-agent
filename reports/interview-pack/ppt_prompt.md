# PPT 生成提示词

请根据本项目资料生成 7 页中文技术面试展示 PPT，适合 Java 后端 / AI 应用后端实习面试。

要求：

1. 明确项目是基于开源 Spring Boot 知识库的二次开发，不写成从零开发。
2. 第 1 页：企业知识库检索问题与个人改造目标。
3. 第 2 页：总体架构，突出 MySQL 权威数据、Redis 向量、Elasticsearch BM25、RRF、Agent。
4. 第 3 页：文档入库链路与 Redis/Elasticsearch 索引同步。
5. 第 4 页：`RetrievalCandidate -> RRF -> RetrievalHit` 数据流，以及为什么 Top K 后才查 MySQL。
6. 第 5 页：Agent 工具调用和 `qa:ask/document:read` 两层权限。
7. 第 6 页：15 问评测表，必须如实展示 BM25 优于当前 RRF，解释语料与串行延迟。
8. 第 7 页：DOCX/PDF/TXT Demo、并发上传死锁、重复 citations 和下一步优化。

使用资料：

- `readme.md`
- `docs/02-architecture-and-call-chain.md`
- `docs/03-six-day-core-plan.md`
- `docs/04-interview-guide.md`

每页给出标题、3–5 个要点、推荐图表以及 30 秒讲稿。不要加入未实现的 MCP、多 Agent、Reranker 或完整文档级 ACL。
