# STAR 简历项目

## Profile Header

- 目标岗位：Java 后端 / AI 应用后端实习
- 技术栈：Java 21、Spring Boot、LangChain4j、Redis Stack、Elasticsearch、MySQL、Vue 3
- 运行深度：local-full-run
- 当前状态：核心二次开发链路、评测和 Demo 已完整跑通

## 4–5 行版本

**企业知识库 Agent｜Java / Spring Boot / LangChain4j / Redis / Elasticsearch / MySQL**

- 基于开源 Spring Boot 知识库系统进行二次开发，构建“混合检索—权威数据补全—Agent 工具调用—引用返回”的企业问答链路。
- 将 Redis 向量召回与 Elasticsearch BM25 统一为 `RetrievalCandidate`，按 `documentId + chunkIndex` 去重并使用 RRF 融合异构排名。
- 在 RRF Top K 后通过单条 MySQL `JOIN FETCH` 批量补全文档标题与最新 chunk，实测补全阶段只执行 1 条 SQL，并支持普通/SSE 问答返回引用。
- 基于 LangChain4j `AiServices` 封装知识库工具，实现 Agent 自主判断是否检索，并以 `qa:ask`、`document:read` 完成调用前后最小权限校验。
- 构建 15 问评测集，Redis、BM25、RRF 的 Hit@3 分别为 0.80、1.00、0.93；完成 DOCX/PDF/TXT 上传、双路引用和知识缺失拒答 Demo。

## 真实性边界

- 可以说：二次开发、核心检索和 Agent 链路由本人实现并验证。
- 不能说：从零独立开发整套系统。
- 不写入已完成：逐文档 ACL、Reranker、MCP、多 Agent、生产级失败重试。
