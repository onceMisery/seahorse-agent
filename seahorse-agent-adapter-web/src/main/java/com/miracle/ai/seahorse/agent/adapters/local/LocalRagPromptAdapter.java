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

import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.PromptContext;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 本地 RAG Prompt 组装适配器。
 *
 * <p>该实现把 KB/MCP 检索上下文写入 system message，并保留会话历史，确保原生内核默认路径具备 RAG 回答能力。
 */
public class LocalRagPromptAdapter implements RagPromptPort {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是一个严谨的知识库问答助手。请优先依据给定上下文回答用户问题。
            如果上下文不足以回答，请明确说明无法从当前知识库中确认，不要编造事实。
            回答应简洁、准确，并保持用户提问的语言。
            """;
    private static final String KB_CONTEXT_TITLE = "知识库上下文：";
    private static final String MCP_CONTEXT_TITLE = "工具上下文：";
    private static final String QUESTION_TITLE = "用户问题：";

    private final ContextWeaverPort contextWeaverPort;

    public LocalRagPromptAdapter() {
        this(new DefaultContextWeaver());
    }

    public LocalRagPromptAdapter(ContextWeaverPort contextWeaverPort) {
        this.contextWeaverPort = Objects.requireNonNullElseGet(contextWeaverPort, DefaultContextWeaver::new);
    }

    @Override
    public List<ChatMessage> buildStructuredMessages(PromptContext context,
                                                     List<ChatMessage> history,
                                                     String question,
                                                     List<String> subQuestions) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(DEFAULT_SYSTEM_PROMPT.trim()));
        buildRuntimeContext(context).ifPresent(runtimeContext -> messages.add(ChatMessage.user(runtimeContext)));
        messages.addAll(safeHistory(history));
        messages.add(ChatMessage.user(buildUserPrompt(question, subQuestions)));
        return messages;
    }

    private Optional<String> buildRuntimeContext(PromptContext context) {
        StringBuilder builder = new StringBuilder("<runtime-context>\n")
                .append("当前时间：")
                .append(LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append(" Asia/Shanghai");
        String kbContext = context == null ? "" : Objects.requireNonNullElse(context.getKbContext(), "");
        String mcpContext = context == null ? "" : Objects.requireNonNullElse(context.getMcpContext(), "");
        appendContext(builder, KB_CONTEXT_TITLE, kbContext);
        appendContext(builder, MCP_CONTEXT_TITLE, mcpContext);
        String memoryContext = contextWeaverPort.weave(
                context == null ? null : context.getContextPack(),
                context == null ? null : context.getMemoryContext(),
                ContextBudget.defaults());
        if (!memoryContext.isBlank()) {
            builder.append("\n\n").append(memoryContext);
        }
        builder.append("\n</runtime-context>");
        return Optional.of(builder.toString());
    }

    private String buildUserPrompt(String question, List<String> subQuestions) {
        StringBuilder builder = new StringBuilder(QUESTION_TITLE)
                .append(Objects.requireNonNullElse(question, "").trim());
        List<String> safeSubQuestions = Objects.requireNonNullElse(subQuestions, List.of());
        if (!safeSubQuestions.isEmpty()) {
            builder.append("\n拆分问题：").append(String.join("；", safeSubQuestions));
        }
        return builder.toString();
    }

    private void appendContext(StringBuilder builder, String title, String context) {
        if (context == null || context.isBlank()) {
            return;
        }
        builder.append("\n\n")
                .append(title)
                .append("\n")
                .append(context.trim());
    }

    private List<ChatMessage> safeHistory(List<ChatMessage> history) {
        return Objects.requireNonNullElse(history, List.<ChatMessage>of()).stream()
                .filter(Objects::nonNull)
                .toList();
    }
}
