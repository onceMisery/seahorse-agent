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

package com.miracle.ai.seahorse.agent.kernel.application.intent;

import com.miracle.ai.seahorse.agent.ports.inbound.intent.IntentTreeInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodeTree;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentTreeRepositoryPort;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Kernel 层意图树管理服务。
 */
public class KernelIntentTreeService implements IntentTreeInboundPort {

    private static final String CACHE_KEY = "seahorse-agent:intent:tree";
    private static final String DEFAULT_OPERATOR = "seahorse";
    private static final int LEVEL_TOPIC = 2;
    private static final int KIND_KB = 0;

    private final IntentTreeRepositoryPort repositoryPort;
    private final KeyValueCachePort cachePort;

    public KernelIntentTreeService(IntentTreeRepositoryPort repositoryPort, KeyValueCachePort cachePort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.cachePort = Objects.requireNonNull(cachePort, "cachePort must not be null");
    }

    @Override
    public List<IntentNodeTree> tree() {
        List<IntentNodeTree> nodes = repositoryPort.listActiveNodes();
        Map<String, List<IntentNodeTree>> childrenMap = buildChildrenMap(nodes);
        return childrenMap.getOrDefault("ROOT", Collections.emptyList()).stream()
                .map(root -> buildTree(root, childrenMap))
                .toList();
    }

    @Override
    public String create(IntentNodePayload payload) {
        IntentNodePayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        validateCreate(safePayload);
        String id = repositoryPort.create(safePayload, DEFAULT_OPERATOR);
        clearCache();
        return id;
    }

    @Override
    public void update(String id, IntentNodePayload payload) {
        IntentNodePayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        validateTopK(safePayload.getTopK());
        if (!repositoryPort.update(requireText(id, "id must not be blank"), safePayload, DEFAULT_OPERATOR)) {
            throw new IllegalArgumentException("intent node not found");
        }
        clearCache();
    }

    @Override
    public void delete(String id) {
        if (!repositoryPort.deleteByIds(List.of(requireText(id, "id must not be blank")))) {
            throw new IllegalArgumentException("intent node not found");
        }
        clearCache();
    }

    @Override
    public void batchEnable(List<String> ids) {
        List<String> normalizedIds = normalizeIds(ids);
        if (!repositoryPort.updateEnabled(normalizedIds, 1, DEFAULT_OPERATOR)) {
            throw new IllegalArgumentException("intent node not found");
        }
        clearCache();
    }

    @Override
    public void batchDisable(List<String> ids) {
        List<IntentNodeTree> targets = listAndValidateTargets(ids);
        List<IntentNodeTree> allNodes = repositoryPort.listActiveNodes();
        Map<String, List<IntentNodeTree>> childrenMap = buildChildrenMap(allNodes);
        Set<String> targetIds = targets.stream().map(IntentNodeTree::getId).collect(Collectors.toSet());
        for (IntentNodeTree target : targets) {
            List<IntentNodeTree> descendants = collectDescendants(target.getIntentCode(), childrenMap);
            boolean hasEnabledUnselected = descendants.stream()
                    .anyMatch(node -> Objects.equals(node.getEnabled(), 1) && !targetIds.contains(node.getId()));
            if (hasEnabledUnselected) {
                throw new IllegalArgumentException("enabled child node must be selected before disabling parent");
            }
        }
        repositoryPort.updateEnabled(new ArrayList<>(targetIds), 0, DEFAULT_OPERATOR);
        clearCache();
    }

    @Override
    public void batchDelete(List<String> ids) {
        List<IntentNodeTree> targets = listAndValidateTargets(ids);
        List<IntentNodeTree> allNodes = repositoryPort.listActiveNodes();
        Map<String, List<IntentNodeTree>> childrenMap = buildChildrenMap(allNodes);
        Set<String> targetIds = targets.stream().map(IntentNodeTree::getId).collect(Collectors.toSet());
        for (IntentNodeTree target : targets) {
            List<IntentNodeTree> descendants = collectDescendants(target.getIntentCode(), childrenMap);
            boolean hasUnselected = descendants.stream().anyMatch(node -> !targetIds.contains(node.getId()));
            if (hasUnselected) {
                throw new IllegalArgumentException("all child nodes must be selected before deleting parent");
            }
        }
        repositoryPort.deleteByIds(new ArrayList<>(targetIds));
        clearCache();
    }

    private void validateCreate(IntentNodePayload payload) {
        String intentCode = requireText(payload.getIntentCode(), "intentCode must not be blank");
        validateTopK(payload.getTopK());
        if (repositoryPort.existsByIntentCode(intentCode)) {
            throw new IllegalArgumentException("intentCode already exists: " + intentCode);
        }
        Integer level = payload.getLevel();
        Integer kind = payload.getKind() == null ? KIND_KB : payload.getKind();
        if (Objects.equals(level, LEVEL_TOPIC) && Objects.equals(kind, KIND_KB) && !hasText(payload.getKbId())) {
            throw new IllegalArgumentException("TOPIC KB node must bind kbId");
        }
    }

    private List<IntentNodeTree> listAndValidateTargets(List<String> ids) {
        List<String> normalizedIds = normalizeIds(ids);
        Map<String, IntentNodeTree> nodeMap = repositoryPort.listActiveNodes().stream()
                .collect(Collectors.toMap(IntentNodeTree::getId, Function.identity(), (first, second) -> first));
        List<IntentNodeTree> targets = normalizedIds.stream()
                .map(nodeMap::get)
                .filter(Objects::nonNull)
                .toList();
        if (targets.size() != normalizedIds.size()) {
            throw new IllegalArgumentException("intent node not found");
        }
        return targets;
    }

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty");
        }
        List<String> normalizedIds = ids.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedIds.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty");
        }
        return normalizedIds;
    }

    private Map<String, List<IntentNodeTree>> buildChildrenMap(List<IntentNodeTree> nodes) {
        return nodes.stream().peek(node -> node.setChildren(List.of()))
                .collect(Collectors.groupingBy(node -> hasText(node.getParentCode()) ? node.getParentCode() : "ROOT"));
    }

    private IntentNodeTree buildTree(IntentNodeTree current, Map<String, List<IntentNodeTree>> childrenMap) {
        List<IntentNodeTree> children = childrenMap.getOrDefault(current.getIntentCode(), Collections.emptyList());
        current.setChildren(children.stream().map(child -> buildTree(child, childrenMap)).toList());
        return current;
    }

    private List<IntentNodeTree> collectDescendants(String intentCode, Map<String, List<IntentNodeTree>> childrenMap) {
        if (!hasText(intentCode)) {
            return List.of();
        }
        List<IntentNodeTree> result = new ArrayList<>();
        Deque<IntentNodeTree> stack = new ArrayDeque<>(childrenMap.getOrDefault(intentCode, Collections.emptyList()));
        Set<String> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            IntentNodeTree current = stack.pop();
            if (!visited.add(current.getId())) {
                continue;
            }
            result.add(current);
            List<IntentNodeTree> children = childrenMap.getOrDefault(current.getIntentCode(), Collections.emptyList());
            for (int index = children.size() - 1; index >= 0; index--) {
                stack.push(children.get(index));
            }
        }
        return result;
    }

    private void validateTopK(Integer topK) {
        if (topK != null && topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private void clearCache() {
        cachePort.delete(CACHE_KEY);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
