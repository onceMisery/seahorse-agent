/*
 * Copyright 2024-2026 the original author or authors.
 * Licensed under the Apache License, Version 2.0.
 */
package com.miracle.ai.seahorse.agent.kernel.domain.task;

/**
 * Seahorse 任务状态。
 */
public enum TaskStatus {

    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    public boolean canTransitionTo(TaskStatus target) {
        if (this.isTerminal()) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == RUNNING || target == CANCELLED;
            case RUNNING -> target == SUCCEEDED || target == FAILED || target == CANCELLED;
            default -> false;
        };
    }
}
