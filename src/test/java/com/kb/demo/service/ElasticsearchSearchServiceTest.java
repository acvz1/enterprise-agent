package com.kb.demo.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchSearchServiceTest {

    @Test
    void filtersScoresBelowConfiguredBm25Threshold() {
        ElasticsearchSearchService service = new ElasticsearchSearchService(null);
        ReflectionTestUtils.setField(service, "minBm25Score", 0.3);

        assertThat(service.meetsMinimumBm25Score(0.29)).isFalse();
        assertThat(service.meetsMinimumBm25Score(0.3)).isTrue();
        assertThat(service.meetsMinimumBm25Score(0.75)).isTrue();
    }
}
