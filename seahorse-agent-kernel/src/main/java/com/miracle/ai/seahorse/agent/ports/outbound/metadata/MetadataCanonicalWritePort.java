package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Map;

public interface MetadataCanonicalWritePort {

    void writeDocumentMetadata(String documentId, Map<String, Object> acceptedMetadata);

    static MetadataCanonicalWritePort noop() {
        return (documentId, acceptedMetadata) -> {
        };
    }
}
