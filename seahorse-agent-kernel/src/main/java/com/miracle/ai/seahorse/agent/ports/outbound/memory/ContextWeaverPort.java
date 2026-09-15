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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;

public interface ContextWeaverPort {

    String weave(MemoryContext context, ContextBudget budget);

    default String weave(ContextPack contextPack, ContextBudget budget) {
        return "";
    }

    /**
     * 合并两路上下文时的默认策略：ContextPack 优先，legacy 记忆仅作回退。
     *
     * <p>实现若覆盖此方法，必须保持这一优先级语义。不要在同一段提示里拼接两路来源：
     * ContextPack 段落的收尾语要求模型“只使用下列 ContextPack 条目作为授权运行时上下文”，
     * 而 legacy {@code MemoryItem} 既没有 sensitivity / aclDecision，也没有 citation，
     * 紧随其后追加它们会让该指令自相矛盾；同时 Profile KV、Correction Ledger 等强事实
     * 已由 {@code ContextPackRuntimeAssembler} 带 ResourceRef 与 citation 提升进 ContextPack，
     * 再拼一次只会让同一份内容以有归属和无归属两种形态重复出现。
     *
     * <p>当 ContextPack 没有贡献任何可用条目（缺失、全为 SECRET、内容全空白或被预算淘汰）时，
     * 回退到 legacy 记忆，保证画像强事实不会因包为空而整体丢失。
     */
    default String weave(ContextPack contextPack, MemoryContext memoryContext, ContextBudget budget) {
        String contextPackText = weave(contextPack, budget);
        if (contextPackText != null && !contextPackText.isBlank()) {
            return contextPackText;
        }
        return weave(memoryContext, budget);
    }
}
