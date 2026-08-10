package com.kb.demo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RedisVectorIndexServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    private RedisVectorIndexService service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        service = new RedisVectorIndexService(redisTemplate);
    }

    @Test
    void registerEmbeddingRecordsIdByDocument() {
        service.registerEmbedding(7L, "embedding-id-1");

        verify(setOperations).add("document-embeddings:document:7", "embedding-id-1");
    }

    @Test
    void deleteByDocumentIdDeletesRegisteredEmbeddingKeysAndRegistry() {
        when(setOperations.members("document-embeddings:document:7"))
                .thenReturn(Set.of("embedding-id-1", "embedding-id-2"));
        when(redisTemplate.delete(List.of("embedding:embedding-id-1", "embedding:embedding-id-2")))
                .thenReturn(2L);

        long deletedCount = service.deleteByDocumentId(7L);

        assertThat(deletedCount).isEqualTo(2L);
        verify(redisTemplate).delete("document-embeddings:document:7");
    }

    @Test
    void clearAllRegistrationsDeletesOnlyRegistryKeys() {
        Set<String> registryKeys = Set.of(
                "document-embeddings:document:7",
                "document-embeddings:document:8");
        when(redisTemplate.keys("document-embeddings:document:*")).thenReturn(registryKeys);
        when(redisTemplate.delete(registryKeys)).thenReturn(2L);

        long deletedCount = service.clearAllRegistrations();

        assertThat(deletedCount).isEqualTo(2L);
        verify(redisTemplate).delete(registryKeys);
    }
}
