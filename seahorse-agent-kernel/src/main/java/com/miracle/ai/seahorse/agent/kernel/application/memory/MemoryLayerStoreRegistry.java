package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;

import java.util.Objects;

/**
 * 三层 memory store 聚合，封装 {@link MemoryLayer} → {@link MemoryStorePort} 的分发。
 * 取代 {@code DefaultMemoryEnginePort} 中重复出现的 layer→port if/switch 分支。
 */
public record MemoryLayerStoreRegistry(
        ShortTermMemoryPort shortTerm,
        LongTermMemoryPort longTerm,
        SemanticMemoryPort semantic) {

    public MemoryLayerStoreRegistry {
        Objects.requireNonNull(shortTerm, "shortTerm must not be null");
        Objects.requireNonNull(longTerm, "longTerm must not be null");
        Objects.requireNonNull(semantic, "semantic must not be null");
    }

    /**
     * 按 {@link MemoryLayer} 返回对应的 store。{@code null} 与 {@link MemoryLayer#WORKING}
     * 均回退到 {@link MemoryLayer#SHORT_TERM}，与 facade 历史语义一致。
     */
    public MemoryStorePort storeFor(MemoryLayer layer) {
        MemoryLayer safeLayer = layer == null ? MemoryLayer.SHORT_TERM : layer;
        return switch (safeLayer) {
            case LONG_TERM -> longTerm;
            case SEMANTIC -> semantic;
            case WORKING, SHORT_TERM -> shortTerm;
        };
    }
}
