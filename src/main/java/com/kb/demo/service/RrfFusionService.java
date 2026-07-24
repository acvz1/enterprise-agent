package com.kb.demo.service;

import com.kb.demo.dto.FusedRetrievalCandidate;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RrfFusionService {

    private static final double RRF_K = 60.0;

    public List<FusedRetrievalCandidate> fuse(
            List<RetrievalCandidate> redisCandidates,
            List<RetrievalCandidate> elasticsearchCandidates,
            int topK) {

        if (topK <= 0) {
            return List.of();
        }

        Map<String, FusedRetrievalCandidate> fusedByChunk = new HashMap<>();
        addCandidates(fusedByChunk, redisCandidates);
        addCandidates(fusedByChunk, elasticsearchCandidates);

        List<FusedRetrievalCandidate> result =
                new ArrayList<>(fusedByChunk.values());

        result.sort(
                Comparator.comparingDouble(FusedRetrievalCandidate::getFusionScore)
                        .reversed()
        );

        return result.subList(0, Math.min(topK, result.size()));
    }

    private void addCandidates(
            Map<String, FusedRetrievalCandidate> fusedByChunk,
            List<RetrievalCandidate> candidates) {

        for (RetrievalCandidate candidate : candidates) {
            String key = candidate.getDocumentId() + "_" + candidate.getChunkIndex();
            FusedRetrievalCandidate current = fusedByChunk.get(key);

            Set<RetrievalSource> sources = current == null
                    ? new HashSet<>()
                    : new HashSet<>(current.getSources());
            sources.add(candidate.getSource());

            double rankContribution = 1.0 / (RRF_K + candidate.getRank());
            double fusionScore = rankContribution
                    + (current == null ? 0.0 : current.getFusionScore());

            fusedByChunk.put(
                    key,
                    new FusedRetrievalCandidate(
                            candidate.getDocumentId(),
                            candidate.getChunkIndex(),
                            fusionScore,
                            sources)
            );
        }
    }
}
