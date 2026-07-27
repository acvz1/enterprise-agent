# 企业知识库 Agent：项目介绍

## 1. 项目解决什么问题

企业的员工手册、报销制度和故障流程通常散落在不同文件中。员工提问时，系统需要先找到相关原文，再让大模型依据原文回答，并返回可以核验的引用。

本项目基于一个开源 Spring Boot 知识库系统进行二次开发，目标岗位是 Java 后端 / AI 应用后端实习。项目不训练模型，重点解决企业知识进入大模型前后的检索、数据一致性、权限和工程验证问题。

## 2. 当前核心链路

```text
DOCX / PDF / TXT
  -> 异步上传与 Apache Tika 解析
  -> MySQL 保存 Document 与 DocumentChunk
  -> Redis 向量召回 + Elasticsearch BM25 召回
  -> RetrievalCandidate
  -> RRF 排名融合
  -> Top K 一次查询 MySQL 补全权威原文
  -> RetrievalHit
  -> RAG / Agent 回答并返回 citations
```

MySQL 是权威数据源；Redis 和 Elasticsearch 是可以从 MySQL 重建的检索索引。

## 3. 本轮二次开发的价值

原项目已经具备登录、文档 CRUD、文件解析、Redis 基础向量搜索、普通 RAG、SSE 和 Vue 页面。本轮重点完成：

- Elasticsearch chunk Mapping、批量同步和 BM25 召回。
- Redis 与 Elasticsearch 统一输出轻量 `RetrievalCandidate`。
- 使用 `documentId + chunkIndex` 识别同一 chunk，并用 RRF 融合两路排名。
- 确定 Top K 后用一条 MySQL `JOIN FETCH` 补全权威正文，避免候选阶段 N+1 查询。
- 普通 RAG、SSE 和 Agent 返回 chunk 级引用证据。
- 将混合检索注册为 LangChain4j 工具，让模型决定是否查询知识库。
- 使用 `qa:ask` 和 `document:read` 完成请求入口与证据返回前的两层权限校验。
- 建立 15 问固定评测集，并用 DOCX、PDF、TXT 完成真实上传和问答验证。

项目的区分度不是“又做了一个 RAG”，而是能用代码、测试和指标解释检索为什么这样分层、数据为什么最终回到 MySQL，以及实际运行暴露了什么边界。

## 4. 已验证结果

| 策略 | Hit@3 | Recall@3 | 平均延迟 | P95 |
|---|---:|---:|---:|---:|
| Redis 向量检索 | 0.8000 | 0.7667 | 116.059 ms | 524.068 ms |
| Elasticsearch BM25 | 1.0000 | 1.0000 | 52.373 ms | 70.848 ms |
| RRF 混合检索 | 0.9333 | 0.9333 | 214.430 ms | 348.513 ms |

当前小规模中文制度语料中 BM25 最好；RRF 优于单独向量检索，但没有超过 BM25。Top K 的 MySQL 补全实测只执行 1 条 SQL。

真实 Demo 已验证：

- 员工手册 DOCX：远程办公日期与申请截止时间。
- 差旅制度 PDF：住宿上限与跨 chunk 报销时限。
- 故障流程 TXT：P1 响应时间与升级对象。
- 知识库缺失股票期权信息时，Agent 明确拒答。

## 5. 当前边界

- 权限是全局 `qa:ask + document:read`，不是逐文档 ACL。
- Redis 与 Elasticsearch 当前串行召回，混合检索延迟较高。
- 并发上传曾触发一次 MySQL 死锁，已通过单文档串行向量化恢复，但尚未实现自动重试。
- 复合问题可能重复调用工具并产生重复引用。
- 未实现 Reranker、MCP、多 Agent 和生产级任务恢复。

## 6. 阅读顺序

1. [原项目检查与二开边界](01-upstream-audit.md)
2. [当前架构与核心调用链](02-architecture-and-call-chain.md)
3. [六天核心开发计划与验收](03-six-day-core-plan.md)
4. [项目介绍、简历描述与面试追问](04-interview-guide.md)

普通 Java 语法、第三方 API 学习过程和历史排错记录不放在项目文档中，统一保存在个人学习笔记。
