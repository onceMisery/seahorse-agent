# Evidence

## Baseline
- `docker compose ps`: backend, frontend, postgres, redis, elasticsearch, milvus, minio, ollama are running; backend reports healthy. Pulsar broker/bookie report unhealthy.
- `README.md`: default backend `http://localhost:9090`, frontend `http://localhost`, admin credentials `admin / admin123`, chat path `/rag/v3/chat`, personal memory center `/api/me/memories`.
- `seahorse-architecture.md`: RAG workflow routes through kernel, vector/search adapters, and streaming response.

## Pending
- Baseline memory/profile/RAG E2E.
- Focused regression tests if a defect is confirmed.
