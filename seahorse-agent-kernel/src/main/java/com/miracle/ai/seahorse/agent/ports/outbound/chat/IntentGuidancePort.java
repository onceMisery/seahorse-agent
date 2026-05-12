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

package com.miracle.ai.seahorse.agent.ports.outbound.chat;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.GuidanceDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;

import java.util.List;

/**
 * 歧义引导端口。
 */
public interface IntentGuidancePort {

    /**
     * 检测是否需要引导用户澄清。
     *
     * @param question   改写后的问题
     * @param subIntents 子问题意图列表
     * @return 引导决策
     */
    GuidanceDecision detectAmbiguity(String question, List<SubQuestionIntent> subIntents);

    static IntentGuidancePort none() {
        return (question, subIntents) -> GuidanceDecision.none();
    }
}
