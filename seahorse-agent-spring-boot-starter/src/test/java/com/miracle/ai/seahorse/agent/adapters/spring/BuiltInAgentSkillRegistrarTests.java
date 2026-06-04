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

import com.miracle.ai.seahorse.agent.kernel.application.agent.skill.KernelAgentSkillManagementService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkill;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillRevision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BuiltInAgentSkillRegistrarTests {

    @Test
    void shouldImportActualBundledPublicSkills() throws Exception {
        InMemoryAgentSkillRepository repository = new InMemoryAgentSkillRepository();
        CurrentUserPort currentUserPort = () -> Optional.of(new CurrentUser(1L, "system", "admin", null));
        KernelAgentSkillManagementService service = new KernelAgentSkillManagementService(repository, currentUserPort,
                Clock.systemUTC());
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:/skills/public/*/SKILL.md");
        long readableCount = java.util.Arrays.stream(resources).filter(Resource::isReadable).count();
        BuiltInAgentSkillRegistrar registrar = new BuiltInAgentSkillRegistrar(service, resolver);

        registrar.run(null);

        assertThat(readableCount).isGreaterThan(0);
        assertThat(repository.skills).hasSize((int) readableCount);
        assertThat(repository.skills).containsKeys(skillKey("bootstrap"), skillKey("github-deep-research"));
    }

    @Test
    void shouldImportReadableBundledPublicSkillResources() throws Exception {
        KernelAgentSkillManagementService service = mock(KernelAgentSkillManagementService.class);
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources("classpath*:/skills/public/*/SKILL.md")).thenReturn(new Resource[]{
                resource("""
                        ---
                        name: research-helper
                        description: Research helper
                        ---
                        # Research helper
                        """),
                new ByteArrayResource("ignored".getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public boolean isReadable() {
                        return false;
                        }
                    }
        });
        BuiltInAgentSkillRegistrar registrar = new BuiltInAgentSkillRegistrar(service, resolver);

        registrar.run(null);

        ArgumentCaptor<String> markdownCaptor = ArgumentCaptor.forClass(String.class);
        verify(service).importPublic(
                org.mockito.ArgumentMatchers.eq(AgentDefinition.DEFAULT_TENANT_ID),
                markdownCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("system"));
        assertThat(markdownCaptor.getValue()).contains("name: research-helper", "# Research helper");
        verifyNoMoreInteractions(service);
    }

    @Test
    void shouldAppendReadablePackageResourcesAsReadOnlySkillAppendix() throws Exception {
        KernelAgentSkillManagementService service = mock(KernelAgentSkillManagementService.class);
        ResourcePatternResolver resolver = mock(ResourcePatternResolver.class);
        when(resolver.getResources("classpath*:/skills/public/*/SKILL.md")).thenReturn(new Resource[]{
                resource("file:/classes/skills/public/research-helper/SKILL.md", """
                        ---
                        name: research-helper
                        description: Research helper
                        ---
                        # Research helper
                        """)
        });
        when(resolver.getResources("classpath*:/skills/public/research-helper/**/*")).thenReturn(new Resource[]{
                resource("file:/classes/skills/public/research-helper/SKILL.md", "ignored"),
                resource("file:/classes/skills/public/research-helper/LICENSE.txt", "Apache License"),
                resource("file:/classes/skills/public/research-helper/references/guide.md", """
                        Use the guide.
                        ```json
                        {}
                        ```
                        """),
                resource("file:/classes/skills/public/research-helper/scripts/analyze.py", "print('read only')")
        });
        BuiltInAgentSkillRegistrar registrar = new BuiltInAgentSkillRegistrar(service, resolver);

        registrar.run(null);

        ArgumentCaptor<String> markdownCaptor = ArgumentCaptor.forClass(String.class);
        verify(service).importPublic(
                org.mockito.ArgumentMatchers.eq(AgentDefinition.DEFAULT_TENANT_ID),
                markdownCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("system"));
        assertThat(markdownCaptor.getValue())
                .contains("## Skill Package Resources",
                        "These bundled files are read-only reference material",
                        "### references/guide.md",
                        "Use the guide.",
                        "````markdown\nUse the guide.",
                        "### LICENSE.txt",
                        "Apache License",
                        "### scripts/analyze.py",
                        "print('read only')")
                .doesNotContain("### SKILL.md");
        verifyNoMoreInteractions(service);
    }

    private static Resource resource(String content) {
        return resource("file:/classes/skills/public/research-helper/SKILL.md", content);
    }

    private static Resource resource(String url, String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public URL getURL() throws IOException {
                return new URL(url);
            }

            @Override
            public String getDescription() {
                return url;
            }
        };
    }

    private static String skillKey(String name) {
        return AgentDefinition.DEFAULT_TENANT_ID + ":" + name;
    }

    private static final class InMemoryAgentSkillRepository implements AgentSkillRepositoryPort {
        private final Map<String, AgentSkill> skills = new HashMap<>();
        private final Map<String, AgentSkillRevision> revisions = new HashMap<>();

        @Override
        public void saveSkill(AgentSkill skill) {
            skills.put(key(skill.tenantId(), skill.name()), skill);
        }

        @Override
        public Optional<AgentSkill> findSkill(String tenantId, String name) {
            return Optional.ofNullable(skills.get(key(tenantId, name)));
        }

        @Override
        public AgentSkillPage page(String tenantId, long current, long size, String keyword) {
            return new AgentSkillPage(List.copyOf(skills.values()), skills.size(), size, current, 1);
        }

        @Override
        public void saveRevision(AgentSkillRevision revision) {
            revisions.put(revision.revisionId(), revision);
        }

        @Override
        public long nextRevisionNo(String tenantId, String skillName) {
            long count = revisions.values().stream()
                    .filter(revision -> revision.tenantId().equals(tenantId) && revision.skillName().equals(skillName))
                    .count();
            return count + 1;
        }

        @Override
        public Optional<AgentSkillRevision> findRevision(String tenantId, String revisionId) {
            AgentSkillRevision revision = revisions.get(revisionId);
            if (revision == null || !revision.tenantId().equals(tenantId)) {
                return Optional.empty();
            }
            return Optional.of(revision);
        }

        @Override
        public List<AgentSkillRevision> listRevisions(String tenantId, String skillName) {
            return revisions.values().stream()
                    .filter(revision -> revision.tenantId().equals(tenantId)
                            && revision.skillName().equals(skillName))
                    .toList();
        }

        @Override
        public List<AgentSkillBinding> listBindings(String tenantId, String agentId) {
            return List.of();
        }

        @Override
        public void replaceBindings(String tenantId, String agentId, List<AgentSkillBinding> bindings) {
        }

        private String key(String tenantId, String name) {
            return tenantId + ":" + name;
        }
    }
}
