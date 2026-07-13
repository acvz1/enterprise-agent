package com.kb.demo;

import com.kb.demo.entity.Document;
import com.kb.demo.repository.DocumentRepository;
import com.kb.demo.service.DocumentService;
import com.kb.demo.service.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试 - 使用 Testcontainers 启动真实的 MySQL 和 Redis 容器
 * 这个测试会：
 * 1. 自动启动 MySQL 容器
 * 2. 自动启动 Redis Stack 容器（支持向量检索）
 * 3. 运行端到端的业务流程测试
 * 4. 测试结束后自动销毁容器
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class IntegrationIT {

    // 启动 MySQL 容器
    @Container
    static MySQLContainer<?> mysqlContainer = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("test_kb")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false);

    // 启动 Redis Stack 容器（支持向量检索）
    @Container
    static GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis/redis-stack:latest"))
            .withExposedPorts(6379)
            .withReuse(false);

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private VectorSearchService vectorSearchService;

    /**
     * 动态注入容器的配置到 Spring Boot
     */
    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // 覆盖 MySQL 配置
        registry.add("spring.datasource.url", mysqlContainer::getJdbcUrl);
        registry.add("spring.datasource.username", mysqlContainer::getUsername);
        registry.add("spring.datasource.password", mysqlContainer::getPassword);
        
        // 覆盖 Redis 配置
        registry.add("spring.data.redis.host", redisContainer::getHost);
        registry.add("spring.data.redis.port", () -> redisContainer.getMappedPort(6379));
        
    }

    @Test
    void contextLoads() {
        // 验证 Spring 容器能够正常启动
        assertThat(documentRepository).isNotNull();
        assertThat(documentService).isNotNull();
        assertThat(vectorSearchService).isNotNull();
    }

    @Test
    void testDatabaseConnection() {
        // 验证 MySQL 容器正常工作
        assertThat(mysqlContainer.isRunning()).isTrue();
        assertThat(documentRepository.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testSaveAndRetrieveDocument() {
        // Given: 创建测试文档
        Document document = new Document();
        document.setTitle("集成测试文档");
        document.setContent("这是一个用于集成测试的文档内容，包含了 Spring Boot 和 LangChain4J 的相关信息。");

        // When: 保存文档
        Document savedDoc = documentRepository.save(document);

        // Then: 验证保存成功
        assertThat(savedDoc.getId()).isNotNull();
        assertThat(savedDoc.getTitle()).isEqualTo("集成测试文档");

        // When: 从数据库检索
        Document retrievedDoc = documentRepository.findById(savedDoc.getId()).orElse(null);

        // Then: 验证检索成功
        assertThat(retrievedDoc).isNotNull();
        assertThat(retrievedDoc.getTitle()).isEqualTo("集成测试文档");
        assertThat(retrievedDoc.getContent()).contains("Spring Boot");
    }

    @Test
    void testVectorSearchWithRealRedis() {
        // Given: 准备测试文档
        Document doc1 = new Document();
        doc1.setTitle("Spring Boot 核心概念");
        doc1.setContent("Spring Boot 是一个基于 Spring 框架的开源 Java 框架。");
        documentRepository.save(doc1);

        Document doc2 = new Document();
        doc2.setTitle("Java 编程语言");
        doc2.setContent("Java 是一种面向对象的编程语言，具有跨平台特性。");
        documentRepository.save(doc2);

        // When: 执行向量检索（会自动回退到关键词检索，因为没有向量化）
        List<Document> results = vectorSearchService.searchDocuments("Spring Boot");

        // Then: 验证检索结果
        assertThat(results).isNotNull();
        assertThat(results).isNotEmpty();
        // 应该能找到包含 "Spring Boot" 的文档
        assertThat(results.stream().anyMatch(doc -> doc.getTitle().contains("Spring Boot"))).isTrue();
    }

    @Test
    void testEndToEndDocumentWorkflow() {
        // 端到端测试：保存文档 → 检索文档 → 删除文档

        // Step 1: 保存文档
        Document document = new Document();
        document.setTitle("端到端测试文档");
        document.setContent("这是一个完整的端到端测试流程。");
        Document saved = documentRepository.save(document);
        assertThat(saved.getId()).isNotNull();

        // Step 2: 检索文档
        List<Document> searchResults = vectorSearchService.searchDocuments("端到端");
        assertThat(searchResults).isNotEmpty();
        assertThat(searchResults.stream().anyMatch(doc -> doc.getId().equals(saved.getId()))).isTrue();

        // Step 3: 删除文档
        documentRepository.deleteById(saved.getId());
        assertThat(documentRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    void testMultipleDocumentsSearch() {
        // Given: 批量保存多个文档
        for (int i = 1; i <= 5; i++) {
            Document doc = new Document();
            doc.setTitle("测试文档 " + i);
            doc.setContent("这是第 " + i + " 个测试文档的内容。");
            documentRepository.save(doc);
        }

        // When: 执行关键词检索
        List<Document> results = vectorSearchService.searchDocuments("测试文档");

        // Then: 验证检索到多个结果
        assertThat(results).isNotNull();
        assertThat(results.size()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void testRedisConnection() {
        // 验证 Redis 容器正常工作
        assertThat(redisContainer.isRunning()).isTrue();
        
        // 验证 Redis 端口映射
        Integer redisPort = redisContainer.getMappedPort(6379);
        assertThat(redisPort).isNotNull();
        assertThat(redisPort).isGreaterThan(0);
    }
}
