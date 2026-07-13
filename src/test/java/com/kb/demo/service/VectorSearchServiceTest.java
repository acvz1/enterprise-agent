package com.kb.demo.service;

import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentChunk;
import com.kb.demo.repository.DocumentChunkRepository;
import com.kb.demo.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * VectorSearchService 单元测试
 * 测试向量检索和关键词检索功能
 */
@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @Mock
    private MetricsService metricsService;

    @InjectMocks
    private VectorSearchService vectorSearchService;

    private Document testDocument1;
    private Document testDocument2;

    @BeforeEach
    void setUp() {
        // 设置 Redis 配置（使用反射注入私有字段）
        ReflectionTestUtils.setField(vectorSearchService, "redisHost", "localhost");
        ReflectionTestUtils.setField(vectorSearchService, "redisPort", 6379);

        // 准备测试数据
        testDocument1 = new Document();
        testDocument1.setId(1L);
        testDocument1.setTitle("Spring Boot 教程");
        testDocument1.setContent("这是一个关于 Spring Boot 的详细教程，包含了依赖注入、AOP 等核心概念。");

        testDocument2 = new Document();
        testDocument2.setId(2L);
        testDocument2.setTitle("LangChain4J 使用指南");
        testDocument2.setContent("LangChain4J 是一个 Java 的 AI 框架，支持多种大语言模型。");
    }

    @Test
    void testFallbackToKeywordSearch_Success() {
        // Given: 模拟关键词检索返回结果
        String query = "Spring Boot";
        List<Document> mockDocuments = Arrays.asList(testDocument1, testDocument2);
        when(documentRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(query, query))
                .thenReturn(mockDocuments);

        // When: 单独验证关键词回退策略，不依赖 Redis 连接失败
        List<Document> results = vectorSearchService.keywordSearch(query, 10);

        // Then: 验证结果
        assertThat(results).isNotNull();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getTitle()).contains("Spring Boot");

        // 验证 Mock 调用
        verify(documentRepository, atLeastOnce())
                .findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(query, query);
    }

    @Test
    void testFallbackToKeywordSearch_EmptyResult() {
        // Given: 模拟关键词检索返回空结果
        String query = "不存在的内容";
        when(documentRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(query, query))
                .thenReturn(new ArrayList<>());

        // When: 调用检索
        List<Document> results = vectorSearchService.keywordSearch(query, 10);

        // Then: 验证返回空列表
        assertThat(results).isNotNull();
        assertThat(results).isEmpty();
    }

    @Test
    void testFallbackToKeywordSearch_MaxResultsLimit() {
        // Given: 模拟返回大量结果
        List<Document> manyDocuments = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Document doc = new Document();
            doc.setId((long) i);
            doc.setTitle("测试文档 " + i);
            manyDocuments.add(doc);
        }
        when(documentRepository.findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(anyString(), anyString()))
                .thenReturn(manyDocuments);

        // When: 关键词检索限制最多返回 10 条
        List<Document> results = vectorSearchService.keywordSearch("测试", 10);

        // Then: 验证结果数量被限制
        assertThat(results).isNotNull();
        assertThat(results.size()).isLessThanOrEqualTo(10);
    }

    @Test
    void testRankFusion_RespectsConfiguredWeights() {
        List<Document> vectorRanking = Arrays.asList(testDocument1, testDocument2);
        List<Document> keywordRanking = Arrays.asList(testDocument2, testDocument1);

        List<Document> vectorPreferred = vectorSearchService.fuseRankedResults(
                vectorRanking, keywordRanking, 2, 0.9, 0.1);
        List<Document> keywordPreferred = vectorSearchService.fuseRankedResults(
                vectorRanking, keywordRanking, 2, 0.1, 0.9);

        assertThat(vectorPreferred).containsExactly(testDocument1, testDocument2);
        assertThat(keywordPreferred).containsExactly(testDocument2, testDocument1);
    }

    @Test
    void testGetRelevantSegments_WithChunks() {
        // Given: 准备文档块数据
        DocumentChunk chunk1 = new DocumentChunk();
        chunk1.setContent("这是第一个段落的内容");
        chunk1.setChunkIndex(0);
        chunk1.setDocument(testDocument1);

        DocumentChunk chunk2 = new DocumentChunk();
        chunk2.setContent("这是第二个段落的内容");
        chunk2.setChunkIndex(1);
        chunk2.setDocument(testDocument1);

        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(1L))
                .thenReturn(Arrays.asList(chunk1, chunk2));

        // When: 获取相关段落
        List<String> segments = vectorSearchService.getRelevantSegments(1L, "段落", 5);

        // Then: 验证返回段落
        assertThat(segments).isNotNull();
        assertThat(segments).isNotEmpty();
    }

    @Test
    void testGetRelevantSegments_EmptyChunks() {
        // Given: 文档没有分块
        when(documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(1L))
                .thenReturn(new ArrayList<>());

        // When: 获取相关段落
        List<String> segments = vectorSearchService.getRelevantSegments(1L, "测试", 5);

        // Then: 验证返回空列表
        assertThat(segments).isNotNull();
        assertThat(segments).isEmpty();
    }
}
