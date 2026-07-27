/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import java.util.Objects;

final class ModelContextEnvelopeException extends AgentLoopException {

    private final String reasonCode;
    private final ModelContextEnvelopeEvidence evidence;

    ModelContextEnvelopeException(String reasonCode, String message, ModelContextEnvelopeEvidence evidence) {
        super(Objects.requireNonNullElse(reasonCode, "CONTEXT_ENVELOPE_FAILED") + ": " + message);
        this.reasonCode = Objects.requireNonNullElse(reasonCode, "CONTEXT_ENVELOPE_FAILED");
        this.evidence = evidence;
    }

    String reasonCode() {
        return reasonCode;
    }

    ModelContextEnvelopeEvidence evidence() {
        return evidence;
    }
}
