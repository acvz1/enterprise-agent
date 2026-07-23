package com.kb.demo.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.kb.demo.dto.ElasticsearchChunkDocument;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用本地 Elasticsearch 8.10.4 验证：
 * chunk Mapping、文档写入、refresh、BM25 排名和 RetrievalCandidate 转换。
 */
class ElasticsearchSearchServiceIT {

    private ElasticsearchTransport transport;
    private ElasticsearchSearchService service;

    @BeforeEach
    void setUp() {
        RestClient restClient = RestClient.builder(
                new HttpHost("localhost", 9200, "http")
        ).build();
        transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        service = new ElasticsearchSearchService(new ElasticsearchClient(transport));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (transport != null) {
            transport.close();
        }
    }

    @Test
    void searchBm25CandidatesReturnsRankedChunkMetadata() throws IOException {
        service.indexChunk(new ElasticsearchChunkDocument(
                920001L,
                0,
                "bm25rankingprobe bm25rankingprobe bm25rankingprobe Elasticsearch"
        ));
        service.indexChunk(new ElasticsearchChunkDocument(
                920002L,
                0,
                "bm25rankingprobe enterprise knowledge base"
        ));
        service.indexChunk(new ElasticsearchChunkDocument(
                920003L,
                0,
                "Redis vector semantic retrieval"
        ));
        service.refreshIndex();

        List<RetrievalCandidate> candidates =
                service.searchBm25Candidates("bm25rankingprobe", 3);

        assertThat(candidates).hasSize(2);

        RetrievalCandidate first = candidates.get(0);
        RetrievalCandidate second = candidates.get(1);

        assertThat(first.getDocumentId()).isEqualTo(920001L);
        assertThat(first.getChunkIndex()).isZero();
        assertThat(first.getRawScore()).isGreaterThan(second.getRawScore());
        assertThat(first.getRank()).isEqualTo(1);
        assertThat(second.getRank()).isEqualTo(2);
        assertThat(candidates)
                .extracting(RetrievalCandidate::getSource)
                .containsOnly(RetrievalSource.ELASTICSEARCH_BM25);
    }
}
