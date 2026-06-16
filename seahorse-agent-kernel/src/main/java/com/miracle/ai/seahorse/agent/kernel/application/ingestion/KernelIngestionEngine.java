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

package com.miracle.ai.seahorse.agent.kernel.application.ingestion;

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.IngestionNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionConditionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionNodeLogPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * L1 内核入库编排器。
 * <p>
 * 该类保留入库流水线的主干控制能力：起始节点识别、按 nextNodeId 串联执行、节点失败中断、
 * 节点主动终止时停止后续节点。L2 入库节点 Feature 只负责单节点处理逻辑。
 */
public class KernelIngestionEngine {

    private static final Logger LOG = LoggerFactory.getLogger(KernelIngestionEngine.class);

    private final ExtensionRegistry extensionRegistry;
    private final FeatureActivationContext activationContext;
    private final IngestionConditionPort conditionPort;
    private final IngestionNodeLogPort nodeLogPort;

    public KernelIngestionEngine(ExtensionRegistry extensionRegistry, FeatureActivationContext activationContext) {
        this.extensionRegistry = Objects.requireNonNull(extensionRegistry, "扩展注册表不能为空");
        this.activationContext = Objects.requireNonNullElse(activationContext, FeatureActivationContext.empty());
        this.conditionPort = IngestionConditionPort.alwaysExecute();
        this.nodeLogPort = IngestionNodeLogPort.noop();
    }

    public KernelIngestionEngine(ExtensionRegistry extensionRegistry,
                                 FeatureActivationContext activationContext,
                                 IngestionConditionPort conditionPort,
                                 IngestionNodeLogPort nodeLogPort) {
        this.extensionRegistry = Objects.requireNonNull(extensionRegistry, "扩展注册表不能为空");
        this.activationContext = Objects.requireNonNullElse(activationContext, FeatureActivationContext.empty());
        this.conditionPort = Objects.requireNonNullElse(conditionPort, IngestionConditionPort.alwaysExecute());
        this.nodeLogPort = Objects.requireNonNullElse(nodeLogPort, IngestionNodeLogPort.noop());
    }

    /**
     * 执行入库流水线。
     *
     * @param pipeline 流水线定义
     * @param context  入库上下文
     * @return 执行后的入库上下文
     */
    public IngestionContext execute(PipelineDefinition pipeline, IngestionContext context) {
        Objects.requireNonNull(pipeline, "入库流水线不能为空");
        IngestionContext safeContext = Objects.requireNonNull(context, "入库上下文不能为空");
        initializeLogs(safeContext);
        Map<String, NodeConfig> nodeConfigMap = buildNodeConfigMap(pipeline.getNodes());
        validatePipeline(nodeConfigMap);
        safeContext.setStatus(IngestionStatus.RUNNING);
        String startNodeId = hasText(safeContext.getStartNodeId())
                ? safeContext.getStartNodeId().trim()
                : findStartNode(nodeConfigMap);
        if (hasText(startNodeId) && !nodeConfigMap.containsKey(startNodeId)) {
            throw new IllegalStateException("找不到入库恢复节点配置：" + startNodeId);
        }
        executeChain(startNodeId, nodeConfigMap, safeContext);
        completeIfStillRunning(safeContext);
        return safeContext;
    }

    private Map<String, NodeConfig> buildNodeConfigMap(List<NodeConfig> nodes) {
        List<NodeConfig> safeNodes = Objects.requireNonNullElse(nodes, List.of());
        return safeNodes.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(NodeConfig::getNodeId, node -> node));
    }

    private void initializeLogs(IngestionContext context) {
        if (context.getLogs() == null) {
            context.setLogs(new ArrayList<>());
        }
    }

    private void validatePipeline(Map<String, NodeConfig> nodeConfigMap) {
        Set<String> nodeIds = nodeConfigMap.keySet();
        for (NodeConfig nodeConfig : nodeConfigMap.values()) {
            String nextNodeId = nodeConfig.getNextNodeId();
            if (hasText(nextNodeId) && !nodeIds.contains(nextNodeId)) {
                throw new IllegalStateException("找不到下一个入库节点配置：" + nextNodeId);
            }
        }
    }

    private String findStartNode(Map<String, NodeConfig> nodeConfigMap) {
        Set<String> referencedNodes = nodeConfigMap.values().stream()
                .map(NodeConfig::getNextNodeId)
                .filter(this::hasText)
                .collect(Collectors.toSet());
        return nodeConfigMap.keySet().stream()
                .filter(nodeId -> !referencedNodes.contains(nodeId))
                .findFirst()
                .orElse("");
    }

    private void executeChain(String startNodeId, Map<String, NodeConfig> nodeConfigMap, IngestionContext context) {
        String currentNodeId = startNodeId;
        int executedCount = 0;
        while (hasText(currentNodeId)) {
            if (executedCount >= nodeConfigMap.size()) {
                throw new IllegalStateException("入库流水线执行节点数超过配置上限，可能存在环");
            }
            NodeConfig nodeConfig = nodeConfigMap.get(currentNodeId);
            if (nodeConfig == null) {
                throw new IllegalStateException("找不到入库节点配置：" + currentNodeId);
            }
            executedCount++;
            NodeResult result = executeNode(context, nodeConfig);
            if (stopAfterResult(context, nodeConfig, result)) {
                return;
            }
            currentNodeId = nodeConfig.getNextNodeId();
        }
    }

    private NodeResult executeNode(IngestionContext context, NodeConfig nodeConfig) {
        IngestionNodeFeature node = findNodeFeature(nodeConfig.getNodeType());
        if (!conditionPort.shouldExecute(context, nodeConfig)) {
            NodeResult skip = NodeResult.skip("条件未满足");
            nodeLogPort.record(context, nodeConfig, skip, 0L);
            return skip;
        }
        long start = System.currentTimeMillis();
        try {
            NodeResult result = node.execute(context, nodeConfig);
            nodeLogPort.record(context, nodeConfig, result, System.currentTimeMillis() - start);
            return result;
        } catch (Exception ex) {
            LOG.error("入库节点 {} 执行失败", nodeConfig.getNodeId(), ex);
            NodeResult result = NodeResult.fail(ex);
            nodeLogPort.record(context, nodeConfig, result, System.currentTimeMillis() - start);
            return result;
        }
    }

    private IngestionNodeFeature findNodeFeature(String nodeType) {
        return extensionRegistry.getActivatedExtensions(IngestionNodeFeature.class, activationContext)
                .stream()
                .filter(feature -> feature.nodeType().equals(nodeType))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到入库节点 Feature：" + nodeType));
    }

    private boolean stopAfterResult(IngestionContext context, NodeConfig nodeConfig, NodeResult result) {
        if (result == null) {
            context.setStatus(IngestionStatus.FAILED);
            context.setError(new IllegalStateException("入库节点返回空结果：" + nodeConfig.getNodeId()));
            return true;
        }
        if (!result.isSuccess()) {
            context.setStatus(IngestionStatus.FAILED);
            context.setError(result.getError());
            return true;
        }
        return !result.isShouldContinue();
    }

    private void completeIfStillRunning(IngestionContext context) {
        if (IngestionStatus.RUNNING.equals(context.getStatus())) {
            context.setStatus(IngestionStatus.COMPLETED);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
