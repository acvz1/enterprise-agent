# 上游基线审计

## 结论

这个仓库适合作为二开底座，不适合作为“已经完成的企业 Agent”直接包装。它的价值在于后端业务面较完整，而 Agent 核心明显缺失，正好给个人 ownership 留出空间。

## 保留的能力

| 能力 | 主要位置 | 保留原因 |
|---|---|---|
| JWT/RBAC | `security`、`config/SecurityConfig`、用户/角色实体 | 可学习企业请求鉴权链，不必重复造密码学轮子 |
| 文档业务 | Document、Version、Category、Tag 及对应 Controller/Service/Repository | 提供真实 CRUD 和关系建模上下文 |
| 文件解析 | `FileParseService`、Apache Tika/POI/PDFBox | 通用基础设施，适合复用 |
| RAG 基线 | `DocumentChunkService`、`VectorSearchService` | 可在真实缺陷上改造召回、排序、引用和 ACL |
| Memory | `ChatMemoryStore` | 可作为后续 ContextBuilder 的候选信息源 |
| 模型适配 | `ModelFactory`、LangChain4j | 不必手写 HTTP 客户端，重点放在 Agent 运行时 |
| SSE/指标 | `AiController`、`MetricsService`、Actuator | 可继续改造成 Agent 事件流和运行追踪 |
| Vue 前端 | `ai-assistant-front` | 用于演示，不把两周浪费在 UI 基础施工 |

## 已发现问题与处置

| 级别 | 问题 | 本轮处置 / 后续任务 |
|---|---|---|
| P0 | 上游未提供明确项目许可证 | 增加 `UPSTREAM_NOTICE.md`；确认授权前仅本地使用 |
| P0 | `.mvn/jvm.config` 硬编码作者本机 GraalVM 路径 | 已删除 |
| P0 | 根目录误提交 374 个 `node_modules` 文件 | 已删除并加入忽略规则 |
| P0 | 异步上传同类调用 `@Async`，代理不会生效；同时把请求期 `MultipartFile` 传给后台线程 | 暂标记为实验功能；阶段 1 由学习者重做任务链 |
| P0 | 只有接口权限，没有把用户/知识空间 ACL 带入检索 | 阶段 2 增加权限过滤，禁止先检索后过滤 |
| P1 | 向量命中通过 chunk 文本反查 MySQL，存在歧义与 N+1 | 阶段 2 将 `chunkId/documentId` 写入向量 metadata |
| P1 | 所谓混合检索曾忽略权重，只拼接去重 | 已改为加权 Reciprocal Rank Fusion，并补单测 |
| P1 | 检索不足时补任意文档，提示词允许模型自由发挥 | 已删除随机补文档并改为严格证据回答 |
| P1 | MySQL 删除文档后可能残留 Redis 向量 | 阶段 1/2 设计幂等写入、补偿和重建任务 |
| P1 | SSE 使用无限超时和无界 cached thread pool | 阶段 3 随 Agent 事件流一起改造 |
| P1 | 回答“评估”主要依据长度、连接词和文档数量 | 只能称启发式指标；阶段 4 增加检索/忠实度测试集 |
| P2 | Elasticsearch 有依赖、配置和容器，但 Java 代码未使用 | 已全部移除，避免虚假技术栈 |
| P2 | GraalVM、JMeter、Gatling、JMH 和泛化教程制造复杂度 | 已从二开基线移除；真实需要时再按数据驱动引入 |

## 本轮清理后的诚实能力边界

现在可以说：这是一个可继续改造的 Java RAG 应用底座，具备业务后端、向量检索和流式问答。

现在不能说：它已经有自主 Agent、生产级异步入库、可信评估、租户级知识隔离或 MCP/A2A。
