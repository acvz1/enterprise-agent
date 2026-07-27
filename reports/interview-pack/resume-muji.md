[姓名] - Java 后端开发实习生 - AI 应用后端

::: left

icon:info [性别/出生年月]

icon:phone [手机号]

icon:weixin [微信号]

:::

::: right

icon:email [邮箱]

[icon:github GitHub](https://github.com/[你的账号])

:::

教育背景

::: left

**[学校名称] - [专业名称]**

:::

::: right

**[入学年月] - [毕业年月]**

:::

[学历]。GPA/专业排名：[有优势则填写，没有可删除]。

主修课程：[Java 程序设计、数据结构、计算机网络、操作系统、数据库原理等，请按实际填写]。

[奖项、竞赛、奖学金或校园经历；没有有价值内容则删除本行。]

项目

::: left

**企业知识库 Agent（开源项目二次开发）**

:::

::: right

**2026.07**

:::

Java 21　Spring Boot　Spring Security　Spring Data JPA　LangChain4j　Redis Stack　Elasticsearch　MySQL　Maven　Docker Compose

**项目描述**：

基于开源 Spring Boot 知识库系统进行二次开发，面向企业制度与业务文档，构建“文档入库—混合检索—权威数据补全—Agent 工具调用—引用返回”的知识库问答链路。

**工作内容**：

- 将 Redis 向量召回与 Elasticsearch BM25 统一转换为 `RetrievalCandidate`，使用 `documentId + chunkIndex` 识别同一分块，并通过 RRF 融合两路排名，避免直接相加量纲不同的向量相似度与 BM25 `_score`。

- 在 RRF 确定 Top K 后，通过单条 MySQL `JOIN FETCH` 批量补全文档标题与最新 chunk 原文，组装 `RetrievalHit`；真实 MySQL 测试确认补全阶段只执行 1 条 SQL，避免候选阶段产生 N+1 查询。

- 基于 LangChain4j `AiServices` 将混合检索封装为 `searchKnowledgeBase` 工具，实现 Agent 根据问题自主决定是否检索；使用 `qa:ask` 与 `document:read` 完成接口入口和证据返回模型前的两层权限校验。

- 构建包含 8 个 chunk、15 个标注问题的固定评测集，Redis、BM25、RRF 的 Hit@3 分别为 0.80、1.00、0.93；完成 DOCX、PDF、TXT 真实上传、双路引用及知识缺失拒答验证。

- 在三份文档并发入库时定位 MySQL 死锁，根据已落库的 `Document` 主记录执行单文档向量化恢复，避免重复创建文档；将自动死锁重试与并发限制记录为后续优化边界。

技能

**Java 基础**：熟悉面向对象、集合、异常、IO、泛型与 Lambda，理解反射、线程池和常见并发问题，能够阅读并维护分层 Java 后端项目。

**Spring**：熟悉 Spring Boot 的 Controller、Service、Repository 分层与构造器注入，理解 Bean、AOP 代理、`@Async`、事务边界及 Spring Security 方法级鉴权。

**数据库**：熟悉 MySQL、JPA 实体关联和常用 SQL，理解事务、索引、JOIN 查询、N+1 问题与死锁的基本排查思路。

**检索与缓存**：熟悉 Redis 缓存、会话存储及 Redis Stack 向量索引；能够使用 Elasticsearch Java Client 完成 Mapping、批量写入、`refresh`、`delete_by_query` 和 BM25 `match` 查询。

**AI 应用**：理解 RAG、chunk、Embedding、Prompt Context、RRF 与 Agent Tool Calling，能够使用 LangChain4j 构建带引用证据和权限校验的企业知识库问答链路。

**工具**：能够使用 Maven、Git、Docker Compose、Postman、JUnit、Mockito 和 Testcontainers 进行本地开发、接口调试、单元测试与集成验证。
