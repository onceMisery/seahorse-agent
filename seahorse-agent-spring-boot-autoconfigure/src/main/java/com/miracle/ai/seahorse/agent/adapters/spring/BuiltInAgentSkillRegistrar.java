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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Imports classpath bundled PUBLIC Agent skills into the repository.
 */
public class BuiltInAgentSkillRegistrar implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BuiltInAgentSkillRegistrar.class);
    private static final String PUBLIC_SKILL_PATTERN = "classpath*:/skills/public/*/SKILL.md";
    private static final String PUBLIC_SKILL_PACKAGE_PATTERN = "classpath*:/skills/public/%s/**/*";
    private static final String SYSTEM_OPERATOR = "system";
    private static final Set<String> RESOURCE_ROOTS = Set.of(
            "references",
            "templates",
            "assets",
            "scripts",
            "agents",
            "eval-viewer",
            "evals");

    private final KernelAgentSkillManagementService skillManagementService;
    private final ResourcePatternResolver resourceResolver;

    public BuiltInAgentSkillRegistrar(KernelAgentSkillManagementService skillManagementService) {
        this(skillManagementService, new PathMatchingResourcePatternResolver());
    }

    BuiltInAgentSkillRegistrar(KernelAgentSkillManagementService skillManagementService,
                               ResourcePatternResolver resourceResolver) {
        this.skillManagementService = Objects.requireNonNull(skillManagementService,
                "skillManagementService must not be null");
        this.resourceResolver = Objects.requireNonNull(resourceResolver, "resourceResolver must not be null");
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Resource[] resources = resourceResolver.getResources(PUBLIC_SKILL_PATTERN);
        Arrays.stream(resources)
                .filter(Resource::isReadable)
                .forEach(this::importResource);
    }

    private void importResource(Resource resource) {
        String skillName = skillName(resource);
        try {
            String markdown = resource.getContentAsString(StandardCharsets.UTF_8);
            if (skillName != null) {
                markdown = appendPackageResources(skillName, markdown);
            }
            skillManagementService.importPublic(AgentDefinition.DEFAULT_TENANT_ID, markdown, SYSTEM_OPERATOR);
        } catch (Exception ex) {
            LOG.warn("Failed to import built-in Agent skill: skillName={}, resource={}",
                    Objects.requireNonNullElse(skillName, "unknown"),
                    resource.getDescription(),
                    ex);
        }
    }

    private String appendPackageResources(String skillName, String markdown) throws Exception {
        Resource[] resources = resourceResolver.getResources(PUBLIC_SKILL_PACKAGE_PATTERN.formatted(skillName));
        if (resources == null || resources.length == 0) {
            return markdown;
        }
        List<PackageResource> packageResources = new ArrayList<>();
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue;
            }
            String relativePath = relativePath(skillName, resource);
            if (!isAllowedPackageResource(relativePath)) {
                continue;
            }
            packageResources.add(new PackageResource(relativePath,
                    resource.getContentAsString(StandardCharsets.UTF_8)));
        }
        if (packageResources.isEmpty()) {
            return markdown;
        }
        packageResources.sort(Comparator.comparing(PackageResource::path));
        StringBuilder out = new StringBuilder(markdown.stripTrailing());
        out.append("\n\n## Skill Package Resources\n\n");
        out.append("These bundled files are read-only reference material. Treat scripts as source text only; ")
                .append("do not execute them unless the runtime explicitly grants an external tool.\n");
        for (PackageResource packageResource : packageResources) {
            out.append("\n### ").append(packageResource.path()).append("\n\n");
            String fence = fenceFor(packageResource.content());
            out.append(fence).append(fenceLanguage(packageResource.path())).append('\n');
            out.append(packageResource.content().stripTrailing()).append('\n');
            out.append(fence).append('\n');
        }
        return out.toString();
    }

    private boolean isAllowedPackageResource(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return false;
        }
        String safePath = relativePath.replace('\\', '/');
        if (safePath.equals("SKILL.md") || safePath.startsWith("/") || safePath.contains("../")
                || safePath.startsWith(".")) {
            return false;
        }
        int slash = safePath.indexOf('/');
        if (slash < 0) {
            return safePath.endsWith(".md") || safePath.endsWith(".txt") || safePath.endsWith(".json");
        }
        return RESOURCE_ROOTS.contains(safePath.substring(0, slash));
    }

    private String skillName(Resource resource) {
        String description = normalize(resource);
        int marker = description.indexOf("/skills/public/");
        if (marker < 0) {
            return null;
        }
        String remainder = description.substring(marker + "/skills/public/".length());
        int slash = remainder.indexOf('/');
        if (slash <= 0) {
            return null;
        }
        return remainder.substring(0, slash);
    }

    private String relativePath(String skillName, Resource resource) {
        String description = normalize(resource);
        String marker = "/skills/public/" + skillName + "/";
        int start = description.indexOf(marker);
        if (start < 0) {
            return "";
        }
        return description.substring(start + marker.length());
    }

    private String normalize(Resource resource) {
        try {
            return resource.getURL().toString().replace('\\', '/');
        } catch (Exception ex) {
            return resource.getDescription().replace('\\', '/');
        }
    }

    private String fenceLanguage(String path) {
        if (path.endsWith(".md")) {
            return "markdown";
        }
        if (path.endsWith(".py")) {
            return "python";
        }
        if (path.endsWith(".js")) {
            return "javascript";
        }
        if (path.endsWith(".json")) {
            return "json";
        }
        if (path.endsWith(".html")) {
            return "html";
        }
        if (path.endsWith(".sh")) {
            return "bash";
        }
        return "text";
    }

    private String fenceFor(String content) {
        String safeContent = content == null ? "" : content;
        int maxRun = 0;
        int currentRun = 0;
        for (int i = 0; i < safeContent.length(); i++) {
            if (safeContent.charAt(i) == '`') {
                currentRun++;
                maxRun = Math.max(maxRun, currentRun);
            } else {
                currentRun = 0;
            }
        }
        return "`".repeat(Math.max(3, maxRun + 1));
    }

    private record PackageResource(String path, String content) {
    }
}
