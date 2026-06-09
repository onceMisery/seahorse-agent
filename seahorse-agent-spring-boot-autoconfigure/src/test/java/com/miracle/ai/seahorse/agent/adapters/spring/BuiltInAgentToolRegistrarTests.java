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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.InMemoryToolRegistry;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.AgentToolJsonSupport;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ChartVisualizationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.FrontendDesignToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.GitHubRepositoryReaderToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ImageGenerationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.LoadSkillResourceToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.NewsletterGenerationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.PptGenerationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositorySnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltInAgentToolRegistrarTests {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-08T00:00:00Z");

    @Test
    void shouldRegisterBuiltInToolsAndWriteThemToCatalog() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        RecordingToolCatalogRepository catalog = new RecordingToolCatalogRepository();
        AgentToolJsonSupport jsonSupport = new AgentToolJsonSupport(new ObjectMapper());
        GenericApplicationContext context = new GenericApplicationContext(new DefaultListableBeanFactory());
        context.registerBean(GitHubRepositoryReaderToolPortAdapter.class,
                () -> new GitHubRepositoryReaderToolPortAdapter(request -> new GitHubRepositorySnapshot(
                        "redis", "redis", "unstable", "https://github.com/redis/redis", "",
                        List.of(), false, FIXED_NOW), jsonSupport));
        context.registerBean(ImageGenerationToolPortAdapter.class,
                () -> new ImageGenerationToolPortAdapter(request -> ImageGenerationResult.generated(
                        request.prompt(), request.model(), "https://cdn.example.com/image.png", "", "image/png"),
                        "agnes-image-2.0-flash", jsonSupport));
        context.registerBean(NewsletterGenerationToolPortAdapter.class,
                () -> new NewsletterGenerationToolPortAdapter(ChatModelPort.noop(), "agnes-2.0-flash", jsonSupport));
        context.registerBean(PptGenerationToolPortAdapter.class,
                () -> new PptGenerationToolPortAdapter(ChatModelPort.noop(), "agnes-2.0-flash", jsonSupport));
        context.registerBean(ChartVisualizationToolPortAdapter.class,
                () -> new ChartVisualizationToolPortAdapter(ChatModelPort.noop(), "agnes-2.0-flash", jsonSupport));
        context.registerBean(FrontendDesignToolPortAdapter.class,
                () -> new FrontendDesignToolPortAdapter(ChatModelPort.noop(), "agnes-2.0-flash", jsonSupport));
        context.registerBean(LoadSkillResourceToolPortAdapter.class,
                () -> new LoadSkillResourceToolPortAdapter(jsonSupport));
        context.refresh();

        BuiltInAgentToolRegistrar registrar = new BuiltInAgentToolRegistrar(
                registry,
                context.getBeanProvider(com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort.class),
                catalog,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
        registrar.run(null);

        assertThat(registry.find(GitHubRepositoryReaderToolPortAdapter.TOOL_ID)).isPresent();
        assertThat(registry.find(ImageGenerationToolPortAdapter.TOOL_ID)).isPresent();
        assertThat(registry.find(NewsletterGenerationToolPortAdapter.TOOL_ID)).isPresent();
        assertThat(registry.find(PptGenerationToolPortAdapter.TOOL_ID)).isPresent();
        assertThat(registry.find(ChartVisualizationToolPortAdapter.TOOL_ID)).isPresent();
        assertThat(registry.find(FrontendDesignToolPortAdapter.TOOL_ID)).isPresent();
        assertThat(registry.find(LoadSkillResourceToolPortAdapter.TOOL_ID)).isPresent();
        assertThat(catalog.savedEntries()).hasSize(7);
        assertThat(catalog.findById(GitHubRepositoryReaderToolPortAdapter.TOOL_ID)).hasValueSatisfying(entry -> {
            assertThat(entry.provider()).isEqualTo(ToolProvider.BUILTIN);
            assertThat(entry.actionType()).isEqualTo(ToolActionType.READ);
            assertThat(entry.resourceType()).isEqualTo("GITHUB");
            assertThat(entry.enabled()).isTrue();
            assertThat(entry.requiresApproval()).isFalse();
            assertThat(entry.createdAt()).isEqualTo(FIXED_NOW);
        });
        assertThat(catalog.findById(ImageGenerationToolPortAdapter.TOOL_ID)).hasValueSatisfying(entry -> {
            assertThat(entry.provider()).isEqualTo(ToolProvider.BUILTIN);
            assertThat(entry.actionType()).isEqualTo(ToolActionType.EXECUTE);
            assertThat(entry.resourceType()).isEqualTo("MODEL");
        });
        assertThat(catalog.findById(NewsletterGenerationToolPortAdapter.TOOL_ID)).hasValueSatisfying(entry -> {
            assertThat(entry.provider()).isEqualTo(ToolProvider.BUILTIN);
            assertThat(entry.actionType()).isEqualTo(ToolActionType.EXECUTE);
            assertThat(entry.resourceType()).isEqualTo("MODEL");
        });
        assertThat(catalog.findById(LoadSkillResourceToolPortAdapter.TOOL_ID)).hasValueSatisfying(entry -> {
            assertThat(entry.provider()).isEqualTo(ToolProvider.BUILTIN);
            assertThat(entry.actionType()).isEqualTo(ToolActionType.READ);
            assertThat(entry.resourceType()).isEqualTo("SKILL");
        });

        context.close();
    }

    private static final class RecordingToolCatalogRepository implements ToolCatalogRepositoryPort {

        private final Map<String, ToolCatalogEntry> entries = new LinkedHashMap<>();

        @Override
        public void save(ToolCatalogEntry entry) {
            entries.put(entry.toolId(), entry);
        }

        @Override
        public Optional<ToolCatalogEntry> findById(String toolId) {
            return Optional.ofNullable(entries.get(toolId));
        }

        @Override
        public void setEnabled(String toolId, boolean enabled) {
        }

        List<ToolCatalogEntry> savedEntries() {
            return List.copyOf(entries.values());
        }
    }
}
