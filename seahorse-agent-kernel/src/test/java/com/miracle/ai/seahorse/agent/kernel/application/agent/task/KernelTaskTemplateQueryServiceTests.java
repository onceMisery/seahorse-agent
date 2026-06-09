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

package com.miracle.ai.seahorse.agent.kernel.application.agent.task;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaCostTier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelTaskTemplateQueryServiceTests {

    @Test
    void defaultTemplatesExposeGithubVisualProjectIntroAgent() {
        KernelTaskTemplateQueryService service = new KernelTaskTemplateQueryService();

        var template = service.findById(TaskTemplateId.fromValue("github-visual-project-intro"))
                .orElseThrow();

        assertEquals("github-visual-project-intro-agent", template.defaultAgentId());
        assertEquals(QuotaCostTier.HIGH, template.maxCostTier());
        assertTrue(template.enabled());
    }
}
