/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent;

final class ModelTurnExecutionException extends AgentLoopException {

    private final ModelContextEnvelopeEvidence evidence;

    ModelTurnExecutionException(RuntimeException cause, ModelContextEnvelopeEvidence evidence) {
        super(ModelFailureSanitizer.safeMessage(cause), cause);
        this.evidence = evidence;
    }

    ModelContextEnvelopeEvidence evidence() {
        return evidence;
    }
}
