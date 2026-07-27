# 核心代码讲解稿

## 启动入口

```powershell
docker compose -f docker/docker-compose.yml up -d mysql redis elasticsearch
.\mvnw.cmd spring-boot:run
```

## Agent 问答主链

```text
POST /api/ai/agent/ask
  -> AgentController.ask()
  -> KnowledgeAgentService.ask()
  -> LLM 决定调用 searchKnowledgeBase
  -> KnowledgeBaseTool.searchKnowledgeBase()
  -> HybridRetrievalService.searchHits()
  -> Redis + Elasticsearch
  -> RrfFusionService.fuse()
  -> RetrievalResultService.assembleHits()
  -> MySQL 一次 JOIN 补全
  -> AgentResponse(answer, toolUsed, toolNames, citations)
```

## 讲解顺序

1. `AgentController`：请求字段和 `qa:ask`。
2. `KnowledgeAgentService`：`AiServices`、模型、工具执行结果。
3. `KnowledgeBaseTool`：工具描述、Top K 参数、`document:read`。
4. `HybridRetrievalService`：两路检索编排。
5. `RrfFusionService`：复合键、rank 贡献、sources 合并。
6. `RetrievalResultService`：批量查询和交叉组合过滤。
7. `DocumentProcessingWorker`：上传任务状态和两路索引同步。
8. `RetrievalEvaluationIT`：评测语料、标准答案、Hit@3/Recall@3/延迟。

## 失败 Case

三文件并发上传时，一个任务在写 `document_chunks` 阶段发生 MySQL 死锁。文档主记录已经生成，因此没有重复上传，而是用单文档 vectorize 串行重建 3 个 chunk。
