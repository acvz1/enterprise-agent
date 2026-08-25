# Retrieval Threshold Evaluation

1. Copy `retrieval-threshold-dataset.example.json` to a local path outside Git, then replace the example IDs with chunks that actually exist in the current Redis and Elasticsearch indexes.
2. Each query must label every relevant chunk with `documentId + chunkIndex`.
3. Run:

```powershell
.\mvnw.cmd -Dit.test=RetrievalThresholdEvaluationIT `
  -Dretrieval.eval.dataset=D:\data\retrieval-threshold-dataset.json verify
```

The runner only reads existing indexes. It writes `redis-vector.csv`, `elasticsearch-bm25.csv`, `threshold-metrics.json`, and `raw-candidates.json` under `target/retrieval-threshold-evaluation/<timestamp>/`.

Useful optional parameters:

```powershell
-Dretrieval.eval.candidate-limit=50
-Dretrieval.eval.sweep-points=21
-Dretrieval.eval.redis-host=localhost
-Dretrieval.eval.redis-port=6379
-Dretrieval.eval.elasticsearch-port=9200
-Dretrieval.eval.output-dir=D:\reports\threshold-eval
```
