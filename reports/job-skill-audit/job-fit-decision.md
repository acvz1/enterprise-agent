# 企业知识库项目求职定位审计

生成日期：2026-07-20

目标岗位：Java 后端开发实习、AI 应用后端实习、Agent 开发实习。

> 本报告区分“仓库事实”“判断”和“未来目标”。未来目标在代码、测试和演示完成前，不得写成已经实现。

## 1. 仓库事实

- 后端共有 74 个 Java 源文件、7 个 Java 测试文件。
- 已有能力：Spring Boot、JWT、JPA、MySQL、Redis、文档管理、异步文档入库、RAG、对话记忆、SSE、Docker。
- 在当前源码中搜索 `AgentLoop`、`ToolRegistry`、`ContextBuilder`、`AgentRun`、`tool_calls`、`MCP`、`A2A`，没有命中。
- `AiService` 当前执行的是固定流程：检索文档 -> 拼接上下文 -> 调用模型 -> 返回回答。模型不能选择工具，也不会根据工具结果决定下一步。
- 使用 JDK 21 执行 `mvnw test`，23 个测试全部通过；使用 JDK 25 会在 Hibernate 增强阶段失败。
- Git 历史能够证明当前二次开发已修改异步文档入库，并增加了文件落盘、异步线程和失败路径测试。
- 文档声称存在 `upstream-baseline-82fa410` 标签，但仓库实际没有该标签；上游提交 `82fa41079387b3450787d709b6a6efd17b45c00e` 仍存在，可用于补建基线证据。
- 上游导入时没有发现明确许可证。后来添加许可证不能自动获得上游代码的再许可权，公开分发前需要获得授权或拆出独立原创模块。

## 2. 岗位能力矩阵

| 求职证据 | 当前已有 | 当前缺口 |
|---|---|---|
| Java 后端 | Spring Boot、鉴权、JPA、Redis、异步任务、Docker | 需要形成一条由本人设计的核心业务链路 |
| AI 应用 | 模型接入、RAG、Memory、SSE | 多数来自上游，个人 ownership 不足 |
| Agent 核心 | 暂无 | Agent Loop、工具选择、运行状态、停止条件、失败处理 |
| 工具与协议 | 暂无 | 类型化工具接口；MCP 可留到后续项目 |
| 工程可信度 | 23 个单元测试通过 | 缺少 Agent 的多步、失败、超时和停止测试 |
| 可展示成果 | 项目可运行、接口已用 Postman 验证 | 缺少一眼能看懂的 Agent 业务任务和执行轨迹 |
| 个人贡献证明 | 异步入库改造有代码和测试 | Agent 核心尚未实现；基线标签需要修复 |

岗位样本参考：

- 国家大学生就业服务平台的 AI 应用后端岗位同时要求 Java/Go、Spring Boot、HTTP、数据库、模型接口和 Docker：<https://www.ncss.cn/student/jobs/8V1GQn7vyoFoaeTxadQmCB/detail.html>
- AI Agent 实习岗位强调 Agent 核心架构、任务规划、RAG、记忆和多 Agent：<https://www.nowcoder.com/jobs/detail/450136>
- AI Agent 应用岗位强调工具集成、OpenAPI/RPC/MCP：<https://www.nowcoder.com/jobs/detail/439376>

## 3. 三种二次开发方向比较

评分用于辅助决策，不是客观测评。

| 方向 | 后端工程 20 | Agent 25 | 个人归属 20 | Demo 15 | 十天可交付 10 | 与 DeerFlow 互补 10 | 总分 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 强化 RAG：ES、权限、评测 | 18 | 5 | 16 | 11 | 8 | 8 | 66 |
| 通用 Java Agent Runtime | 17 | 25 | 20 | 10 | 4 | 9 | 85 |
| 最小 Runtime + 企业制度审查 Agent | 17 | 23 | 19 | 15 | 7 | 9 | 90 |

结论：选择第三种。

原因：只强化 RAG 不能证明 Agent 能力；直接做通用 Runtime 容易在十天内留下大量没有业务验证的抽象。企业制度审查场景可以复用现有文档、版本和检索能力，同时迫使 Agent 完成搜索、读取、比较、生成报告的多步任务。

## 4. 推荐项目定位

项目不是“会聊天的知识库”，而是：

> 基于 Spring Boot 的可审计企业制度审查 Agent。Agent 能根据任务动态选择知识检索和文档版本工具，保存每轮决策与执行结果，并在最大轮数、超时或失败条件下安全停止，最终生成带来源引用的审查报告。

最小调用链：

```text
POST /api/agent/runs
  -> 创建 AgentRun
  -> AgentRunner 循环
  -> ContextBuilder 组装目标、历史步骤和工具说明
  -> LLM 返回 AgentDecision
       -> tool_call: search_knowledge
       -> tool_call: read_document_version
       -> final_answer
  -> ToolRegistry 查找工具
  -> ToolExecutor 校验参数并执行
  -> 保存 AgentStep，发送 SSE 事件
  -> 完成 / 最大轮数 / 超时 / 失败
  -> 返回带引用的审查报告
```

## 5. 十天内必须完成

### 5.1 运行状态与持久化：约 1～1.5 天

- `AgentRun`：一次任务，保存目标、当前状态、轮数和最终结果。
- `AgentStep`：一次模型决策或工具执行，保存输入、输出、错误和耗时。
- 明确状态变化：`PENDING -> RUNNING -> COMPLETED / FAILED / TIMEOUT`。

### 5.2 类型化工具系统：约 1.5～2 天

- `AgentTool<I, O>`：统一工具名称、说明、输入类型和执行方法。
- `ToolRegistry`：注册并按名称查找工具。
- `ToolExecutor`：参数转换、校验、超时和异常封装。
- 两个真实工具：`search_knowledge`、`read_document_version`。

### 5.3 最小 Agent Loop：约 2～2.5 天

- 模型只允许返回两类结构化决策：调用工具或给出最终答案。
- 工具结果加入下一轮上下文，模型据此决定下一步。
- 最大轮数、总超时、未知工具和非法参数均能停止或失败。

### 5.4 最小 ContextBuilder：约 0.5～1 天

- 输入：任务目标、工具说明、最近步骤、工具结果。
- 输出：单次模型调用所需的提示内容。
- 只做长度限制和顺序组织，不在第一版加入复杂评分、摘要和长期记忆。

### 5.5 API、SSE 与测试：约 2 天

- 创建任务、查询状态、查看步骤。
- SSE 显示状态变化、工具调用、工具结果和最终报告。
- 至少覆盖：直接回答、多步工具调用、非法参数、工具异常、达到最大轮数。

### 5.6 README、演示与面试材料：约 1 天

- 准备固定的企业制度文档和一条必定需要多步工具调用的演示任务。
- 保存测试结果、Postman 请求、执行轨迹和架构图。
- 根据真实代码生成简历描述和追问题，不提前宣称未实现功能。

总量约 8.5～10 天。它建立在严格控制范围、复用现有 RAG/版本服务的前提下。

## 6. 十天内明确不做

- 不做通用工作流编排平台。
- 不做多 Agent 或 A2A。
- 不做完整 MCP Server/Client 生态。
- 不重写前端，只增加最小执行轨迹展示。
- 不同时重构启发式回答评估。
- 不把 Elasticsearch 作为主线；完整 ES 索引同步、重建和混合检索另需约 3～5 天。

第二个 DeerFlow 项目再展示框架二开、MCP 和多 Agent；第一个项目专门证明 Java Agent Runtime 和后端工程化能力。

## 7. 必须由本人亲手实现

- `AgentRun`、`AgentStep` 和状态变化规则。
- `AgentDecision` 的结构化输出模型。
- `AgentTool`、`ToolRegistry`、`ToolExecutor`。
- `AgentRunner` 的循环和停止条件。
- 最小 `ContextBuilder`。
- Agent 场景测试。

可直接复用：Spring Web/JPA/Security、LangChain4j 模型客户端、现有检索和文档版本 Service、SSE 基础能力、Docker 基础设施。

## 8. 求职验收门槛

项目达到下面全部条件后，才适合把“Agent”写入简历：

1. 至少有一条任务需要两次以上模型决策和两个工具调用。
2. 下一轮决策确实使用了上一轮工具结果。
3. 每个步骤可在数据库中查询，失败原因和耗时可见。
4. 最大轮数、总超时、非法参数和工具异常都有测试。
5. 最终报告包含真实文档或版本引用。
6. 固定演示任务能够稳定复现完整轨迹。
7. 本人能不看文档讲清输入、输出、调用链、状态变化和失败处理。

## 9. 简历表述预演

下面只是目标表述，完成并验证前不得放入简历：

> 基于 Spring Boot 设计并实现状态化 Agent Runtime，抽象 AgentRun/AgentStep 生命周期、类型化 Tool Registry 与最大轮数/超时停止策略，通过 SSE 输出可审计执行轨迹。

> 将企业知识检索和文档版本查询封装为 Agent 工具，使模型能够依据工具结果进行多轮决策并生成带来源引用的制度审查报告；通过直接回答、多步调用、工具失败和循环上限等测试验证运行边界。

## 10. 立即要处理的风险

1. 用现有上游提交补建基线标签，确保 Git 可以生成可靠 ownership diff。
2. 向上游作者确认许可证；未获得授权前，不公开分发整份派生仓库。
3. 将 JDK 21 固化到启动脚本或开发文档，避免 JDK 25 导致构建失败。
4. 清理仓库内 `.m2-cache`、`.npm-cache`、`target`、临时 Skill 文件等生成物，避免审计和公开仓库被噪声污染。
