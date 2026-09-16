/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent.bench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 从 classpath 加载 AgentLoop 基准用例集。
 *
 * <p>刻意做得很薄：用例就是数据，放在 {@code seahorse-agent/agentloop-golden/*.json}，
 * 加一条用例不需要改任何 Java 代码。索引文件 {@code _index.json} 列出要加载哪些文件，
 * 与 memory-recall-golden 的约定保持一致。
 */
final class AgentLoopBenchmarkCaseRepository {

    private static final String ROOT = "seahorse-agent/agentloop-golden/";
    private static final String INDEX = ROOT + "_index.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentLoopBenchmarkCaseRepository() {
    }

    static List<AgentLoopBenchmarkCase> loadAll() {
        List<String> files = new ArrayList<>();
        JsonNode index = read(INDEX);
        JsonNode sets = index.path("sets");
        if (sets.isArray()) {
            sets.forEach(node -> {
                String name = node.asText(null);
                if (name != null && !name.isBlank()) {
                    files.add(name.endsWith(".json") ? name : name + ".json");
                }
            });
        }
        if (files.isEmpty()) {
            files.add("regression.json");
        }
        List<AgentLoopBenchmarkCase> cases = new ArrayList<>();
        for (String file : files) {
            cases.addAll(readCases(read(ROOT + file)));
        }
        return List.copyOf(cases);
    }

    private static List<AgentLoopBenchmarkCase> readCases(JsonNode root) {
        List<AgentLoopBenchmarkCase> cases = new ArrayList<>();
        JsonNode array = root.path("cases");
        if (!array.isArray()) {
            return List.of();
        }
        for (JsonNode node : array) {
            cases.add(new AgentLoopBenchmarkCase(
                    node.path("caseId").asText(null),
                    node.path("goal").asText(null),
                    node.path("maxSteps").asInt(6),
                    node.path("workTurns").asInt(0)));
        }
        return cases;
    }

    private static JsonNode read(String resource) {
        try (InputStream stream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("benchmark resource not found on classpath: " + resource);
            }
            return Objects.requireNonNull(MAPPER.readTree(stream), "benchmark resource is empty: " + resource);
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to read benchmark resource: " + resource, ex);
        }
    }
}
