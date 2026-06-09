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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.EstimatedDurationTier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaCostTier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateId;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateOutputType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.TaskTemplateQueryInboundPort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelTaskTemplateQueryService implements TaskTemplateQueryInboundPort {

    public static final String DEFAULT_TOOL_POLICY_ID = "consumer-web-default";
    public static final String GITHUB_VISUAL_PROJECT_INTRO_AGENT_ID = "github-visual-project-intro-agent";

    private final List<TaskTemplate> templates;

    public KernelTaskTemplateQueryService() {
        this(defaultTemplates());
    }

    public KernelTaskTemplateQueryService(List<TaskTemplate> templates) {
        this.templates = List.copyOf(Objects.requireNonNullElseGet(templates, List::of));
    }

    @Override
    public List<TaskTemplate> listEnabled() {
        return templates.stream()
                .filter(TaskTemplate::enabled)
                .toList();
    }

    @Override
    public Optional<TaskTemplate> findById(TaskTemplateId templateId) {
        TaskTemplateId safeTemplateId = Objects.requireNonNull(templateId, "templateId must not be null");
        return templates.stream()
                .filter(TaskTemplate::enabled)
                .filter(template -> template.templateId() == safeTemplateId)
                .findFirst();
    }

    private static List<TaskTemplate> defaultTemplates() {
        return List.of(
                new TaskTemplate(
                        TaskTemplateId.QUICK_ANSWER,
                        "Quick answer",
                        "Answer directly with a concise explanation.",
                        TaskTemplateCategory.WRITING,
                        null,
                        DEFAULT_TOOL_POLICY_ID,
                        TaskTemplateOutputType.PLAIN_TEXT,
                        QuotaCostTier.LOW,
                        EstimatedDurationTier.SHORT,
                        true),
                new TaskTemplate(
                        TaskTemplateId.DEEP_RESEARCH,
                        "Deep research",
                        "Search public web sources and produce a cited report.",
                        TaskTemplateCategory.RESEARCH,
                        null,
                        DEFAULT_TOOL_POLICY_ID,
                        TaskTemplateOutputType.MARKDOWN_REPORT,
                        QuotaCostTier.HIGH,
                        EstimatedDurationTier.LONG,
                        true),
                new TaskTemplate(
                        TaskTemplateId.WEB_SUMMARY,
                        "Web summary",
                        "Summarize a webpage or public source with citations.",
                        TaskTemplateCategory.RESEARCH,
                        null,
                        DEFAULT_TOOL_POLICY_ID,
                        TaskTemplateOutputType.SOURCE_DIGEST,
                        QuotaCostTier.MEDIUM,
                        EstimatedDurationTier.MEDIUM,
                        true),
                new TaskTemplate(
                        TaskTemplateId.COMPARE_ANALYSIS,
                        "Compare analysis",
                        "Compare options, sources, or products in a structured table.",
                        TaskTemplateCategory.ANALYSIS,
                        null,
                        DEFAULT_TOOL_POLICY_ID,
                        TaskTemplateOutputType.COMPARISON_TABLE,
                        QuotaCostTier.MEDIUM,
                        EstimatedDurationTier.MEDIUM,
                        true),
                new TaskTemplate(
                        TaskTemplateId.GITHUB_VISUAL_PROJECT_INTRO,
                        "GitHub visual intro",
                        "Read a GitHub repository and generate a visual project introduction with artifacts.",
                        TaskTemplateCategory.ANALYSIS,
                        GITHUB_VISUAL_PROJECT_INTRO_AGENT_ID,
                        DEFAULT_TOOL_POLICY_ID,
                        TaskTemplateOutputType.MARKDOWN_REPORT,
                        QuotaCostTier.HIGH,
                        EstimatedDurationTier.LONG,
                        true));
    }
}
