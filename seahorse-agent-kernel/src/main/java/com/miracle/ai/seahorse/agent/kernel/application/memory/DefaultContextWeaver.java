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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.MemoryPromptFormatter;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;

import java.util.Objects;

public class DefaultContextWeaver implements ContextWeaverPort {

    public DefaultContextWeaver() {
    }

    @Override
    public String weave(MemoryContext context, ContextBudget budget) {
        String prompt = MemoryPromptFormatter.format(context);
        int maxChars = Objects.requireNonNullElseGet(budget, ContextBudget::defaults).maxChars();
        if (prompt.length() <= maxChars) {
            return prompt;
        }
        return prompt.substring(0, maxChars);
    }
}
