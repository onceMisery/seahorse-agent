/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;

import java.util.Objects;

record ModelContextEnvelope(ChatRequest request, ModelContextEnvelopeEvidence evidence) {

    ModelContextEnvelope {
        request = Objects.requireNonNull(request, "request must not be null");
        evidence = Objects.requireNonNull(evidence, "evidence must not be null");
    }
}
