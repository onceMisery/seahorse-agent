/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;

import java.util.List;
import java.util.Objects;

/** Executes a final model turn through the kernel's canonical model-request owner. */
public interface AgentFinalModelTurnPort {

    FinalModelTurnResult requestFinalModelTurn(AgentLoopRequest request, List<ChatMessage> messages);

    default FinalModelTurnResult requestFinalModelTurn(
            AgentLoopRequest request,
            List<ChatMessage> messages,
            TraceRunScope traceRunScope) {
        return requestFinalModelTurn(request, messages);
    }

    record FinalModelTurnResult(String content, String safeEvidenceJson) {

        public FinalModelTurnResult {
            content = Objects.requireNonNullElse(content, "");
            safeEvidenceJson = Objects.requireNonNullElse(safeEvidenceJson, "");
        }
    }

    final class FinalModelTurnException extends RuntimeException {

        private final String safeEvidenceJson;

        public FinalModelTurnException(RuntimeException cause, String safeEvidenceJson) {
            super(ModelFailureSanitizer.safeMessage(cause), cause);
            this.safeEvidenceJson = Objects.requireNonNullElse(safeEvidenceJson, "");
        }

        public String safeEvidenceJson() {
            return safeEvidenceJson;
        }
    }
}
