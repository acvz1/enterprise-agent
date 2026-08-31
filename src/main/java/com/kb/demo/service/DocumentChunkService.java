package com.kb.demo.service;

import com.kb.demo.dto.ElasticsearchChunkDocument;
import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentChunk;
import com.kb.demo.repository.DocumentChunkRepository;
import com.kb.demo.repository.DocumentRepository;
import org.springframework.scheduling.annotation.Async;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import com.kb.demo.config.EmbeddingModelConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.document.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.springframework.data.redis.core.RedisTemplate;
import java.io.IOException;

/**
 * 文档分块和向量化服务
 * 负责将文档分割成小块并进行向量化处理，以提高检索性能
 * @author LiJingLin
 */
@Service
public class DocumentChunkService {
    
    private static final Logger logger = LoggerFactory.getLogger(DocumentChunkService.class);
    
    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;
    
    @Autowired
    private RedisTemplate<String, ?> redisTemplate;

    @Autowired
    private ElasticsearchSearchService elasticsearchSearchService;

    @Autowired
    private RedisVectorIndexService redisVectorIndexService;

    @Autowired
    private AiService aiService;

    @Autowired
    private DocumentIndexSyncTaskService indexSyncTaskService;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private ApplicationContext applicationContext;
    
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;
    
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;
    
    /**
     * 文档分块大小（字符数）
     */
    private static final int CHUNK_SIZE = 500;
    
    /**
     * 文档分块重叠大小（字符数）
     */
    private static final int OVERLAP_SIZE = 50;
    
    /**
     * 获取DocumentService实例（懒加载，避免循环依赖）
     * @return DocumentService实例
     */
    private DocumentService getDocumentService() {
        return applicationContext.getBean(DocumentService.class);
    }
    
    /**
     * 对文档进行分块和向量化处理
     * @param documentId 文档ID
     * @return 处理后的文档块数量
     */
    public int processDocument(Long documentId) {
        return processDocumentWithProgress(documentId, null);
    }

    /** 版本化入口：显式传入 targetVersion，写入 outbox 后由 afterCommit 触发重建。 */
    public int processDocumentWithVersion(Long documentId, Integer targetVersion) {
        boolean transactionActive = TransactionSynchronizationManager.isSynchronizationActive();
        indexSyncTaskService.request(documentId,
                com.kb.demo.entity.DocumentIndexSyncTask.Operation.REBUILD, targetVersion);
        if (!transactionActive) {
            return getSelf().retryRebuild(documentId);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    getSelf().retryRebuild(documentId);
                } catch (Exception exception) {
                    logger.warn("post-commit rebuild failed, will retry, documentId={}", documentId, exception);
                }
            }
        });
        return 0;
    }
    
    /**
     * 对文档进行分块和向量化处理（支持进度回调）
     * @param documentId 文档ID
     * @param progressCallback 进度回调函数，接收 (当前块索引, 总块数)
     * @return 处理后的文档块数量
     */
    public int processDocumentWithProgress(Long documentId, BiConsumer<Integer, Integer> progressCallback) {
        boolean deferredUntilCommit = enqueueAndDispatchAfterCommit(
                documentId, com.kb.demo.entity.DocumentIndexSyncTask.Operation.REBUILD);
        // 在 DocumentService 的业务事务中，只提交 outbox；afterCommit 再写外部索引。
        // 单独调用"手动向量化"时没有外层事务，任务已先提交，可立即执行并返回 chunk 数。
        return deferredUntilCommit ? 0 : getSelf().retryRebuild(documentId, progressCallback);
    }

    /** 仅供持久化任务重试；不会覆盖原来的失败次数与错误记录。 */
    @Transactional
    public int retryRebuild(Long documentId) {
        return runRebuild(documentId, null);
    }

    /** afterCommit 回调需要保留上传进度时使用。 */
    @Transactional
    public int retryRebuild(Long documentId, BiConsumer<Integer, Integer> progressCallback) {
        return runRebuild(documentId, progressCallback);
    }

    private int runRebuild(Long documentId, BiConsumer<Integer, Integer> progressCallback) {
        Optional<DocumentIndexSyncTaskService.SyncAttempt> attempt = indexSyncTaskService.claim(documentId);
        if (attempt.isEmpty()) {
            logger.debug("索引同步任务正在执行或已完成，跳过重复重建，documentId={}", documentId);
            return 0;
        }
        if (attempt.get().operation() != com.kb.demo.entity.DocumentIndexSyncTask.Operation.REBUILD) {
            indexSyncTaskService.release(attempt.get());
            return 0;
        }

        logger.info("开始处理文档分块和向量化，文档ID: {}", documentId);
        try {
            Document document = getDocumentService().getDocumentById(documentId);
            if (document == null) {
                deleteIndexData(documentId);
                documentChunkRepository.deleteByDocumentId(documentId);
                validateDelete(documentId);
                indexSyncTaskService.markSuccess(attempt.get());
                return 0;
            }

            Integer targetVersion = attempt.get().targetVersion();
            if (targetVersion == null) {
                targetVersion = document.getCurrentVersion() != null ? document.getCurrentVersion() : 1;
            }
            Integer activeVersion = document.getActiveVersion() != null ? document.getActiveVersion() : 1;

            // --- A. 清理可能残留的部分写入的 targetVersion 数据（幂等重试安全）---
            documentChunkRepository.deleteByDocumentIdAndDocumentVersion(documentId, targetVersion);
            redisVectorIndexService.deleteByDocumentIdAndVersion(documentId, targetVersion);
            elasticsearchSearchService.deleteByDocumentIdAndVersion(documentId, targetVersion);

            // --- B. BUILD v{targetVersion} ---
            Long docId = document.getId();
            DocumentSplitter splitter = DocumentSplitters.recursive(CHUNK_SIZE, OVERLAP_SIZE);
            dev.langchain4j.data.document.Document langchainDocument =
                    dev.langchain4j.data.document.Document.from(document.getContent());
            List<TextSegment> segments = splitter.split(langchainDocument);
            logger.info("[DocumentChunk] split into {} segments, documentId={}, targetVersion={}", segments.size(), documentId, targetVersion);

            EmbeddingStore<TextSegment> embeddingStore = RedisEmbeddingStore.builder()
                    .host(redisHost)
                    .port(redisPort)
                    .dimension(EmbeddingModelConfig.EMBEDDING_DIMENSION)
                    .indexName("document-embeddings")
                    .metadataKeys(List.of("documentId", "chunkIndex", "documentVersion"))
                    .build();
            List<ElasticsearchChunkDocument> elasticsearchDocuments = new ArrayList<>();

            final int tv = targetVersion;
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                documentChunkRepository.insertChunkWithVersion(docId, i, segment.text(), tv);
                Metadata metadata = new Metadata()
                        .put("documentId", docId)
                        .put("chunkIndex", i)
                        .put("documentVersion", tv);
                TextSegment indexedSegment = TextSegment.from(segment.text(), metadata);
                String embeddingId = embeddingStore.add(embeddingModel.embed(indexedSegment.text()).content(), indexedSegment);
                redisVectorIndexService.registerEmbedding(docId, embeddingId, tv);
                elasticsearchDocuments.add(new ElasticsearchChunkDocument(docId, i, segment.text(), tv));

                if ((i + 1) % 10 == 0 || i == segments.size() - 1) {
                    if (progressCallback != null) progressCallback.accept(i + 1, segments.size());
                    logger.debug("[向量化进度] 已处理 {}/{} 个块", i + 1, segments.size());
                }
            }

            elasticsearchSearchService.indexChunks(elasticsearchDocuments);
            elasticsearchSearchService.refreshIndex();

            // --- C. VALIDATE ---
            validateRebuildVersion(docId, tv, segments.size());

            // --- D. SWITCH（CAS）---
            int affected = documentRepository.casActiveVersion(docId, activeVersion, tv);
            if (affected == 0) {
                // 并发更新：本次 v{targetVersion} 已被更新的文档版本覆盖，视为已废弃
                logger.warn("CAS switch activeVersion failed, possibly overwritten by newer version, documentId={}, targetVersion={}", documentId, tv);
                indexSyncTaskService.markSuccess(attempt.get());
                scheduleGcAsync(docId, tv);
                return segments.size();
            }

            // --- E. 失效答案缓存 ---
            aiService.invalidateAnswersByDocumentId(documentId);
            logger.info("[DocumentChunk] version switch done, documentId={}, activeVersion={}->{}", documentId, activeVersion, tv);

            indexSyncTaskService.markSuccess(attempt.get());

            // --- F. 异步 GC 旧版本 ---
            scheduleGcAsync(docId, activeVersion);

            return segments.size();
        } catch (Exception exception) {
            indexSyncTaskService.markFailure(attempt.get(), exception);
            throw exception instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new IllegalStateException("文档索引同步失败，documentId=" + documentId, exception);
        }
    }
    
    /**
     * 批量处理文档分块和向量化
     * @param documentIds 文档ID列表
     * @return 处理的总文档块数量
     */
    @Transactional
    public int batchProcessDocuments(List<Long> documentIds) {
        logger.info("开始批量处理文档分块和向量化，文档数量: {}", documentIds.size());
        
        int totalChunks = 0;
        for (Long documentId : documentIds) {
            try {
                totalChunks += processDocument(documentId);
            } catch (Exception e) {
                logger.error("处理文档分块失败，文档ID: {}", documentId, e);
            }
        }
        
        logger.info("批量处理文档分块和向量化完成，共处理 {} 个块", totalChunks);
        return totalChunks;
    }
    
    /**
     * 处理所有文档的分块和向量化
     * @return 处理的总文档块数量
     */
    @Transactional
    public int processAllDocuments() {
        logger.info("开始处理所有文档的分块和向量化");
        
        // 获取所有文档ID
        List<Long> documentIds = getDocumentService().getAllDocumentIds();
        
        return batchProcessDocuments(documentIds);
    }
    
    /**
     * 根据文档ID获取文档块
     * @param documentId 文档ID
     * @return 文档块列表
     */
    public List<DocumentChunk> getChunksByDocumentId(Long documentId) {
        return documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
    }
    
    /**
     * 删除文档的所有分块及其向量数据
     * @param documentId 文档ID
     */
    public void deleteChunksByDocumentId(Long documentId) {
        boolean deferredUntilCommit = enqueueAndDispatchAfterCommit(
                documentId, com.kb.demo.entity.DocumentIndexSyncTask.Operation.DELETE);
        if (!deferredUntilCommit) {
            getSelf().retryDelete(documentId);
        }
    }

    /** 仅供持久化任务重试；不会把旧失败记录覆盖成一个新任务。 */
    @Transactional
    public void retryDelete(Long documentId) {
        runDelete(documentId);
    }

    /**
     * 业务事务内只写 MySQL outbox；提交后才访问 Redis 和 ES。
     * 若 afterCommit 的即时执行失败，任务仍为 PENDING / RETRYING，交给定时扫描恢复。
     */
    private boolean enqueueAndDispatchAfterCommit(
            Long documentId, com.kb.demo.entity.DocumentIndexSyncTask.Operation operation) {
        boolean transactionActive = TransactionSynchronizationManager.isSynchronizationActive();
        indexSyncTaskService.request(documentId, operation, null);
        if (!transactionActive) {
            return false;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (operation == com.kb.demo.entity.DocumentIndexSyncTask.Operation.DELETE) {
                        getSelf().retryDelete(documentId);
                    } else {
                        getSelf().retryRebuild(documentId);
                    }
                } catch (Exception exception) {
                    logger.warn("事务提交后的索引同步失败，将由定时任务重试，documentId={}, operation={}",
                            documentId, operation, exception);
                }
            }
        });
        return true;
    }

    /** 通过 Spring 代理调用，确保 retryRebuild / retryDelete 的事务注解生效。 */
    private DocumentChunkService getSelf() {
        return applicationContext.getBean(DocumentChunkService.class);
    }

    private void runDelete(Long documentId) {
        Optional<DocumentIndexSyncTaskService.SyncAttempt> attempt = indexSyncTaskService.claim(documentId);
        if (attempt.isEmpty()) {
            logger.debug("索引删除任务正在执行或已完成，跳过重复删除，documentId={}", documentId);
            return;
        }
        if (attempt.get().operation() != com.kb.demo.entity.DocumentIndexSyncTask.Operation.DELETE) {
            indexSyncTaskService.release(attempt.get());
            return;
        }

        logger.info("删除文档的所有分块，文档ID: {}", documentId);
        try {
            aiService.invalidateAnswersByDocumentId(documentId);
            deleteIndexData(documentId);
            documentChunkRepository.deleteByDocumentId(documentId);
            validateDelete(documentId);
            indexSyncTaskService.markSuccess(attempt.get());
            logger.info("✅ [文档删除] 索引删除完成且校验通过，documentId={}", documentId);
        } catch (Exception exception) {
            indexSyncTaskService.markFailure(attempt.get(), exception);
            throw exception instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new IllegalStateException("文档索引删除失败，documentId=" + documentId, exception);
        }
    }

    /** Redis 和 ES 均按 documentId 删除；重复执行不会扩大副作用。 */
    private void deleteIndexData(Long documentId) throws IOException {
        long deletedRedisVectors = redisVectorIndexService.deleteByDocumentId(documentId);
        logger.debug("✅ [Redis] 文档向量删除完成，documentId: {}, 删除 {} 条", documentId, deletedRedisVectors);
        long deletedEsChunks = elasticsearchSearchService.deleteByDocumentId(documentId);
        elasticsearchSearchService.refreshIndex();
        logger.debug("✅ [Elasticsearch] 文档分块删除完成，documentId: {}, 删除 {} 条", documentId, deletedEsChunks);
    }

    /** 只有三处数量一致时才把任务标记为成功。 */
    private void validateRebuild(Long documentId, int expectedCount) throws IOException {
        long mysqlCount = documentChunkRepository.countByDocumentId(documentId);
        long redisCount = redisVectorIndexService.countByDocumentId(documentId);
        long esCount = elasticsearchSearchService.countByDocumentId(documentId);
        if (mysqlCount != expectedCount || redisCount != expectedCount || esCount != expectedCount) {
            throw new IllegalStateException("索引分块数量不一致，documentId=" + documentId
                    + ", expected=" + expectedCount
                    + ", mysql=" + mysqlCount
                    + ", redis=" + redisCount
                    + ", elasticsearch=" + esCount);
        }
    }

    /** 按版本校验三侧 chunk 数量，用于版本化 BUILD 后的验证。 */
    private void validateRebuildVersion(Long documentId, Integer version, int expectedCount) throws IOException {
        long mysqlCount = documentChunkRepository.countByDocumentIdAndDocumentVersion(documentId, version);
        long redisCount = redisVectorIndexService.countByDocumentIdAndVersion(documentId, version);
        long esCount = elasticsearchSearchService.countByDocumentIdAndVersion(documentId, version);
        if (mysqlCount != expectedCount || redisCount != expectedCount || esCount != expectedCount) {
            throw new IllegalStateException("版本化索引分块数量不一致，documentId=" + documentId
                    + ", version=" + version
                    + ", expected=" + expectedCount
                    + ", mysql=" + mysqlCount
                    + ", redis=" + redisCount
                    + ", elasticsearch=" + esCount);
        }
    }

    /** 异步 GC：CAS 切换成功后删除旧版本三侧数据，失败仅记录日志不影响新版本服务。 */
    @Async
    public void scheduleGcAsync(Long documentId, Integer oldVersion) {
        if (oldVersion == null) return;
        try {
            documentChunkRepository.deleteByDocumentIdAndDocumentVersion(documentId, oldVersion);
            redisVectorIndexService.deleteByDocumentIdAndVersion(documentId, oldVersion);
            elasticsearchSearchService.deleteByDocumentIdAndVersion(documentId, oldVersion);
            elasticsearchSearchService.refreshIndex();
            logger.info("✅ [GC] 旧版本数据清理完成，documentId={}, oldVersion={}", documentId, oldVersion);
        } catch (Exception e) {
            logger.warn("GC 旧版本数据失败，documentId={}, oldVersion={}，将在下次重建时清理", documentId, oldVersion, e);
        }
    }

    private void validateDelete(Long documentId) throws IOException {
        long mysqlCount = documentChunkRepository.countByDocumentId(documentId);
        long redisCount = redisVectorIndexService.countByDocumentId(documentId);
        long esCount = elasticsearchSearchService.countByDocumentId(documentId);
        if (mysqlCount != 0 || redisCount != 0 || esCount != 0) {
            throw new IllegalStateException("索引删除数量校验失败，documentId=" + documentId
                    + ", mysql=" + mysqlCount
                    + ", redis=" + redisCount
                    + ", elasticsearch=" + esCount);
        }
    }
    
    /**
     * 全量重建向量索引（清理孤立数据）
     * 警告：此操作会清空Redis中的所有向量数据并重新生成，耗时较长
     * @return 重建的文档块数量
     */
    @Transactional
    public int rebuildAllVectorIndex() {
        logger.info("========================================");
        logger.info("开始全量重建向量索引");
        logger.info("========================================");
        
        try {
            // 1. 连接Redis并删除旧索引
            logger.info("[1/3] 清理旧的向量索引...");
            try {
                long deletedRegistrations = redisVectorIndexService.clearAllRegistrations();
                logger.info("清理 Redis 文档向量登记表完成，共删除 {} 个登记 key", deletedRegistrations);
                // 使用RedisTemplate执行FT.DROPINDEX命令
                redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    try {
                        connection.execute("FT.DROPINDEX", "document-embeddings".getBytes(), "DD".getBytes());
                        logger.info("✅ 旧索引删除成功: document-embeddings");
                    } catch (Exception e) {
                        logger.warn("旧索引不存在或删除失败，将创建新索引");
                    }
                    return null;
                });
            } catch (Exception e) {
                logger.warn("清理旧索引时出错: {}", e.getMessage());
            }
            
            // 2. 清除MySQL中的所有文档分块
            logger.info("[2/3] 清理旧的文档分块数据...");
            documentChunkRepository.deleteAll();
            logger.info("✅ 旧分块数据清理完成");
            
            // 3. 重新处理所有文档
            logger.info("[3/3] 重新处理所有文档...");
            int totalChunks = processAllDocuments();
            
            logger.info("========================================");
            logger.info("✅ 全量重建完成！共处理 {} 个文档块", totalChunks);
            logger.info("========================================");
            
            return totalChunks;
        } catch (Exception e) {
            logger.error("全量重建向量索引失败", e);
            throw new RuntimeException("全量重建向量索引失败: " + e.getMessage(), e);
        }
    }
}
