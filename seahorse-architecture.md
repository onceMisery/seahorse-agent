# Seahorse Agent Architecture

## Overview
Seahorse Agent is a RAG (Retrieval Augmented Generation) intelligent agent platform built on Spring Boot 3.5.7, using hexagonal architecture (Ports and Adapters pattern).

## Hexagonal Architecture

### Core Layer (kernel)
The domain core contains business logic:
- KernelChatService: Chat orchestration
- KernelKnowledgeBaseService: Knowledge base management
- KernelAgentRunService: Agent execution

### Adapter Layer
- **Web Adapter**: REST API endpoints
- **AI Adapter**: OpenAI-compatible integration (Ollama)
- **Vector Adapter**: pgvector for semantic search
- **Storage Adapter**: MinIO/Local file storage
- **Cache Adapter**: Redis caching
- **MQ Adapter**: Pulsar message queue

### Port Pattern
Ports define interfaces between layers:
- **Inbound Ports**: ChatPort, KnowledgeBasePort (use cases)
- **Outbound Ports**: ChatModelPort, VectorStorePort (infrastructure)

## RAG Workflow

1. **Document Upload**: User uploads document to knowledge base
2. **Parsing**: Tika adapter extracts text
3. **Chunking**: Split text into 500-token chunks
4. **Vectorization**: Ollama nomic-embed-text generates 768-dim vectors
5. **Storage**: Vectors stored in pgvector with HNSW index
6. **Query**: User asks question
7. **Retrieval**: Generate query vector, search top-k similar chunks
8. **Augmentation**: Combine retrieved context with user question
9. **Generation**: LLM generates answer using augmented prompt
10. **Streaming**: Response streamed back via SSE

## Key Features

- **Multi-tenancy**: Row-level security with tenant_id
- **Authentication**: sa-token with Bearer token
- **Observability**: Micrometer + Actuator
- **Generation Tools**: Chart, PPT, Image generation
- **Skills**: 21 public skills for various tasks
