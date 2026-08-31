# Retrieval Evaluation 2.0

`retrieval-evaluation-v2.json` is human-reviewed gold for the self-cleaning `EvaluationFixture`.
Gold uses `documentLogicalId + sectionId`, not a database auto-increment chunk ID.

Run the real Redis/Elasticsearch evaluation without triggering unrelated Surefire tests:

```powershell
mvn.cmd failsafe:integration-test failsafe:verify -Dit.test=RetrievalEvaluationV2IT
```

It writes JSON and Markdown to `target/retrieval-evaluation-v2/<run-id>/`, then deletes only
the temporary `970001`–`970006` fixture entries. Defaults mirror the current production
configuration: vector minimum score `0.72`, BM25 minimum score `10.0`, candidate limit `20`,
and evaluation TopK `3`.

To compare a new run with an old JSON report:

```powershell
mvn.cmd failsafe:integration-test failsafe:verify -Dit.test=RetrievalEvaluationV2IT -Devaluation.baseline=<old-report.json>
```

The runner never changes production retrieval parameters. Overrides are evaluation-run metadata:
`evaluation.min-vector-score`, `evaluation.min-bm25-score`, `evaluation.candidate-limit`,
`evaluation.redis-port`, `evaluation.elasticsearch-port`, and `evaluation.output-dir`.

The permission cases exercise the same Redis/ES `allowedDocumentIds` filtering used by production
retrieval. They do not yet create MySQL users/departments or exercise `SecurityContextHolder`; that
HTTP-to-ACL resolution remains a separate integration boundary.

## Legacy Threshold Evaluation

1. Copy `retrieval-threshold-dataset.example.json` to a local path outside Git, then replace the
   example IDs with chunks that actually exist in the current Redis and Elasticsearch indexes.
2. Each query must label every relevant chunk with `documentId + chunkIndex`.
3. Run:

```powershell
.\mvnw.cmd -Dit.test=RetrievalThresholdEvaluationIT `
  -Dretrieval.eval.dataset=D:\data\retrieval-threshold-dataset.json verify
```

The legacy runner only reads existing indexes. It writes `redis-vector.csv`, `elasticsearch-bm25.csv`,
`threshold-metrics.json`, and `raw-candidates.json` under `target/retrieval-threshold-evaluation/<timestamp>/`.

Useful optional parameters:

```powershell
-Dretrieval.eval.candidate-limit=50
-Dretrieval.eval.sweep-points=21
-Dretrieval.eval.redis-host=localhost
-Dretrieval.eval.redis-port=6379
-Dretrieval.eval.elasticsearch-port=9200
-Dretrieval.eval.output-dir=D:\reports\threshold-eval
```
