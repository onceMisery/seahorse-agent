package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

public interface MetadataReviewQueuePort {

    void enqueue(MetadataReviewItem item);

    static MetadataReviewQueuePort noop() {
        return item -> {
        };
    }
}
