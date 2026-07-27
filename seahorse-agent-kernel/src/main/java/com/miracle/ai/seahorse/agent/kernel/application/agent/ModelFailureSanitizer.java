/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent;

/** Produces prompt-free failure summaries for model-boundary persistence and telemetry. */
public final class ModelFailureSanitizer {

    private static final int MAX_CAUSE_DEPTH = 16;
    private static final String MODEL_TURN_TIMEOUT_PREFIX = "Model streaming call timed out after ";

    private ModelFailureSanitizer() {
    }

    public static boolean isModelFailure(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof ModelTurnExecutionException
                    || current instanceof AgentFinalModelTurnPort.FinalModelTurnException
                    || current instanceof ModelContextEnvelopeException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static String safeMessage(Throwable failure) {
        ModelContextEnvelopeException envelopeFailure = findEnvelopeFailure(failure);
        if (envelopeFailure != null) {
            return "MODEL_CONTEXT_ENVELOPE_FAILED:" + envelopeFailure.reasonCode();
        }
        return "MODEL_TURN_FAILED:" + safeType(rootCause(failure));
    }

    static boolean isModelTurnTimeout(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            String message = current.getMessage();
            if (message != null && message.startsWith(MODEL_TURN_TIMEOUT_PREFIX)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static ModelContextEnvelopeException findEnvelopeFailure(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof ModelContextEnvelopeException envelopeFailure) {
                return envelopeFailure;
            }
            current = current.getCause();
        }
        return null;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        Throwable root = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            root = current;
            current = current.getCause();
        }
        return root;
    }

    private static String safeType(Throwable failure) {
        if (failure == null) {
            return "RuntimeException";
        }
        String simpleName = failure.getClass().getSimpleName();
        return simpleName == null || simpleName.isBlank() ? "RuntimeException" : simpleName;
    }
}
