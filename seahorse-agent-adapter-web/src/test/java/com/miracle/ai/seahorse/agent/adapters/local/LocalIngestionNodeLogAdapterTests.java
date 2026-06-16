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

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalIngestionNodeLogAdapterTests {

    @Test
    void shouldRecordRecoverableContextOutputForSuccessfulNode() {
        LocalIngestionNodeLogAdapter adapter = new LocalIngestionNodeLogAdapter();
        IngestionContext context = IngestionContext.builder()
                .logs(new ArrayList<>())
                .rawText("Parsed text")
                .keywords(List.of("rag", "pipeline"))
                .metadata(Map.of("language", "en"))
                .vectorSpaceId("kb-1")
                .build();

        adapter.record(context, NodeConfig.builder().nodeId("parse").nodeType("parse").build(),
                NodeResult.ok(), 12);

        assertThat(context.getLogs()).hasSize(1);
        assertThat(context.getLogs().get(0).getOutput())
                .containsEntry("rawText", "Parsed text")
                .containsEntry("keywords", List.of("rag", "pipeline"))
                .containsEntry("metadata", Map.of("language", "en"))
                .containsEntry("vectorSpaceId", "kb-1");
    }
}
