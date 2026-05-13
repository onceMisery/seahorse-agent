package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

public interface MetadataExtractionResultRepositoryPort {

    void save(MetadataExtractionRecord record);

    static MetadataExtractionResultRepositoryPort noop() {
        return record -> {
        };
    }
}
