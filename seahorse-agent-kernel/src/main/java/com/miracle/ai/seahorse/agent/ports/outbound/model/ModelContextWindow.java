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

package com.miracle.ai.seahorse.agent.ports.outbound.model;

import java.util.Objects;

/**
 * Model context-window capability and its auditable source.
 */
public record ModelContextWindow(int tokens, String source, boolean resolved) {

    public ModelContextWindow(int tokens, String source) {
        this(tokens, source, true);
    }

    public ModelContextWindow {
        if (resolved && tokens <= 0) {
            throw new IllegalArgumentException("model context window tokens must be > 0");
        }
        if (!resolved) {
            tokens = 0;
        }
        source = Objects.requireNonNullElse(source, "unknown").trim();
        if (source.isBlank()) {
            source = "unknown";
        }
    }

    public static ModelContextWindow unknown(String source) {
        return new ModelContextWindow(0, source, false);
    }
}
