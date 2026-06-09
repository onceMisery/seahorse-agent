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

package com.miracle.ai.seahorse.agent.kernel.application.agent.quota;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.EstimatedDurationTier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaCostTier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateId;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateOutputType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.TaskTemplateQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.UserQuotaSummaryQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KernelQuotaSummaryServiceTests {

    @Test
    void githubVisualIntroSummaryUsesTemplateHighCostAndLongDuration() {
        KernelQuotaSummaryService service = new KernelQuotaSummaryService();

        var summary = service.summary(new UserQuotaSummaryQuery(
                "tenant-a",
                "user-1",
                "github-visual-project-intro"));

        assertEquals(QuotaCostTier.HIGH, summary.defaultCostTier());
        assertEquals(EstimatedDurationTier.LONG, summary.estimatedDuration());
    }

    @Test
    void summaryUsesTaskTemplateMetadataWhenTemplateQueryPortIsAvailable() {
        KernelQuotaSummaryService service = new KernelQuotaSummaryService(null, null, new SingleTemplateQueryPort(
                new TaskTemplate(
                        TaskTemplateId.WEB_SUMMARY,
                        "Custom web summary",
                        "Custom metadata should drive quota hints.",
                        TaskTemplateCategory.RESEARCH,
                        null,
                        "policy-1",
                        TaskTemplateOutputType.SOURCE_DIGEST,
                        QuotaCostTier.HIGH,
                        EstimatedDurationTier.LONG,
                        true)));

        var summary = service.summary(new UserQuotaSummaryQuery(
                "tenant-a",
                "user-1",
                "web-summary"));

        assertEquals(QuotaCostTier.HIGH, summary.defaultCostTier());
        assertEquals(EstimatedDurationTier.LONG, summary.estimatedDuration());
    }

    private record SingleTemplateQueryPort(TaskTemplate template) implements TaskTemplateQueryInboundPort {

        @Override
        public List<TaskTemplate> listEnabled() {
            return List.of(template);
        }

        @Override
        public Optional<TaskTemplate> findById(TaskTemplateId templateId) {
            return template.templateId() == templateId ? Optional.of(template) : Optional.empty();
        }
    }
}
