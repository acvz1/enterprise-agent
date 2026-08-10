package com.kb.demo.service;

import org.springframework.stereotype.Service;

import com.kb.demo.repository.DocumentChunkRepository;
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

    public RetrievalResultService(DocumentChunkRepository documentChunkRepository) {
        this.documentChunkRepository = documentChunkRepository;
    }

    /**
     * 将 RRF 融合后的候选批量补全为最终检索结果。
     *
     * @param candidates 已按融合分数排序的候选列表
     * @return 包含文档标题、分块原文、融合分数和命中来源的结果列表
     */
    public List<RetrievalHit> assembleHits(List<FusedRetrievalCandidate> candidates){
        return assembleHits(candidates, null);
    }

    /** 以 MySQL 的部门关联作为最终权威校验。 */
    public List<RetrievalHit> assembleHits(List<FusedRetrievalCandidate> candidates,
            DepartmentAccessService.AccessScope scope){
        if(candidates.size()==0)return List.of();
        Set<Long> documentIds=new HashSet<>();
        Set<Integer> chunkIndexes=new HashSet<>();
        for(FusedRetrievalCandidate candidate:candidates){
            documentIds.add(candidate.getDocumentId());
            chunkIndexes.add(candidate.getChunkIndex());
        }
        //对文档id集合与chunkid集合查表求总和
        List<DocumentChunk> chunks = scope == null || scope.global()
                ? documentChunkRepository.findCandidateChunksWithDocument(documentIds, chunkIndexes)
                : scope.departmentIds().isEmpty() ? List.of()
                        : documentChunkRepository.findCandidateChunksWithDocumentAndDepartments(documentIds, chunkIndexes, scope.departmentIds());
        //建立哈希表方便用唯一索引查找对应
        Map<String,DocumentChunk>chunkByKey=new HashMap<>();
        for(DocumentChunk chunk:chunks){
            String key=chunk.getDocument().getId()+"_"+chunk.getChunkIndex();
            chunkByKey.put(key,chunk);
        }
        List<RetrievalHit>hits=new ArrayList<>();
        //提取需要的chunk的document
        for(FusedRetrievalCandidate candidate:candidates){
            String key=candidate.getDocumentId()+"_"+candidate.getChunkIndex();
            if(chunkByKey.get(key)==null)continue;
            else{
                DocumentChunk chunk=chunkByKey.get(key);
                RetrievalHit hit=new RetrievalHit(candidate.getDocumentId(),chunk.getId(),candidate.getChunkIndex(),
                                                chunk.getDocument().getTitle(),chunk.getContent(),candidate.getFusionScore()
                                                    ,candidate.getSources());
                hits.add(hit);                    
            }
        }
        return hits;
    }
    
}
