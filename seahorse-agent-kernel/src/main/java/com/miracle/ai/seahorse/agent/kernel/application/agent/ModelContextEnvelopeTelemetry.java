/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeScope;

import java.util.LinkedHashMap;
import java.util.Map;

final class ModelContextEnvelopeTelemetry {

    private ModelContextEnvelopeTelemetry() {
    }

    static void record(
            KernelRagTraceRecorder traceRecorder,
            TraceNodeScope modelScope,
            ModelContextEnvelopeEvidence evidence) {
        if (evidence == null) {
            return;
        }
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("seahorse.context.payload_hash", evidence.payloadHash());
        attributes.put("seahorse.context.payload_hash_source", evidence.payloadHashSource());
        attributes.put("seahorse.context.model_id", evidence.modelId());
        attributes.put("seahorse.context.mode", evidence.mode().name());
        attributes.put("seahorse.context.estimator_mode", evidence.estimatorMode().name());
        attributes.put("seahorse.context.estimator_version", evidence.estimatorVersion());
        attributes.put("seahorse.context.window", Integer.toString(evidence.contextWindow()));
        attributes.put("seahorse.context.window_source", evidence.contextWindowSource());
        attributes.put("seahorse.context.output_reserve", Integer.toString(evidence.outputReserve()));
        attributes.put("seahorse.context.safety_buffer", Integer.toString(evidence.safetyBuffer()));
        attributes.put("seahorse.context.effective_window", Long.toString(evidence.effectiveWindow()));
        attributes.put("seahorse.context.selected_input_tokens", Long.toString(evidence.selectedInputTokens()));
        attributes.put("seahorse.context.remaining_tokens", Long.toString(evidence.remainingTokens()));
        boolean providerUsageAvailable = evidence.providerInputTokens() != null
                && evidence.providerOutputTokens() != null;
        attributes.put("seahorse.context.provider_usage_available", Boolean.toString(providerUsageAvailable));
        if (providerUsageAvailable) {
            attributes.put("seahorse.context.provider_input_tokens", Long.toString(evidence.providerInputTokens()));
            attributes.put("seahorse.context.provider_output_tokens", Long.toString(evidence.providerOutputTokens()));
            if (evidence.estimatorDeltaTokens() != null) {
                attributes.put(
                        "seahorse.context.estimator_delta_tokens",
                        Long.toString(evidence.estimatorDeltaTokens()));
            }
        }
        attributes.put("seahorse.context.reason_code", evidence.reasonCode());
        attributes.forEach((key, value) -> traceRecorder.recordNodeAttribute(modelScope, key, value));
    }

    static ModelContextEnvelopeEvidence from(Throwable error) {
        if (error instanceof ModelContextEnvelopeException envelopeException) {
            return envelopeException.evidence();
        }
        if (error instanceof ModelTurnExecutionException executionException) {
            return executionException.evidence();
        }
        return null;
    }

    static String toJson(ModelContextEnvelopeEvidence evidence) {
        return evidence == null ? null : evidence.toJson();
    }
}
