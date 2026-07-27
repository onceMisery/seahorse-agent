/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import java.time.Instant;

/** Durable at-most-once claim for side-effecting tool execution. */
public interface ToolInvocationIdempotencyPort {

    boolean tryClaim(String tenantId, String idempotencyKey, Instant now);

    void markCompleted(String tenantId, String idempotencyKey, Instant completedAt);

    static ToolInvocationIdempotencyPort noop() {
        return new ToolInvocationIdempotencyPort() {
            @Override
            public boolean tryClaim(String tenantId, String idempotencyKey, Instant now) {
                return true;
            }

            @Override
            public void markCompleted(String tenantId, String idempotencyKey, Instant completedAt) {
            }
        };
    }
}
