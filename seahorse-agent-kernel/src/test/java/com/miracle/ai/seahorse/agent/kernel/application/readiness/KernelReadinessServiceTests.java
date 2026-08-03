/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */
package com.miracle.ai.seahorse.agent.kernel.application.readiness;

import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessCheck;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.readiness.ReadinessProbePort;
import com.miracle.ai.seahorse.agent.ports.outbound.readiness.ReadinessProbePort.ComponentStatus;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelReadinessServiceTests {

    @Test
    void unknownProductModeBlocksReadiness() {
        ReadinessSummary summary = new KernelReadinessService("unknown", probe(coreComponents(), Map.of()))
                .getSummary();

        assertEquals(ReadinessSummary.OverallStatus.BLOCKED, summary.overall());
        assertEquals(ReadinessCheck.Severity.ERROR, check(summary, "feature.flags").severity());
    }

    @Test
    void probeFailureProducesStableBlockedSummary() {
        ReadinessProbePort probe = new ReadinessProbePort() {
            @Override
            public Map<String, ComponentStatus> probeComponents() {
                throw new IllegalStateException("database probe failed");
            }

            @Override
            public Map<String, String> adapterTypes() {
                return Map.of();
            }
        };

        ReadinessSummary summary = new KernelReadinessService("demo", probe).getSummary();

        assertEquals(ReadinessSummary.OverallStatus.BLOCKED, summary.overall());
        assertTrue(check(summary, "readiness.probe").message().contains("database probe failed"));
    }

    @Test
    void ragTreatsMissingSearchDependenciesAsBlocking() {
        Map<String, ComponentStatus> components = coreComponents();
        components.put("vector-store", ComponentStatus.unavailable("missing"));
        components.put("keyword-search", ComponentStatus.unavailable("missing"));

        ReadinessSummary summary = new KernelReadinessService("rag", probe(components, Map.of(
                "vector-store", "pgvector",
                "keyword-search", "jdbc"))).getSummary();

        assertEquals(ReadinessSummary.OverallStatus.BLOCKED, summary.overall());
        assertEquals(ReadinessCheck.Severity.ERROR, check(summary, "vector.store").severity());
        assertEquals(ReadinessCheck.Severity.ERROR, check(summary, "search.keyword").severity());
    }

    @Test
    void demoCanRunWithoutOptionalSearchAndStorage() {
        Map<String, ComponentStatus> components = coreComponents();
        components.put("vector-store", ComponentStatus.unavailable("disabled"));
        components.put("keyword-search", ComponentStatus.unavailable("disabled"));
        components.put("storage", ComponentStatus.unavailable("disabled"));

        ReadinessSummary summary = new KernelReadinessService("demo", probe(components, Map.of(
                "vector-store", "noop",
                "keyword-search", "noop",
                "storage", "noop"))).getSummary();

        assertNotEquals(ReadinessSummary.OverallStatus.BLOCKED, summary.overall());
    }

    @Test
    void noopVectorIsNotReportedHealthyInRag() {
        Map<String, ComponentStatus> components = coreComponents();
        components.put("vector-store", ComponentStatus.available("noop"));

        ReadinessSummary summary = new KernelReadinessService("rag", probe(components, Map.of(
                "vector-store", "noop"))).getSummary();

        assertEquals(ReadinessCheck.Status.FAILED, check(summary, "vector.store").status());
        assertEquals(ReadinessCheck.Severity.ERROR, check(summary, "vector.store").severity());
    }

    private static ReadinessCheck check(ReadinessSummary summary, String id) {
        return summary.checks().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }

    private static ReadinessProbePort probe(Map<String, ComponentStatus> components,
                                            Map<String, String> adapterTypes) {
        return new ReadinessProbePort() {
            @Override
            public Map<String, ComponentStatus> probeComponents() {
                return components;
            }

            @Override
            public Map<String, String> adapterTypes() {
                return adapterTypes;
            }
        };
    }

    private static Map<String, ComponentStatus> coreComponents() {
        Map<String, ComponentStatus> components = new HashMap<>();
        ComponentStatus available = ComponentStatus.available("test");
        components.put("database", available);
        components.put("db.migration", available);
        components.put("auth.default-admin", available);
        components.put("chat-model", available);
        components.put("embedding-model", available);
        components.put("embedding.dimension", ComponentStatus.available("unknown"));
        components.put("vector-store", available);
        components.put("keyword-search", available);
        components.put("cache", available);
        components.put("mq", available);
        components.put("storage", available);
        return components;
    }
}
