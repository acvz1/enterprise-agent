# Enterprise Knowledge Agent

一个用于学习与求职展示的 Java 企业知识库 Agent 二次开发项目。

当前仓库已经完成上游代码清理。它现在能完成登录、文档管理、知识库搜索和大模型问答，但还不会让大模型自己选择工具，因此还不是完整 Agent。

如果你刚开始学习后端，不要先背下面的技术栈。请从 [这个项目到底在做什么](docs/00-start-here.md) 开始，再看 [三条调用链](docs/02-architecture-and-call-chain.md)。

## 当前能力

- Java 21 + Spring Boot 后端。
- 用户登录和接口权限检查。
- 文档上传、解析、分段、版本、分类与标签。
- 根据问题搜索相关文档，再让大模型根据文档回答。
- 保存最近对话和短期回答缓存。
- 同时使用语义搜索和关键词搜索，并合并两份排名。
- 回答可以逐步推送到前端。
- MySQL、Redis、Docker 和 Vue 管理页面。

## 当前不宣称具备

- 让大模型自己选择并反复调用工具。
- 在搜索时严格排除当前用户无权查看的文档。
- 可重试、可恢复的后台文档处理任务。
- 回答精确引用到某篇文档的某个段落。
- MCP 或正式的多 Agent 通信。

这些不是藏起来的缺陷，而是本项目的二次开发主线。详见 [上游审计](docs/01-upstream-audit.md) 和 [功能模块与工作量预估](docs/03-secondary-development-roadmap.md)。

## 最小启动

要求：JDK 21、Docker Desktop、Node.js 22+。运行 Maven 前先用 `java -version` 确认当前终端确实是 JDK 21；本项目当前依赖的 Hibernate 增强插件不能直接使用 JDK 25 构建。

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
     ToolRegistry -> KnowledgeSearchTool
                  |     -> 计算当前用户可访问范围
                  |     -> Redis 在权限范围内做向量搜索
                  |     -> Elasticsearch 用相同范围做关键词搜索
                  |     -> 合并排名并再次校验权限
                  -> DocumentTool / MCPTool
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
