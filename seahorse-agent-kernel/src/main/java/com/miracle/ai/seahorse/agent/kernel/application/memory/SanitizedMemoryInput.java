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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import java.util.List;
import java.util.Objects;

record SanitizedMemoryInput(String content, boolean rejected, String reason, List<String> signals) {

    SanitizedMemoryInput {
        content = Objects.requireNonNullElse(content, "").trim();
        reason = Objects.requireNonNullElse(reason, "").trim();
        signals = List.copyOf(Objects.requireNonNullElse(signals, List.of()));
    }

    static SanitizedMemoryInput accepted(String content, List<String> signals) {
        return new SanitizedMemoryInput(content, false, "", signals);
    }

    static SanitizedMemoryInput rejected(String content, String reason, List<String> signals) {
        return new SanitizedMemoryInput(content, true, reason, signals);
    }
}
