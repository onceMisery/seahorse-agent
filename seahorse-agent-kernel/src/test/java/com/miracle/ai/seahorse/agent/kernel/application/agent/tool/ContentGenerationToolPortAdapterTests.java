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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentGenerationToolPortAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentToolJsonSupport jsonSupport = new AgentToolJsonSupport(objectMapper);

    @Test
    void newsletterGenerationShouldCallChatModelAndReturnMarkdownArtifact() throws Exception {
        CapturingChatModel chatModel = new CapturingChatModel("""
                # Redis 项目简报

                Redis 是内存数据结构服务器。
                """);
        NewsletterGenerationToolPortAdapter tool = new NewsletterGenerationToolPortAdapter(
                chatModel, "agnes-2.0-flash", jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", NewsletterGenerationToolPortAdapter.TOOL_ID,
                Map.of(
                        "topic", "Redis 项目介绍",
                        "audience", "架构评审",
                        "sourceMaterial", "README.md: Redis is an in-memory data structure store."));

        assertTrue(result.success());
        assertEquals("agnes-2.0-flash", chatModel.modelId.get());
        assertTrue(chatModel.request.get().getMessages().get(0).getContent().contains("Newsletter"));
        assertTrue(chatModel.request.get().getMessages().get(1).getContent().contains("Redis 项目介绍"));
        JsonNode root = objectMapper.readTree(result.content());
        assertEquals("newsletter", root.path("artifactType").asText());
        assertEquals("markdown", root.path("format").asText());
        assertTrue(root.path("content").asText().contains("Redis 项目简报"));
    }

    @Test
    void generationToolsShouldTreatDefaultModelAsConfiguredModel() {
        CapturingChatModel chatModel = new CapturingChatModel("# Redis 项目简报");
        NewsletterGenerationToolPortAdapter tool = new NewsletterGenerationToolPortAdapter(
                chatModel, "agnes-2.0-flash", jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", NewsletterGenerationToolPortAdapter.TOOL_ID,
                Map.of(
                        "topic", "Redis 项目介绍",
                        "model", "default",
                        "sourceMaterial", "README.md: Redis is a data structure server."));

        assertTrue(result.success());
        assertEquals("agnes-2.0-flash", chatModel.modelId.get());
    }

    @Test
    void pptGenerationShouldCallChatModelAndReturnDeckOutlineArtifact() throws Exception {
        CapturingChatModel chatModel = new CapturingChatModel("""
                # Redis 技术介绍

                1. 项目概览
                2. 架构设计
                """);
        PptGenerationToolPortAdapter tool = new PptGenerationToolPortAdapter(
                chatModel, "agnes-2.0-flash", jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", PptGenerationToolPortAdapter.TOOL_ID,
                Map.of(
                        "topic", "Redis 架构说明",
                        "slideCount", 6,
                        "sourceMaterial", "src/server.c: initServer();"));

        assertTrue(result.success());
        assertTrue(chatModel.request.get().getMessages().get(0).getContent().contains("presentation deck"));
        assertTrue(chatModel.request.get().getMessages().get(1).getContent().contains("slideCount=6"));
        JsonNode root = objectMapper.readTree(result.content());
        assertEquals("presentation", root.path("artifactType").asText());
        assertEquals("markdown", root.path("format").asText());
        assertTrue(root.path("content").asText().contains("架构设计"));
    }

    @Test
    void chartVisualizationShouldCallChatModelAndReturnChartSpecArtifact() throws Exception {
        CapturingChatModel chatModel = new CapturingChatModel("""
                {"chartType":"flowchart","title":"Redis 请求处理流程","mermaid":"flowchart TD\\nA[Client]-->B[Server]"}
                """);
        ChartVisualizationToolPortAdapter tool = new ChartVisualizationToolPortAdapter(
                chatModel, "agnes-2.0-flash", jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", ChartVisualizationToolPortAdapter.TOOL_ID,
                Map.of(
                        "title", "Redis 请求处理流程",
                        "chartType", "flowchart",
                        "data", "client connects to server and executes command"));

        assertTrue(result.success());
        assertTrue(chatModel.request.get().getMessages().get(0).getContent().contains("chart"));
        JsonNode root = objectMapper.readTree(result.content());
        assertEquals("chart", root.path("artifactType").asText());
        assertEquals("mermaid/json", root.path("format").asText());
        assertTrue(root.path("content").asText().contains("Redis 请求处理流程"));
    }

    @Test
    void frontendDesignShouldCallChatModelAndReturnHtmlArtifact() throws Exception {
        CapturingChatModel chatModel = new CapturingChatModel("""
                <section class="project-intro"><h1>Redis</h1><p>内存数据结构服务器</p></section>
                """);
        FrontendDesignToolPortAdapter tool = new FrontendDesignToolPortAdapter(
                chatModel, "agnes-2.0-flash", jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", FrontendDesignToolPortAdapter.TOOL_ID,
                Map.of(
                        "brief", "Redis 项目图文介绍页面",
                        "style", "technical editorial",
                        "sourceMaterial", "README.md and src/server.c evidence"));

        assertTrue(result.success());
        assertTrue(chatModel.request.get().getMessages().get(0).getContent().contains("frontend"));
        JsonNode root = objectMapper.readTree(result.content());
        assertEquals("frontend_design", root.path("artifactType").asText());
        assertEquals("html", root.path("format").asText());
        assertTrue(root.path("content").asText().contains("project-intro"));
    }

    @Test
    void generationToolsShouldFailWhenRequiredPromptIsMissing() {
        NewsletterGenerationToolPortAdapter tool = new NewsletterGenerationToolPortAdapter(
                new CapturingChatModel("ignored"), "agnes-2.0-flash", jsonSupport);

        ToolInvocationResult result = tool.invoke("call-1", NewsletterGenerationToolPortAdapter.TOOL_ID,
                Map.of("audience", "architects"));

        assertFalse(result.success());
        assertTrue(result.error().contains("topic is required"));
    }

    private static final class CapturingChatModel implements ChatModelPort {

        private final String response;
        private final AtomicReference<ChatRequest> request = new AtomicReference<>();
        private final AtomicReference<String> modelId = new AtomicReference<>();

        private CapturingChatModel(String response) {
            this.response = response;
        }

        @Override
        public String chat(ChatRequest request, String modelId) {
            this.request.set(request);
            this.modelId.set(modelId);
            return response;
        }
    }
}
