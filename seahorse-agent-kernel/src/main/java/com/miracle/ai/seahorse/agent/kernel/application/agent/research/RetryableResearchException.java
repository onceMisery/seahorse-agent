/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

/**
 * 可重试的研究步骤异常。抛出此异常时编排器会安排延迟重试。
 */
public class RetryableResearchException extends RuntimeException {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final Class<?>[] NON_RETRYABLE_TYPES = {
            SecurityException.class,
            IllegalArgumentException.class
    };

    public RetryableResearchException(String message) {
        super(message);
    }

    public RetryableResearchException(String message, Throwable cause) {
        super(message, cause);
    }

    public boolean shouldRetry(int attemptCount) {
        return shouldRetry(attemptCount, DEFAULT_MAX_ATTEMPTS);
    }

    public boolean shouldRetry(int attemptCount, int maxAttempts) {
        if (attemptCount >= Math.max(1, maxAttempts)) {
            return false;
        }
        Throwable current = this;
        while (current != null) {
            for (Class<?> type : NON_RETRYABLE_TYPES) {
                if (type.isInstance(current)) {
                    return false;
                }
            }
            current = current.getCause();
        }
        return true;
    }
}
