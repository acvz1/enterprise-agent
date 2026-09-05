# NEXUS Enterprise Knowledge Base

Spring Boot 3.2.10 / Java 21 企业知识库系统，集成 RocketMQ 异步文档入库、RAG 检索增强问答、LLM-as-a-Judge 质量验证。

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 框架 | Spring Boot 3.2.10, Java 21 虚拟线程 |
| 消息队列 | Apache RocketMQ 5.x (`rocketmq-spring-boot-starter:2.3.6`) |
| 持久化 | MySQL 8, Spring Data JPA |
| 向量检索 | Redis Stack (RediSearch) |
| 全文检索 | Elasticsearch 8 |
| LLM | DeepSeek / Qwen / Kimi / Ollama（可切换）|
| 监控 | Micrometer + Prometheus + Actuator |

---

## 文档异步入库链路

```
POST /api/documents/upload
  └── DocumentProcessingService.uploadFileAsync
        ├── 存储文件 (DocumentFileStorage)
        ├── 写 UploadProgress (status=PENDING)
        └── DocumentIngestionProducer.send(uploadId)
              └── RocketMQ topic: document-ingestion

DocumentIngestionConsumer (RocketMQListener<MessageExt>)
  └── DocumentIngestionService.process(uploadId, isLastAttempt)
        ├── tryClaim()         — 原子抢占 (generation + attemptToken)
        ├── doIngest()         — Parse → Chunk → Embed → BUILD/VALIDATE/CAS SWITCH
        ├── markCompleted()    — status=COMPLETED (仅 CAS 切换成功后)
        └── 失败时:
              isLastAttempt=true  → markFailed (status=FAILED)
              isLastAttempt=false → resetToRetry + throw → RECONSUME_LATER
```

### 幂等 claim 状态机

```
PENDING / UPLOADING / PARSING / CHUNKING / EMBEDDING
  → doAtomicClaim (UPDATE ... WHERE status='PENDING') → PROCESSING

PROCESSING (lease 超时 >10min)
  → resetToPending → doAtomicClaim → PROCESSING

COMPLETED / FAILED
  → 跳过（skip）
```

### DLQ 触发条件

`maxReconsumeTimes=3`，`reconsumeTimes >= 3` 时 `isLastAttempt=true`，
Consumer 调用 `markFailed`，消息进入 DLQ，DB 记录 `FAILED+lastError`。

---

## 人工重试

```
POST /api/ingestion/tasks/{uploadId}/retry
```

仅 `FAILED` 状态可重试。重置为 `PENDING` 后重新发送 MQ 消息。

---

## 本地启动

### 前置服务

```bash
docker compose -f docker/docker-compose.yml up -d
```

启动 MySQL、Redis Stack、Elasticsearch、RocketMQ（namesrv + broker）。

### 环境变量

```bash
export DB_HOST=localhost
export DB_PORT=3307
export REDIS_HOST=localhost
export ELASTICSEARCH_URIS=http://localhost:9200
export ROCKETMQ_NAMESRV=localhost:9876
export DEEPSEEK_API_KEY=your-key
export DASHSCOPE_API_KEY=your-key   # 通义千问
export KIMI_API_KEY=your-key
```

### 运行

```bash
./mvnw spring-boot:run
```

服务启动于 `http://localhost:8080`。

---

## 数据库 Migration

新增字段需手动执行（JPA `ddl-auto=update` 会自动添加，但建议生产环境手动执行）：

```sql
-- UploadProgress
ALTER TABLE upload_progress
  ADD COLUMN generation     INT          NOT NULL DEFAULT 0,
  ADD COLUMN attempt_token  VARCHAR(36),
  ADD COLUMN last_error     VARCHAR(500);

-- Document (版本化切换)
ALTER TABLE documents
  ADD COLUMN active_version INT NOT NULL DEFAULT 1;

-- DocumentChunk (版本化构建)
ALTER TABLE document_chunks
  ADD COLUMN document_version INT NOT NULL DEFAULT 1;
```

---

## 检索评测

```bash
./mvnw test -pl . -Dtest=RetrievalEvaluationV2IT -Dspring.profiles.active=test
```

评测语料位于 `src/test/resources/evaluation/retrieval-evaluation-v2.json`（42 cases）。

---

## 监控

- Health: `GET /actuator/health`
- Prometheus: `GET /actuator/prometheus`
- 文档处理耗时指标: `document.processing.time`
- 上传计数指标: `document.upload.count`
