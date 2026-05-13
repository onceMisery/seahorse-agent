package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.Optional;

public interface MetadataDictionaryPort {

    Optional<String> canonicalValue(String tenantId, String dictionaryCode, String rawValue);

    static MetadataDictionaryPort noop() {
        return (tenantId, dictionaryCode, rawValue) -> Optional.empty();
    }
}
