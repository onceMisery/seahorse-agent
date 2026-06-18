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

package com.miracle.ai.seahorse.agent.adapters.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Seahorse 原生 Web 横切治理配置。
 */
@Configuration(proxyBeanMethods = false)
public class SeahorseWebGovernanceConfiguration implements WebMvcConfigurer, Filter {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final boolean demoModeEnabled;

    public SeahorseWebGovernanceConfiguration(
            @Value("${seahorse-agent.web.demo-mode.enabled:false}") boolean demoModeEnabled) {
        this.demoModeEnabled = demoModeEnabled;
    }

    @Bean
    public AdvancedFeatureGate seahorseAdvancedFeatureGate(
            @Value("${seahorse-agent.product-mode:demo}") String productMode,
            @Value("${seahorse-agent.advanced.sandbox-enabled:false}") boolean sandboxEnabled,
            @Value("${seahorse-agent.advanced.connector-management-enabled:false}")
            boolean connectorManagementEnabled,
            @Value("${seahorse-agent.advanced.mcp-tool-enabled:false}") boolean mcpToolEnabled,
            @Value("${seahorse-agent.advanced.secret-management-enabled:false}") boolean secretManagementEnabled,
            @Value("${seahorse-agent.advanced.agent-handoff-enabled:false}") boolean agentHandoffEnabled,
            @Value("${seahorse-agent.advanced.remote-agent-enabled:false}") boolean remoteAgentEnabled,
            @Value("${seahorse-agent.advanced.local-agent-enabled:false}") boolean localAgentEnabled,
            @Value("${seahorse-agent.advanced.intent-tree-management-enabled:false}") boolean intentTreeManagementEnabled,
            @Value("${seahorse-agent.advanced.ingestion-task-management-enabled:false}")
            boolean ingestionTaskManagementEnabled,
            @Value("${seahorse-agent.advanced.ingestion-pipeline-management-enabled:false}")
            boolean ingestionPipelineManagementEnabled,
            @Value("${seahorse-agent.advanced.tool-catalog-management-enabled:false}")
            boolean toolCatalogManagementEnabled,
            @Value("${seahorse-agent.advanced.skill-management-enabled:false}") boolean skillManagementEnabled,
            @Value("${seahorse-agent.advanced.agent-definition-management-enabled:false}")
            boolean agentDefinitionManagementEnabled,
            @Value("${seahorse-agent.advanced.agent-factory-management-enabled:false}")
            boolean agentFactoryManagementEnabled,
            @Value("${seahorse-agent.advanced.agent-tool-binding-management-enabled:false}")
            boolean agentToolBindingManagementEnabled,
            @Value("${seahorse-agent.advanced.agent-run-management-enabled:false}")
            boolean agentRunManagementEnabled,
            @Value("${seahorse-agent.advanced.agent-evaluation-enabled:false}") boolean agentEvaluationEnabled,
            @Value("${seahorse-agent.advanced.production-gate-enabled:false}") boolean productionGateEnabled,
            @Value("${seahorse-agent.advanced.enterprise-pilot-readiness-enabled:false}")
            boolean enterprisePilotReadinessEnabled,
            @Value("${seahorse-agent.advanced.agent-rollout-management-enabled:false}")
            boolean agentRolloutManagementEnabled,
            @Value("${seahorse-agent.advanced.quota-management-enabled:false}") boolean quotaManagementEnabled,
            @Value("${seahorse-agent.advanced.resource-acl-management-enabled:false}")
            boolean resourceAclManagementEnabled,
            @Value("${seahorse-agent.advanced.memory-governance-enabled:false}") boolean memoryGovernanceEnabled,
            @Value("${seahorse-agent.advanced.rag-evaluation-enabled:false}") boolean ragEvaluationEnabled,
            @Value("${seahorse-agent.advanced.metadata-governance-enabled:false}") boolean metadataGovernanceEnabled,
            @Value("${seahorse-agent.advanced.audit-log-enabled:false}") boolean auditLogEnabled,
            @Value("${seahorse-agent.advanced.cost-analytics-enabled:false}") boolean costAnalyticsEnabled) {
        return AdvancedFeatureGate.configured(
                ProductMode.fromProperty(productMode),
                sandboxEnabled,
                connectorManagementEnabled,
                mcpToolEnabled,
                secretManagementEnabled,
                agentHandoffEnabled,
                remoteAgentEnabled,
                localAgentEnabled,
                intentTreeManagementEnabled,
                ingestionTaskManagementEnabled,
                ingestionPipelineManagementEnabled,
                toolCatalogManagementEnabled,
                skillManagementEnabled,
                agentDefinitionManagementEnabled,
                agentFactoryManagementEnabled,
                agentToolBindingManagementEnabled,
                agentRunManagementEnabled,
                agentEvaluationEnabled,
                productionGateEnabled,
                enterprisePilotReadinessEnabled,
                agentRolloutManagementEnabled,
                quotaManagementEnabled,
                resourceAclManagementEnabled,
                memoryGovernanceEnabled,
                ragEvaluationEnabled,
                metadataGovernanceEnabled,
                auditLogEnabled,
                costAnalyticsEnabled);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(demoModeInterceptor()).addPathPatterns("/**");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (response instanceof HttpServletResponse httpResponse
                && httpResponse.getContentType() == null) {
            httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
        }
        chain.doFilter(request, response);
    }

    private HandlerInterceptor demoModeInterceptor() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                    throws IOException {
                if (!demoModeEnabled || !WRITE_METHODS.contains(request.getMethod())) {
                    return true;
                }
                if (isAllowedInDemoMode(request)) {
                    return true;
                }
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"1\",\"message\":\"demo mode is read-only\"}");
                return false;
            }
        };
    }

    private boolean isAllowedInDemoMode(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.startsWith("/auth/");
    }
}
