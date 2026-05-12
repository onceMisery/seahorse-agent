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

package com.miracle.ai.seahorse.agent.kernel.application.model;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelHealthPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelRoutingStatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;

import java.util.List;
import java.util.Objects;

/**
 * L1 模型路由服务门面。
 * <p>
 * 模型候选、健康状态、fallback 和 token 估算均通过 Seahorse 原生端口承载。
 */
public class KernelModelRoutingService {

    private static final String CAPABILITY_CHAT = "chat";
    private static final String CAPABILITY_STREAMING_CHAT = "streaming_chat";
    private static final String CAPABILITY_EMBEDDING = "embedding";
    private static final String CAPABILITY_RERANK = "rerank";

    private final ChatModelPort chatModelPort;
    private final StreamingChatModelPort streamingChatModelPort;
    private final ModelProviderPort modelProviderPort;
    private final EmbeddingModelPort embeddingModelPort;
    private final RerankModelPort rerankModelPort;
    private final TokenCounterPort tokenCounterPort;
    private final ModelHealthPort modelHealthPort;
    private final ModelRoutingStatePort modelRoutingStatePort;

    public KernelModelRoutingService(ChatModelPort chatModelPort, StreamingChatModelPort streamingChatModelPort) {
        this(chatModelPort, streamingChatModelPort, ModelProviderPort.noop(),
                EmbeddingModelPort.noop(), RerankModelPort.noop());
    }

    public KernelModelRoutingService(ChatModelPort chatModelPort,
                                     StreamingChatModelPort streamingChatModelPort,
                                     ModelProviderPort modelProviderPort,
                                     EmbeddingModelPort embeddingModelPort,
                                     RerankModelPort rerankModelPort) {
        this(chatModelPort, streamingChatModelPort, modelProviderPort, embeddingModelPort, rerankModelPort,
                TokenCounterPort.approximate(), ModelHealthPort.noop(), ModelRoutingStatePort.firstAvailable());
    }

    public KernelModelRoutingService(ChatModelPort chatModelPort,
                                     StreamingChatModelPort streamingChatModelPort,
                                     ModelProviderPort modelProviderPort,
                                     EmbeddingModelPort embeddingModelPort,
                                     RerankModelPort rerankModelPort,
                                     TokenCounterPort tokenCounterPort,
                                     ModelHealthPort modelHealthPort,
                                     ModelRoutingStatePort modelRoutingStatePort) {
        this.chatModelPort = Objects.requireNonNull(chatModelPort, "同步模型端口不能为空");
        this.streamingChatModelPort = Objects.requireNonNull(streamingChatModelPort, "流式模型端口不能为空");
        this.modelProviderPort = Objects.requireNonNullElse(modelProviderPort, ModelProviderPort.noop());
        this.embeddingModelPort = Objects.requireNonNullElse(embeddingModelPort, EmbeddingModelPort.noop());
        this.rerankModelPort = Objects.requireNonNullElse(rerankModelPort, RerankModelPort.noop());
        this.tokenCounterPort = Objects.requireNonNullElse(tokenCounterPort, TokenCounterPort.approximate());
        this.modelHealthPort = Objects.requireNonNullElse(modelHealthPort, ModelHealthPort.noop());
        this.modelRoutingStatePort = Objects.requireNonNullElse(modelRoutingStatePort,
                ModelRoutingStatePort.firstAvailable());
    }

    public String chat(ChatRequest request) {
        return chatModelPort.chat(request, "");
    }

    public String chat(ChatRequest request, String modelId) {
        String selectedModel = selectHealthyModel(modelId, CAPABILITY_CHAT);
        try {
            String response = chatModelPort.chat(request, selectedModel);
            modelHealthPort.recordSuccess(selectedModel);
            return response;
        } catch (RuntimeException ex) {
            modelHealthPort.recordFailure(selectedModel, ex);
            return fallbackChat(request, selectedModel, ex);
        }
    }

    public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
        String selectedModel = selectHealthyModel("", CAPABILITY_STREAMING_CHAT);
        if (!selectedModel.isBlank()) {
            modelHealthPort.recordSuccess(selectedModel);
        }
        return streamingChatModelPort.streamChat(request, callback);
    }

    public boolean available(String modelId) {
        return modelProviderPort.available(modelId);
    }

    public List<String> listModels(String capability) {
        return modelProviderPort.listModels(capability);
    }

    public List<Float> embed(String modelId, String text) {
        String selectedModel = selectHealthyModel(modelId, CAPABILITY_EMBEDDING);
        try {
            List<Float> embedding = embeddingModelPort.embed(selectedModel, text);
            modelHealthPort.recordSuccess(selectedModel);
            return embedding;
        } catch (RuntimeException ex) {
            modelHealthPort.recordFailure(selectedModel, ex);
            throw ex;
        }
    }

    public List<RetrievedChunk> rerank(String modelId, String query, List<RetrievedChunk> chunks) {
        String selectedModel = selectHealthyModel(modelId, CAPABILITY_RERANK);
        try {
            List<RetrievedChunk> reranked = rerankModelPort.rerank(selectedModel, query, chunks);
            modelHealthPort.recordSuccess(selectedModel);
            return reranked;
        } catch (RuntimeException ex) {
            modelHealthPort.recordFailure(selectedModel, ex);
            throw ex;
        }
    }

    public int countTextTokens(String modelId, String text) {
        return tokenCounterPort.countTextTokens(modelId, text);
    }

    public int countRequestTokens(String modelId, ChatRequest request) {
        ChatRequest safeRequest = Objects.requireNonNullElseGet(request, ChatRequest::new);
        return tokenCounterPort.countMessages(modelId, safeRequest.getMessages());
    }

    private String selectHealthyModel(String requestedModelId, String capability) {
        List<String> candidates = modelProviderPort.listModels(capability);
        String selected = modelRoutingStatePort.selectModel(requestedModelId, capability, candidates);
        if (selected.isBlank() || modelHealthPort.isHealthy(selected)) {
            return selected;
        }
        return candidates.stream()
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .filter(modelHealthPort::isHealthy)
                .findFirst()
                .orElse(selected);
    }

    private String fallbackChat(ChatRequest request, String failedModel, RuntimeException original) {
        for (String candidate : modelProviderPort.listModels(CAPABILITY_CHAT)) {
            if (candidate == null || candidate.isBlank() || candidate.equals(failedModel)
                    || !modelHealthPort.isHealthy(candidate)) {
                continue;
            }
            try {
                String response = chatModelPort.chat(request, candidate);
                modelHealthPort.recordSuccess(candidate);
                return response;
            } catch (RuntimeException ex) {
                modelHealthPort.recordFailure(candidate, ex);
            }
        }
        throw original;
    }
}
