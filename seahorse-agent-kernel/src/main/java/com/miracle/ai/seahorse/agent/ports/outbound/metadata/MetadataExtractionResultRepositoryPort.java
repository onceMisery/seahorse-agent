package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

public interface MetadataExtractionResultRepositoryPort {

    void save(MetadataExtractionRecord record);

    default boolean hasAcceptedResult(String tenantId,
                                      String knowledgeBaseId,
                                      String documentId,
                                      int schemaVersion,
                                      String extractorVersion) {
        return false;
    }

    static MetadataExtractionResultRepositoryPort noop() {
        return record -> {
        };
    }
}
