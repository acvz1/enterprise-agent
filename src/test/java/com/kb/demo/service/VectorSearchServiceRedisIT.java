package com.kb.demo.service;

import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用本地 Redis Stack 验证向量检索结果能够还原为 RetrievalCandidate。
 *
 * 该测试依赖 localhost:6379 中已经重建的 document-embeddings 索引，
 * 因此使用 IT 后缀，避免普通单元测试默认执行它。
 */
class VectorSearchServiceRedisIT {

    @Test
    void searchVectorCandidatesReturnsMetadataFromRedis() {
        VectorSearchService service = new VectorSearchService();
        ReflectionTestUtils.setField(service, "redisHost", "localhost");
        ReflectionTestUtils.setField(service, "redisPort", 6379);

        List<RetrievalCandidate> candidates =
                service.searchVectorCandidates("智能搜索", 3, 0.0);

        assertThat(candidates).isNotEmpty();

        RetrievalCandidate first = candidates.get(0);
        assertThat(first.getDocumentId()).isNotNull();
        assertThat(first.getChunkIndex()).isNotNull();
        assertThat(first.getRawScore()).isBetween(0.0, 1.0);
        assertThat(first.getRank()).isEqualTo(1);
        assertThat(first.getSource()).isEqualTo(RetrievalSource.REDIS_VECTOR);
    }
}
