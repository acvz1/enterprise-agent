package com.kb.demo.service;

import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalHit;
import com.kb.demo.dto.RetrievalSource;
import com.kb.demo.entity.Document;
import com.kb.demo.entity.DocumentChunk;
import com.kb.demo.repository.DocumentChunkRepository;
import com.kb.demo.repository.DocumentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RetrievalResultService.class)
@Testcontainers(disabledWithoutDocker = true)
class RetrievalResultServiceMySqlIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("retrieval_result_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add(
                "spring.jpa.properties.hibernate.generate_statistics",
                () -> "true");
    }

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private RetrievalResultService retrievalResultService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void assembleHitsLoadsDocumentAndChunkWithOneMySqlQuery() {
        Document document = new Document();
        document.setTitle("MySQL authority document");
        document.setContent("Complete document body");
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        Document savedDocument = documentRepository.saveAndFlush(document);

        DocumentChunk firstChunk =
                new DocumentChunk(savedDocument, 0, "First authoritative chunk");
        DocumentChunk secondChunk =
                new DocumentChunk(savedDocument, 1, "Second authoritative chunk");
        documentChunkRepository.saveAllAndFlush(List.of(firstChunk, secondChunk));

        entityManager.clear();
        Statistics statistics =
                entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        FusedRetrievalCandidate candidate = new FusedRetrievalCandidate(
                savedDocument.getId(),
                1,
                0.032,
                Set.of(
                        RetrievalSource.REDIS_VECTOR,
                        RetrievalSource.ELASTICSEARCH_BM25));

        List<RetrievalHit> hits =
                retrievalResultService.assembleHits(List.of(candidate));

        assertThat(hits).hasSize(1);
        RetrievalHit hit = hits.get(0);
        assertThat(hit.getDocumentId()).isEqualTo(savedDocument.getId());
        assertThat(hit.getChunkId()).isEqualTo(secondChunk.getId());
        assertThat(hit.getChunkIndex()).isEqualTo(1);
        assertThat(hit.getDocumentTitle()).isEqualTo("MySQL authority document");
        assertThat(hit.getContent()).isEqualTo("Second authoritative chunk");
        assertThat(hit.getFusionScore()).isEqualTo(0.032);
        assertThat(hit.getSources())
                .containsExactlyInAnyOrder(
                        RetrievalSource.REDIS_VECTOR,
                        RetrievalSource.ELASTICSEARCH_BM25);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }
}
