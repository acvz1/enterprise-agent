package com.kb.demo.service;

import com.kb.demo.dto.ElasticsearchChunkDocument;
import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentChunk;
import com.kb.demo.repository.DocumentChunkRepository;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
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

import java.util.ArrayList;
import java.util.List;
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
    private RedisTemplate<String, ?> redisTemplate;

    @Autowired
    private ElasticsearchSearchService elasticsearchSearchService;
    
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
    @Transactional
    public int processDocument(Long documentId) {
        // 调用带进度回调的方法，不提供回调
        return processDocumentWithProgress(documentId, null);
    }
    
    /**
     * 对文档进行分块和向量化处理（支持进度回调）
     * @param documentId 文档ID
     * @param progressCallback 进度回调函数，接收 (当前块索引, 总块数)
     * @return 处理后的文档块数量
     */
    @Transactional
    public int processDocumentWithProgress(Long documentId, BiConsumer<Integer, Integer> progressCallback) {
        logger.info("开始处理文档分块和向量化，文档ID: {}", documentId);
        
        // 获取文档内容 - 使用em.getReference避免Hibernate字节码增强问题
        Document document = getDocumentService().getDocumentById(documentId);
        if (document == null) {
            logger.warn("文档不存在，ID: {}", documentId);
            return 0;
        }
        
        // 缓存文档ID和内容，避免后续关联问题
        Long docId = document.getId();
        String docContent = document.getContent();
        
        // 清除该文档的旧分块
        documentChunkRepository.deleteByDocumentId(documentId);
        
        // 创建文档分割器
        DocumentSplitter splitter = DocumentSplitters.recursive(CHUNK_SIZE, OVERLAP_SIZE);
        
        // 使用LangChain4J的Document对象
        dev.langchain4j.data.document.Document langchainDocument = 
            dev.langchain4j.data.document.Document.from(document.getContent());
        
        // 分割文档
        List<TextSegment> segments = splitter.split(langchainDocument);
        logger.info("✅ [文档分块] 文档被分割为 {} 个块，文档ID: {}", segments.size(), documentId);
        
        // 创建嵌入模型
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        
        // 创建Redis向量存储，明确指定索引名称
        EmbeddingStore<TextSegment> embeddingStore = RedisEmbeddingStore.builder()
                .host(redisHost)
                .port(redisPort)
                .dimension(384) // AllMiniLmL6V2EmbeddingModel的向量维度
                .indexName("document-embeddings") // 指定索引名称，与VectorSearchService一致
                .metadataKeys(List.of("documentId", "chunkIndex"))
                .build();
        
        // 同步elasticsearch收集
        List<ElasticsearchChunkDocument> elasticsearchDocuments=new ArrayList<>();
    

        logger.info("✅ [向量存储] 连接Redis向量存储成功: {}:{}, 索引: document-embeddings", redisHost, redisPort);
        
        // 处理每个文档块
        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);
            
            // 使用原生SQL插入，避免Hibernate字节码增强问题
            documentChunkRepository.insertChunk(docId, i, segment.text());

            // 添加元数据标识
            Metadata metadata=new Metadata()
                                .put("documentId",docId)
                                .put("chunkIndex",i);
            TextSegment indexedSegment=TextSegment.from(segment.text(),metadata);

            // 将文档块添加到向量存储
            String embeddingId = embeddingStore.add(embeddingModel.embed(indexedSegment.text()).content(), indexedSegment);
            
            // 添加到elasticsearch列表
            elasticsearchDocuments.add(new ElasticsearchChunkDocument(docId,i,segment.text()));

            if (i == 0) {
                logger.debug("✅ [向量存储] 第一个块向量化成功，ID: {}", embeddingId);
            }
            
            // 每处理10个块或最后一个块时，调用进度回调
            if ((i + 1) % 10 == 0 || i == segments.size() - 1) {
                if (progressCallback != null) {
                    progressCallback.accept(i + 1, segments.size());
                }
                logger.debug("[向量化进度] 已处理 {}/{} 个块", i + 1, segments.size());
            }
        }
        
        try {
            long deletedCount=elasticsearchSearchService.deleteByDocumentId(docId);
            elasticsearchSearchService.indexChunks(elasticsearchDocuments);
            elasticsearchSearchService.refreshIndex();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Elasticsearch 文档分块同步失败，documentId=" + docId,
                    exception
    );
}

        logger.info("✅ [文档分块] 文档分块和向量化处理完成，文档ID: {}, 共处理 {} 个块", documentId, segments.size());
        return segments.size();
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
    @Transactional
    public void deleteChunksByDocumentId(Long documentId) {
        logger.info("删除文档的所有分块，文档ID: {}", documentId);
        
        // 1. 从MySQL删除分块数据
        int deletedChunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId).size();
        documentChunkRepository.deleteByDocumentId(documentId);
        logger.debug("✅ [MySQL] 文档分块删除完成，文档ID: {}, 删除 {} 个分块", documentId, deletedChunks);
        
        // 2. 从Redis删除向量数据
        // 注意：LangChain4J的RedisEmbeddingStore没有提供单个向量删除API
        // 当前策略：记录日志，忽略Redis孤立向量（它们不会影响检索结果，因为MySQL中已无对应文档）
        // 最佳实践：定期执行全量重建向量索引，清理孤立数据
        logger.warn("⚠️ [Redis] 向量数据未实时删除，文档ID: {}，建议定期执行全量重建", documentId);

        // 3. 从 Elasticsearch 删除分块
        try {
            long deletedCount = elasticsearchSearchService.deleteByDocumentId(documentId);
            logger.debug("✅ [Elasticsearch] 文档分块删除完成，文档ID: {}, 删除 {} 个分块",
                    documentId, deletedCount);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Elasticsearch 文档分块删除失败，documentId=" + documentId,
                    exception);
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
