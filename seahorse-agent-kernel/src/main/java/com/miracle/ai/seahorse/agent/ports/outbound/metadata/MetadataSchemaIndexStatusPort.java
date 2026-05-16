package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Objects;

public interface MetadataSchemaIndexStatusPort {

    void recordSyncResult(MetadataSchemaIndexSyncStatusRecord status);

    static MetadataSchemaIndexStatusPort noop() {
        return status -> Objects.requireNonNull(status, "status must not be null");
    }
}
