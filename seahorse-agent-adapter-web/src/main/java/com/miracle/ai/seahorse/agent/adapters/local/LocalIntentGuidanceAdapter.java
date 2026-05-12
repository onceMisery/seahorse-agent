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

package com.miracle.ai.seahorse.agent.adapters.local;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.GuidanceDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;

import java.util.List;

/**
 * 本地澄清引导适配器。
 *
 * <p>默认实现不阻断问答流程。需要大模型歧义检测时，可通过专用 adapter 覆盖该端口。
 */
public class LocalIntentGuidanceAdapter implements IntentGuidancePort {

    @Override
    public GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents) {
        return GuidanceDecision.none();
    }
}
