# 14 天二次开发路线

按每天 4–6 小时规划。目标不是把所有流行名词装进仓库，而是形成一条能运行、能测试、能解释、能区分个人贡献的主链。

## 手敲与复用边界

你亲手实现：

- 可靠入库任务状态机和关键测试
- `SearchHit` 与引用链、检索 ACL
- `AgentRunState`、`AgentLoop`、停止条件与错误模型
- `ToolSpec`、`ToolRegistry`、参数校验和执行策略
- `ContextPacket`、`ContextBuilder` 的最小 GSSC
- 至少两个业务工具及一个 MCP 工具适配
- Agent 事件流和一套可复现实验数据

直接复用并读懂调用边界：

- Spring MVC/Security/JPA/Validation
- LangChain4j 的模型客户端和 embedding 模型
- Tika/POI/PDFBox 文档解析
- MySQL、Redis Stack、Prometheus、Grafana
- 上游 Vue 页面和通用 CRUD

## 阶段 0：基线审计（已完成）

- 固定上游 commit/tag 和独立二开分支。
- 删除误提交依赖、未接线技术栈、Native/压测噪声和泛化面试稿。
- 修复不可移植 JDK 配置、严格 grounded prompt、加权 RRF。
- 产出架构、审计、路线和 ownership 文档。

验收：POM/Compose 能静态解析；后端测试与前端生产构建有真实结果记录。

## 阶段 1：Day 1–3，可靠文档入库

目标：把“看起来异步”改成可恢复任务。

1. Day 1：画同步/异步调用链，定义 `IngestionJobStatus`：`PENDING -> PARSING -> CHUNKING -> EMBEDDING -> COMPLETED/FAILED`。
2. Day 2：上传文件先落稳定临时目录；Controller 只创建 job；独立 worker bean 根据 `jobId` 执行，避免 `@Async` 自调用。
3. Day 3：加入幂等键、失败原因、重试次数、清理策略；测试重复提交、进程失败和非法状态迁移。

验收：接口立即返回 `jobId`；任务失败可查询原因；同一幂等键不会产生两份文档；后台线程不持有请求期 `MultipartFile`。

## 阶段 2：Day 4–6，可信 RAG

1. Day 4：定义强类型 `SearchHit`，向量 metadata 写入 `documentId/chunkId`，删除文本反查。
2. Day 5：将 ACL 条件放进召回链；返回引用信息；无证据时拒答。
3. Day 6：准备 20–30 条小型黄金问答集，记录 Recall@K、MRR、引用命中率和延迟；对 RRF 参数做一次对照实验。

验收：每个回答能追到具体 chunk；无权限文档不会进入候选集；一次查询不再产生逐条 chunk 文本反查。

## 阶段 3：Day 7–10，Agent Runtime

1. Day 7：只定义类型和状态：`AgentRunState`、`AgentStatus`、`ToolCall`、`ToolResult`、`AgentEvent`。
2. Day 8：实现最小 `ToolRegistry` 与 `KnowledgeSearchTool`、`DocumentDetailTool`；做参数校验、超时和错误映射。
3. Day 9：实现 `AgentLoop`：模型调用、工具调用、结果回填、最大轮次、最大工具次数、完成/失败。
4. Day 10：实现最小 ContextBuilder：收集 system/memory/RAG/tool result，按优先级和 token 预算选择、结构化、截断。

验收：一个问题可以触发 1–2 次工具调用后回答；未知工具、参数错误、超时、循环超限都有确定状态和测试。

## 阶段 4：Day 11–12，MCP 与事件流

1. Day 11：把一个简单业务能力暴露为本地 MCP Server，再写 `McpToolAdapter` 映射为现有 `ToolSpec`。核心 Agent Loop 不依赖 MCP SDK 类型。
2. Day 12：SSE 输出结构化 `AgentEvent`，处理断开、超时和资源释放；前端展示工具步骤和引用。

验收：本地工具与 MCP 工具通过同一 Registry 被调用；断开 SSE 后任务能按策略取消或转后台，不泄漏无界线程。

## 阶段 5：Day 13–14，工程收口与面试包

1. Day 13：Testcontainers 端到端测试、关键指标、错误码、结构化日志；Docker 一键演示。
2. Day 14：整理 Git 提交、架构图、两组前后对比数据、60 秒项目介绍和追问题库。

验收：新机器按 README 可启动；演示脚本固定；能用 Git diff 证明个人贡献。

## A2A/多 Agent 的边界

两周内不把“多个类互相调用”包装成 A2A。第一项目完成 Agent Runtime 后，只做一个可选的 supervisor + specialist 实验；正式任务生命周期、Agent Card、跨进程协议和协商留给第二个 DeerFlow 项目，更容易形成两个项目不同的面试卖点。
