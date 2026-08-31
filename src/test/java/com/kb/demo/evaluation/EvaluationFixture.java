package com.kb.demo.evaluation;

import com.kb.demo.dto.ElasticsearchChunkDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The evaluation corpus is deliberately small and self-cleaning. Logical names and
 * section names are the stable gold; 970xxx IDs are only temporary index addresses.
 */
final class EvaluationFixture {
    static final String VERSION = "nexus-evaluation-fixture-v2";
    static final Set<Long> DOCUMENT_IDS = Set.of(970001L, 970002L, 970003L, 970004L, 970005L, 970006L);

    private final List<FixtureChunk> chunks;
    private final Map<String, FixtureChunk> chunksByReference;
    private final Map<String, Long> documentIdsByLogicalId;

    private EvaluationFixture(List<FixtureChunk> chunks) {
        this.chunks = List.copyOf(chunks);
        this.chunksByReference = new LinkedHashMap<>();
        this.documentIdsByLogicalId = new LinkedHashMap<>();
        for (FixtureChunk chunk : chunks) {
            chunksByReference.put(key(chunk.documentLogicalId(), chunk.sectionId()), chunk);
            documentIdsByLogicalId.put(chunk.documentLogicalId(), chunk.documentId());
        }
    }

    static EvaluationFixture current() {
        return new EvaluationFixture(List.of(
                chunk(970001L, 0, "leave-policy", "annual-leave",
                        "员工申请年假需至少提前3个工作日在OA系统提交，并由直属主管审批。"),
                chunk(970001L, 1, "leave-policy", "sick-leave",
                        "病假超过1天需上传医院证明，病假不扣除年假额度。"),
                chunk(970002L, 0, "travel-expense", "submission-deadline",
                        "差旅报销应在行程结束后30天内提交，逾期需部门负责人补充说明。"),
                chunk(970002L, 1, "travel-expense", "large-expense-approval",
                        "单笔费用超过5000元时，需要部门负责人和财务负责人两级审批。"),
                chunk(970003L, 0, "database-change", "normal-change",
                        "生产数据库变更必须提交工单，经过研发负责人和DBA双人审核后方可执行。"),
                chunk(970003L, 1, "database-change", "emergency-change",
                        "紧急变更应先电话通知值班负责人，执行后24小时内补齐工单和复盘记录。"),
                chunk(970004L, 0, "account-security", "password-lock",
                        "连续5次输入错误密码会锁定企业账号30分钟。"),
                chunk(970004L, 1, "account-security", "vpn-troubleshooting",
                        "VPN登录失败时先确认企业账号未锁定，再检查网络并重新获取验证码。"),
                chunk(970005L, 0, "procurement", "contract-review",
                        "采购合同金额超过10万元时，必须完成法务审查并由采购负责人审批。"),
                chunk(970005L, 1, "procurement", "supplier-onboarding",
                        "新增供应商须提交营业执照、银行账户信息和廉洁承诺书，采购专员完成准入登记。"),
                chunk(970006L, 0, "compensation-confidential", "salary-access",
                        "薪酬调整方案仅限人力资源部和财务部授权人员查阅，不得向其他部门传播。"),
                chunk(970006L, 1, "compensation-confidential", "bonus-approval",
                        "年度奖金预算由人力资源部拟定，财务复核后提交总经理审批。")
        ));
    }

    void index(EvaluationRedisVectorSearch redisVectorSearch,
            com.kb.demo.service.ElasticsearchSearchService elasticsearchSearchService) throws Exception {
        redisVectorSearch.create();
        redisVectorSearch.index(chunks);
        List<ElasticsearchChunkDocument> elasticsearchDocuments = new ArrayList<>();
        for (FixtureChunk chunk : chunks) {
            elasticsearchDocuments.add(new ElasticsearchChunkDocument(chunk.documentId(), chunk.chunkIndex(), chunk.content()));
        }
        elasticsearchSearchService.indexChunks(elasticsearchDocuments);
        elasticsearchSearchService.refreshIndex();
    }

    FixtureChunk resolve(StableChunkReference reference) {
        FixtureChunk chunk = chunksByReference.get(key(reference.documentLogicalId(), reference.sectionId()));
        if (chunk == null) {
            throw new IllegalArgumentException("Unknown fixture reference: " + reference);
        }
        return chunk;
    }

    Long documentId(String logicalId) {
        Long documentId = documentIdsByLogicalId.get(logicalId);
        if (documentId == null) {
            throw new IllegalArgumentException("Unknown fixture document: " + logicalId);
        }
        return documentId;
    }

    String logicalDocumentId(Long documentId) {
        return documentIdsByLogicalId.entrySet().stream()
                .filter(entry -> entry.getValue().equals(documentId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("runtime-document-" + documentId);
    }

    String sectionId(Long documentId, Integer chunkIndex) {
        return chunks.stream()
                .filter(chunk -> chunk.documentId().equals(documentId) && chunk.chunkIndex().equals(chunkIndex))
                .map(FixtureChunk::sectionId)
                .findFirst()
                .orElse("runtime-chunk-" + chunkIndex);
    }

    List<FixtureChunk> chunks() {
        return chunks;
    }

    private static FixtureChunk chunk(Long documentId, Integer chunkIndex, String documentLogicalId,
            String sectionId, String content) {
        return new FixtureChunk(documentId, chunkIndex, documentLogicalId, sectionId, content);
    }

    private static String key(String documentLogicalId, String sectionId) {
        return documentLogicalId + "#" + sectionId;
    }

    record FixtureChunk(Long documentId, Integer chunkIndex, String documentLogicalId, String sectionId, String content) {
    }
}
