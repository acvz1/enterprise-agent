# Enterprise Knowledge Agent

一个用于学习与求职展示的 Java 企业知识库 Agent 二次开发项目。

当前仓库已经完成上游基线清理，但必须准确理解它的现状：**现在是带鉴权、文档管理、RAG 和流式问答的知识库应用，还不是完整 Agent**。接下来的核心任务是在这条可运行链路上亲手加入 Agent Loop、Tool Calling、ContextBuilder 和可观测的运行状态。

## 当前能力

- Spring Boot 3 + Java 21 后端
- Spring Security + JWT + RBAC
- 文档上传、解析、分块、版本、分类与标签
- LangChain4j 模型适配与 Redis Stack 向量检索
- Redis 会话记忆与回答缓存
- 加权 RRF 混合检索、严格知识库证据约束
- SSE 流式问答
- MySQL、Redis、Prometheus、Grafana 的 Docker 环境
- Vue 3 + TypeScript 管理前端

## 当前不宣称具备

- 生产级 Agent Loop、工具注册与工具执行策略
- 文档级/知识空间级检索权限隔离
- 可靠的异步入库任务、失败恢复与向量/数据库一致性
- 完整引用链、RAGAS 类评测或可信幻觉检测
- MCP/A2A 协议实现

这些不是藏起来的缺陷，而是本项目的二次开发主线。详见 [上游审计](docs/01-upstream-audit.md) 和 [14 天路线](docs/03-secondary-development-roadmap.md)。

## 最小启动

要求：JDK 21、Docker Desktop、Node.js 22+。

```powershell
# 1. 只启动开发所需基础设施
docker compose -f docker/docker-compose.yml up -d mysql redis

# 2. 配置模型密钥
Copy-Item .env.template .env

# 3. 后端测试与启动
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run

# 4. 前端（另开终端）
Set-Location ai-assistant-front
npm.cmd ci
npm.cmd run dev
```

基础设施端口：MySQL `3307`、Redis `6379`、RedisInsight `8888`。后端默认 `8080`，前端默认 `5173`。

需要验证完整容器与监控时：

```powershell
docker compose -f docker/docker-compose.yml --profile full up -d --build
```

## 核心调用链

```text
HTTP/JWT
  -> Controller
  -> AiService（当前应用服务）
  -> VectorSearchService -> EmbeddingModel -> Redis Stack
                         -> MySQL 关键词检索
                         -> weighted RRF
  -> ChatMemoryStore -> Redis
  -> ModelFactory -> LLM
  -> JSON / SSE
```

目标调用链会演进为：

```text
AgentController
  -> AgentOrchestrator
  -> ContextBuilder
  -> AgentLoop <-> LLM
       | tool_calls
       v
     ToolRegistry -> KnowledgeSearchTool / DocumentTool / MCPTool
  -> AgentRunTrace -> SSE
```

完整模块分工见 [架构与调用链](docs/02-architecture-and-call-chain.md)。

## 二开原则

- 保留上游成熟的通用后端能力，重点改造 AI 应用核心链路。
- 每个阶段都先定义输入、输出、状态和验收测试，再写实现。
- Agent Loop、工具抽象、上下文构建和任务状态由项目作者亲手实现。
- 框架只负责模型客户端、Web/ORM/安全等基础设施，不把核心思考外包给框架。

## 来源与使用边界

本项目基于 [2518350LJL/ai-knowledge-base](https://github.com/2518350LJL/ai-knowledge-base) 的提交 `82fa41079387b3450787d709b6a6efd17b45c00e` 做本地学习型二次开发。导入时上游仓库根目录未发现许可证文件，因此在获得明确授权或许可证前，不应公开分发衍生代码。详见 [UPSTREAM_NOTICE.md](UPSTREAM_NOTICE.md)。
