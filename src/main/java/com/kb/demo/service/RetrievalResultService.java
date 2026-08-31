package com.kb.demo.service;

import org.springframework.stereotype.Service;

import com.kb.demo.repository.DocumentChunkRepository;
import com.kb.demo.repository.DocumentRepository;
import com.kb.demo.dto.RetrievalHit;
import com.kb.demo.entity.DocumentChunk;
import com.kb.demo.dto.FusedRetrievalCandidate;


import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;


@Service
public class RetrievalResultService {
    private final DocumentChunkRepository documentChunkRepository;
    private final DocumentRepository documentRepository;

    public RetrievalResultService(DocumentChunkRepository documentChunkRepository,
                                  DocumentRepository documentRepository) {
        this.documentChunkRepository = documentChunkRepository;
        this.documentRepository = documentRepository;
    }

    public List<RetrievalHit> assembleHits(List<FusedRetrievalCandidate> candidates){
        return assembleHits(candidates, null);
    }

    /** 以 MySQL 的部门关联作为最终权威校验，并拒绝版本已过期的 stale candidates。 */
    public List<RetrievalHit> assembleHits(List<FusedRetrievalCandidate> candidates,
            DepartmentAccessService.AccessScope scope){
        if(candidates.size()==0)return List.of();
        Set<Long> documentIds=new HashSet<>();
        Set<Integer> chunkIndexes=new HashSet<>();
        for(FusedRetrievalCandidate candidate:candidates){
            documentIds.add(candidate.getDocumentId());
            chunkIndexes.add(candidate.getChunkIndex());
        }

        // 批量加载 activeVersion，用于 stale candidate 拦截
        Map<Long, Integer> activeVersionMap = new HashMap<>();
        for (Object[] row : documentRepository.findActiveVersionsByIds(documentIds)) {
            Long docId = (Long) row[0];
            Integer av = (Integer) row[1];
            activeVersionMap.put(docId, av);
        }

        List<DocumentChunk> chunks = scope == null || scope.global()
                ? documentChunkRepository.findCandidateChunksWithDocument(documentIds, chunkIndexes)
                : scope.departmentIds().isEmpty() ? List.of()
                        : documentChunkRepository.findCandidateChunksWithDocumentAndDepartments(documentIds, chunkIndexes, scope.departmentIds());

        Map<String,DocumentChunk>chunkByKey=new HashMap<>();
        for(DocumentChunk chunk:chunks){
            // 版本过滤：只保留与 activeVersion 匹配的 chunks
            Integer activeVersion = activeVersionMap.get(chunk.getDocument().getId());
            if (activeVersion != null && chunk.getDocumentVersion() != null
                    && !chunk.getDocumentVersion().equals(activeVersion)) {
                continue;
            }
            String key=chunk.getDocument().getId()+"_"+chunk.getChunkIndex();
            chunkByKey.put(key,chunk);
        }

        List<RetrievalHit>hits=new ArrayList<>();
        for(FusedRetrievalCandidate candidate:candidates){
            String key=candidate.getDocumentId()+"_"+candidate.getChunkIndex();
            DocumentChunk chunk=chunkByKey.get(key);
            if(chunk==null)continue;
            RetrievalHit hit=new RetrievalHit(candidate.getDocumentId(),chunk.getId(),candidate.getChunkIndex(),
                                            chunk.getDocument().getTitle(),chunk.getContent(),candidate.getFusionScore(),
                                            candidate.getSources());
            hit.setDocumentVersion(chunk.getDocumentVersion());
            hits.add(hit);
        }
        return hits;
    }
}
