# ADR-0001：使用 Java 构建 AI 应用后端

- 状态：Accepted
- 日期：2026-07-13

## Context

项目目标是展示企业知识库与 Agent 后端能力，而不是训练模型。主要复杂度是鉴权、数据一致性、异步任务、权限过滤、运行状态、可观测性和与企业系统集成。开发者已有 Java 速学基础，但后端项目经验需要通过真实系统补齐。

## Decision

主应用采用 Java 21、Spring Boot 和 LangChain4j。Agent 运行时的领域类型不直接依赖具体模型厂商或 MCP SDK；外部模型、Python 算法服务或远程 Agent 通过稳定协议接入。

## Consequences

正向影响：

- 可以复用 Spring Security、Validation、JPA、Actuator、Testcontainers 等企业后端基础设施。
- 强类型适合表达 Agent 状态、工具输入输出、事件和失败模型。
- 更贴近 Java 企业后端岗位，也便于解释事务、权限和可观测性。

代价：

- 部分 AI SDK 和研究工具首先出现在 Python，需要适配或拆分服务。
- Java 生态的 Agent 抽象变化较快，必须保持核心领域模型与框架解耦。
- 对初学者来说 Spring 代理、Bean 生命周期、事务边界和线程模型有学习成本。

## Alternatives

全 Python：适合模型实验和快速原型，但不能自动解决本项目最重要的企业工程问题，也弱化 Java 岗位匹配。

Java 主后端 + Python 辅助服务：保留为后续方案。当出现重排序模型、复杂文档算法或 Python-only 评测工具时，通过 HTTP、消息队列或 MCP 接入，而不是强行在单体内重写。
