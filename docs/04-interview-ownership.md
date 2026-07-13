# 面试表达与 Ownership

## 1. 一句话定位

我基于一个 Java 知识库项目做了工程审计和二次开发，保留 Spring Security、文档业务与基础 RAG，重点重构可靠入库、可信检索，并实现可观测的 Agent Loop、工具系统和 ContextBuilder。

在对应功能真正完成前，只使用“计划实现”，不要提前把路线图说成结果。

## 2. Ownership 表

| 来源 | 可以怎么说 |
|---|---|
| 上游已有 | 我选型并审计了现有底座，读懂鉴权、CRUD、RAG 和 SSE 调用链 |
| 本轮基线改造 | 我删除未接线的 ES/Native/压测噪声，修复不可移植构建配置、grounding 和 RRF 权重失效 |
| 后续亲手实现 | 我设计并实现可靠入库状态机、强类型 SearchHit、ACL/citation、Agent Loop、Tool Registry、ContextBuilder 和 MCP 适配 |
| 框架能力 | Spring/LangChain4j 提供基础设施，我负责边界、控制流、状态、错误策略和测试 |

## 3. 当前已经能讲的技术点

- 为什么先审计再重构：避免在错误依赖和虚假复杂度上叠功能。
- MySQL 与 Redis 的职责：业务事实和派生检索状态分离，派生状态可重建。
- 为什么原混合检索不成立：只拼接去重且忽略权重；现在用加权 RRF 融合两个排名。
- 为什么不能“召回不足就塞任意文档”：会污染上下文，增加无证据回答概率。
- `@Async` 为什么失效：Spring AOP 基于代理，同对象内部调用不经过代理；即使异步成功，请求临时文件也不适合跨线程持有。
- 为什么接口 RBAC 不等于知识库权限：检索必须在召回阶段带入租户/文档 ACL，不能生成前才过滤。
- 为什么向量命中不能靠文本反查：文本不唯一、查询成本高、无法稳定携带 score 与 citation。

## 4. “为什么不用 Python”的高分回答

> 这不是模型训练或算法研究项目，核心边界是企业 AI 应用后端：鉴权、事务、文档权限、任务状态、可观测性和现有 Java 系统集成，所以我选择 Java 21 + Spring Boot。静态类型可以约束 Agent 状态、工具输入输出和错误模型，Spring 生态也能复用 Security、JPA、Validation、Actuator 与 Testcontainers。模型访问和 tool calling 通过 LangChain4j 或标准协议完成，并不要求业务编排必须用 Python。Python 在模型实验、数据处理和快速验证上更有优势；如果团队核心任务是训练、评测研究或大量 Python AI SDK 集成，我会选择 Python 服务，必要时与 Java 主后端通过 MCP/HTTP/消息队列协作。这里的选择依据是系统边界和团队生态，不是语言优劣。

追问“Java 开发慢吗”时：承认 Python 原型更快，但本项目复用了 Spring Boot 和 LangChain4j，核心成本在状态、权限、可靠性与评测，换语言不会消失；类型与成熟基础设施能降低二开后的维护成本。

## 5. 面试官最可能追问

1. RRF 的公式、`k` 常量和权重如何影响排序？为什么不用直接相加相似度？
2. 向量库和 MySQL 双写失败怎么办？谁是事实来源，如何补偿/重建？
3. 如何保证 Agent 不无限调用工具？轮次、时间、token、工具次数分别在哪一层限制？
4. Tool Registry 如何做参数校验、权限、超时、重试和幂等？
5. ContextBuilder 和 Memory/RAG Tool 的职责为什么不同？
6. SSE 断开后 Agent 是否继续？如何释放线程与模型流？
7. 文档 ACL 如何进入向量召回，而不是召回后才过滤？
8. 你的评测集怎么构造，Recall@K、MRR、faithfulness 各说明什么？
9. MCP 与 function calling 的边界是什么？为什么 Agent Loop 不直接依赖 MCP SDK？
10. 哪些代码来自上游，哪三个 commit 最能证明你的个人贡献？

## 6. 60 秒项目介绍模板

> 我做的是一个 Java 企业知识库 Agent。最初我没有从零搭 CRUD，而是审计了一个 Spring Boot RAG 项目，发现它存在异步上传实际不异步、混合检索忽略权重、向量结果靠文本反查、检索没有文档 ACL 等问题。我先固定上游基线并清理未使用技术栈，再把检索改为可解释的排名融合和严格证据回答。后续核心改造是三条线：可靠入库任务状态机；带 chunk 引用和权限的可信 RAG；以及强类型 Agent Loop、Tool Registry、ContextBuilder 和 MCP 适配。整个运行过程通过结构化 SSE 事件和指标观测，并用 Testcontainers 与小型黄金集验证。这个项目主要展示的不是调用一次大模型，而是如何把 Agent 放进一个可维护的企业后端。
