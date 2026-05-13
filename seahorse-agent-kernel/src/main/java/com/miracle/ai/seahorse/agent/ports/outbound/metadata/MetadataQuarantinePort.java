package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

public interface MetadataQuarantinePort {

    void quarantine(MetadataQuarantineItem item);

    static MetadataQuarantinePort noop() {
        return item -> {
        };
    }
}
