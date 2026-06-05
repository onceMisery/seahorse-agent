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

package com.miracle.ai.seahorse.agent.adapters.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RerankModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Jina AI rerank model adapter for the Advanced RAG pipeline.
 *
 * <p>Calls the Jina AI rerank endpoint ({@code https://api.jina.ai/v1/rerank})
 * using {@link java.net.http.HttpClient}. On any failure the adapter degrades
 * gracefully by returning the documents in their original order.
 */
public class JinaRerankModelAdapter implements RerankModelPort {

    private static final Logger log = LoggerFactory.getLogger(JinaRerankModelAdapter.class);
    private static final String DEFAULT_MODEL = "jina-reranker-v2-base-multilingual";
    private static final String RERANK_URL = "https://api.jina.ai/v1/rerank";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Create an adapter with the specified API key and default model.
     *
     * @param apiKey the Jina AI API key
     */
    public JinaRerankModelAdapter(String apiKey) {
        this(apiKey, DEFAULT_MODEL);
    }

    /**
     * Create an adapter with the specified API key and model name.
     *
     * @param apiKey the Jina AI API key
     * @param model  the rerank model name
     */
    public JinaRerankModelAdapter(String apiKey, String model) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey must not be null");
        this.model = Objects.requireNonNullElse(model, DEFAULT_MODEL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<RerankResult> rerank(String query, List<String> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        try {
            return doRerank(query, documents, topK);
        } catch (Exception ex) {
            log.warn("Jina rerank failed, falling back to original order: {}", ex.getMessage());
            return fallbackResults(documents, topK);
        }
    }

    private List<RerankResult> doRerank(String query, List<String> documents, int topK)
            throws Exception {
        String requestBody = buildRequestBody(query, documents, topK);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RERANK_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new RuntimeException("Jina rerank API returned status " + response.statusCode()
                    + ": " + response.body());
        }

        return parseResponse(response.body());
    }

    private String buildRequestBody(String query, List<String> documents, int topK)
            throws Exception {
        var root = objectMapper.createObjectNode();
        root.put("model", model);
        root.put("query", query);
        root.put("top_n", topK);
        var docsArray = root.putArray("documents");
        for (String doc : documents) {
            docsArray.add(doc);
        }
        return objectMapper.writeValueAsString(root);
    }

    private List<RerankResult> parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.get("results");
        if (results == null || !results.isArray()) {
            throw new RuntimeException("Unexpected Jina rerank response: missing 'results' array");
        }
        List<RerankResult> rerankResults = new ArrayList<>();
        for (JsonNode node : results) {
            int index = node.get("index").asInt();
            double score = node.has("relevance_score")
                    ? node.get("relevance_score").asDouble()
                    : 0.0;
            rerankResults.add(new RerankResult(index, score));
        }
        return List.copyOf(rerankResults);
    }

    private List<RerankResult> fallbackResults(List<String> documents, int topK) {
        int limit = Math.min(documents.size(), topK);
        List<RerankResult> results = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            results.add(new RerankResult(i, 1.0 - (double) i / documents.size()));
        }
        return List.copyOf(results);
    }
}
