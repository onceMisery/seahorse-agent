package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;

public interface MetadataSchemaRegistryPort {

    MetadataSchema loadSchema(String tenantId, String knowledgeBaseId);

    static MetadataSchemaRegistryPort empty() {
        return MetadataSchema::empty;
    }
}
