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

package com.miracle.ai.seahorse.agent.kernel.domain.trace;

import java.util.Objects;

/**
 * Trace run 启动命令。
 */
public record TraceRunStartCommand(String traceName,
                                   String entryMethod,
                                   String conversationId,
                                   String taskId,
                                   String userId) {

    public TraceRunStartCommand {
        traceName = requireText(traceName, "traceName");
        entryMethod = requireText(entryMethod, "entryMethod");
        conversationId = blankToNull(conversationId);
        taskId = blankToNull(taskId);
        userId = blankToNull(userId);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String blankToNull(String value) {
        String safeValue = Objects.requireNonNullElse(value, "");
        return safeValue.isBlank() ? null : safeValue;
    }
}
