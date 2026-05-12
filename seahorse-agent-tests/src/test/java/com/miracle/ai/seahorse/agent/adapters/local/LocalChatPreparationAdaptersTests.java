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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalChatPreparationAdaptersTests {

    @Test
    void shouldSplitQuestionWithoutCallingModel() {
        LocalQueryRewriteAdapter adapter = new LocalQueryRewriteAdapter();

        RewriteResult result = adapter.rewriteWithSplit("如何入库？如何检索？", List.of());

        assertThat(result.rewrittenQuestion()).isEqualTo("如何入库？如何检索？");
        assertThat(result.subQuestions()).containsExactly("如何入库", "如何检索");
    }

    @Test
    void shouldCreateSubQuestionIntentForGlobalRetrieval() {
        LocalIntentResolutionAdapter adapter = new LocalIntentResolutionAdapter();

        List<SubQuestionIntent> intents = adapter.resolve(new RewriteResult("如何检索？", List.of("如何检索")));

        assertThat(intents).hasSize(1);
        assertThat(intents.get(0).subQuestion()).isEqualTo("如何检索");
        assertThat(intents.get(0).intentScores()).isEmpty();
        assertThat(adapter.isSystemOnly(intents.get(0).intentScores())).isFalse();
        assertThat(adapter.mergeIntentGroup(intents)).satisfies(group -> {
            assertThat(group.kbIntents()).isEmpty();
            assertThat(group.mcpIntents()).isEmpty();
        });
    }
}
