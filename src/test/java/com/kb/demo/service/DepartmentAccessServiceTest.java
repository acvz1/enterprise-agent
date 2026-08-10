package com.kb.demo.service;

import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import com.kb.demo.repository.DepartmentRepository;
import com.kb.demo.repository.DocumentRepository;
import com.kb.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DepartmentAccessServiceTest {

    @Test
    void filtersUnauthorizedCandidatesBeforeRrf() {
        DocumentRepository documentRepository = mock(DocumentRepository.class);
        DepartmentAccessService service = new DepartmentAccessService(
                mock(UserRepository.class), mock(DepartmentRepository.class), documentRepository,
                mock(RedisTemplate.class));
        DepartmentAccessService.AccessScope scope = new DepartmentAccessService.AccessScope(false, Set.of(10L));
        when(documentRepository.findReadableDocumentIds(Set.of(10L))).thenReturn(Set.of(1L));

        List<RetrievalCandidate> readable = service.filterCandidates(List.of(
                new RetrievalCandidate(1L, 0, 0.9, 1, RetrievalSource.REDIS_VECTOR),
                new RetrievalCandidate(2L, 0, 0.8, 2, RetrievalSource.REDIS_VECTOR)), scope);

        assertThat(readable).extracting(RetrievalCandidate::getDocumentId).containsExactly(1L);
    }
}
