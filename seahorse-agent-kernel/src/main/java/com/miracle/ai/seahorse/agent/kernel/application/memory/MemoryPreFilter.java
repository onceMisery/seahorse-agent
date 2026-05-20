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

import java.util.Objects;

class MemoryPreFilter {

    MemoryPreFilterResult filter(String content) {
        String value = Objects.requireNonNullElse(content, "").trim();
        if (value.isBlank()) {
            return MemoryPreFilterResult.ignored("blank");
        }
        if (value.length() < 2) {
            return MemoryPreFilterResult.ignored("too_short");
        }
        if (isLowValueChat(value)) {
            return MemoryPreFilterResult.ignored("no_high_value_signal");
        }
        return MemoryPreFilterResult.allow();
    }

    private boolean isLowValueChat(String content) {
        return content.matches("^(谢谢|感谢|收到|好的|好|嗯|OK|ok|明白|了解)[。！!\\s]*$");
    }
}
