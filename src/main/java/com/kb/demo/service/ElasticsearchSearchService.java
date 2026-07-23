package com.kb.demo.service;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;

import org.springframework.stereotype.Service;
import com.kb.demo.dto.ElasticsearchChunkDocument;
import com.kb.demo.dto.RetrievalCandidate;
import com.kb.demo.dto.RetrievalSource;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;


@Service
public class ElasticsearchSearchService {
    private final ElasticsearchClient elasticsearchClient;
    private static final String INDEX_NAME = "document-chunks";

    public ElasticsearchSearchService(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * 幂等判断创建索引
     * @throws IOException
     */
    public void createIndexIfAbsent()throws IOException{
        boolean exists=elasticsearchClient.indices()
                        .exists(e->e.index(INDEX_NAME))
                        .value();
        if(exists)return;
        elasticsearchClient.indices().create(c->c
                                            .index(INDEX_NAME)
                                            .mappings(m->m
                                                        .properties("documentId",p->p.long_(l->l))
                                                        .properties("chunkIndex",p->p.integer(i->i))
                                                        .properties("content",p->p.text(t->t))
                                            )   
        );
    }
    
    /**
     * 单条chunk入索引
     * @param document 文档
     * @throws IOException
     */
    public void indexChunk(ElasticsearchChunkDocument document) throws IOException{
        createIndexIfAbsent();

        elasticsearchClient.index(i->i
                                    .index(INDEX_NAME)
                                    .id(document.getDocumentId()+"_"+document.getChunkIndex())
                                    .document(document)
        );
    }

    /**
     * 批量chunk入索引
     * @param documents
     * @throws IOException
     */
    public void indexChunks(List<ElasticsearchChunkDocument> documents)throws IOException{
        if(documents.size()==0)return;
        createIndexIfAbsent();

    }


    /**
     * 刷新文档
     * 连续写入多条chunk统一refresh一次
     * @throws IOException
     */
    public void refreshIndex()throws IOException{
        elasticsearchClient.indices()
                            .refresh(r->r.index(INDEX_NAME)); //设置要刷新的索引
    }

    /**
     * Bm25检索候选
     * @param query 查询语句
     * @param maxResults 最大结果数
     * @return 查询结果（统一包装）
     * @throws IOException
     */
    public List<RetrievalCandidate> searchBm25Candidates(String query,int maxResults)throws IOException{
        createIndexIfAbsent();
        SearchResponse<ElasticsearchChunkDocument> response=elasticsearchClient
                                                            .search(request->request
                                                                            .index(INDEX_NAME)
                                                                            .size(maxResults)
                                                                            .query(queryBuilder->queryBuilder
                                                                                                .match(match->match
                                                                                                            .field("content")
                                                                                                            .query(query)
                                                                                                )
                                                                            ),ElasticsearchChunkDocument.class
                                                            );
        /* 
        SearchResponse
        └── HitsMetadata
            ├── total
            ├── maxScore
            └── List<Hit<ElasticsearchChunkDocument>> 两次hits
            */
        List<Hit<ElasticsearchChunkDocument>> hits=response.hits().hits();
        List<RetrievalCandidate> candidates=new ArrayList<>();
        for (int index = 0; index < hits.size(); index++) {
            Hit<ElasticsearchChunkDocument> hit=hits.get(index);
            ElasticsearchChunkDocument chunkDocument=hit.source();
            Double rawScore=hit.score();
            if(chunkDocument==null||chunkDocument.getDocumentId()==null||chunkDocument.getChunkIndex()==null||rawScore==null)continue;
            //rank持续加一
            int rank=candidates.size()+1;
            RetrievalCandidate candidate=new RetrievalCandidate(chunkDocument.getDocumentId(),chunkDocument.getChunkIndex(), rawScore, rank, RetrievalSource.ELASTICSEARCH_BM25);
            candidates.add(candidate);
        }
        return candidates;
    }

}
