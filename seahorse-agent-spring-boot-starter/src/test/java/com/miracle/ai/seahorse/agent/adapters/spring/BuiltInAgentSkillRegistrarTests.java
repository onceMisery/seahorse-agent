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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class BuiltInAgentSkillRegistrarTests {

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
}
