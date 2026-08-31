# 原项目检查结果与二开边界

## 1. 为什么先做审计

这是一个基于 GitHub 开源项目的二次开发。正式改造前需要先确认：

- 哪些能力真实存在，哪些只出现在 README 或配置中。
- 哪些代码可以复用，哪些问题会阻塞后续检索与 Agent 链路。
- 哪些成果来自上游，哪些属于本轮个人改造。

上游固定基线为提交 `82fa41079387b3450787d709b6a6efd17b45c00e`。

## 2. 上游已有能力

| 能力 | 本轮处理 |
|---|---|
| Spring Boot + Vue 3 基础工程 | 保留并作为二开底座 |
| JWT 登录、角色与权限基础设施 | 保留，补充 Agent 权限演示 |
| 文档 CRUD、分类、标签和版本 | 保留 |
| Apache Tika 文件解析 | 保留并用于真实 DOCX/PDF/TXT 验证 |
| Redis 基础向量搜索 | 保留召回能力，重构 chunk 身份与返回模型 |
| 普通 RAG、SSE、会话记忆和回答缓存 | 保留，改为使用精确 chunk 并返回引用 |
| Vue 登录、文档和聊天页面 | 保留并改造成可演示界面 |

面试时应表述为“接手并读懂这些能力”，不能说成从零实现。

## 3. 原项目的关键问题

### Elasticsearch 只有痕迹，没有调用链

配置和说明中出现 Elasticsearch，但 Java 代码没有完成 chunk Mapping、索引写入、BM25 查询和结果转换，不能把它算作已实现能力。

### 向量命中缺少稳定业务身份

Redis 命中正文后按文本反查 MySQL。重复正文可能关联错误，而且每条候选继续查分块和文档，会放大为 N+1 查询。

### RAG 使用完整文档而非命中 chunk

真正相关的分块可能被整篇文档中的无关内容淹没；响应也无法说明答案具体依据哪段原文。

### 异步上传边界不可靠

同类内部调用可能绕过 Spring `@Async` 代理；把请求期 `MultipartFile` 交给后台线程还存在临时文件生命周期问题。

### Agent 和权限边界不完整

原项目是固定“先检索、再调用模型”的 RAG，不是由模型选择工具的 Agent。权限也主要停留在接口级，没有在检索证据返回模型前再次校验。

## 4. 本轮完成的核心改造

| 原问题 | 当前实现与验证 |
|---|---|
| Elasticsearch 未真正接入 | 建立 `document-chunks` Mapping，完成同步、删除、refresh、BM25 查询和真实 ES 集成测试 |
| Redis 命中按正文回表 | `TextSegment` 保存 `documentId + chunkIndex`，召回直接转换为 `RetrievalCandidate` |
| 两路分数不可直接相加 | 各路保留 `rawScore/rank`，使用 RRF 计算 `fusionScore` 并合并来源 |
| 候选阶段逐条查询 MySQL | RRF 选定 Top K 后一次 `JOIN FETCH` 补全，实测 SQL 次数为 1 |
| 模型使用整篇文档 | 使用 `RetrievalHit.content` 精确 chunk 构建 Prompt，并返回 citations |
| 固定 RAG | LangChain4j `AiServices` 注册 `searchKnowledgeBase`，模型按问题决定是否调用 |
| 证据可能越权进入模型 | Controller 检查 `qa:ask`，工具执行检索前检查 `document:read` |
| 只有成功样例 | 15 问固定评测 + DOCX/PDF/TXT 真实上传 + 缺失知识拒答 |

## 5. 清理过的上游噪声

- 删除误提交的依赖和构建产物，不把 `node_modules`、`dist`、Maven 缓存放入版本库。
- 删除绑定原作者电脑的 GraalVM 路径和与当前主线无关的压测配置。
- 删除与本项目 ownership 无关的大批教程和旧面试稿。
- 不保留只有名称、没有代码与验证证据的技术声明。

## 6. 可以与不能宣称的内容

可以说：

> 我基于已有 Spring Boot 知识库系统完成核心检索与 Agent 链路二次开发：将 Redis 向量召回与 Elasticsearch BM25 统一为 chunk 级候选，使用 RRF 融合，Top K 后单次查询 MySQL 补全权威证据，并把检索封装为带权限校验的 LangChain4j 工具。

不能说：

- 整套系统从零独立开发。
- 已完成逐文档、部门、租户和密级 ACL。
- RRF 在所有语料上都优于 BM25。
- 已完成 Reranker、MCP、多 Agent 或生产级失败恢复。

## 7. 来源边界

上游仓库导入时未发现明确许可证文件。在获得明确授权或许可证前，本项目只用于本地学习与面试展示，不公开分发衍生代码。
