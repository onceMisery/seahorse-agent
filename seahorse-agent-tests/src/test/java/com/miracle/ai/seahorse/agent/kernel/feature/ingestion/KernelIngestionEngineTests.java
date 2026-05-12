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

package com.miracle.ai.seahorse.agent.kernel.feature.ingestion;

import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionEngine;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionDescriptor;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionNodeLogPort;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 内核入库引擎契约测试。
 * <p>
 * 新入库引擎必须保留旧流水线主干语义：按 nextNodeId 串联执行、失败节点中断并标记 FAILED、
 * terminate 节点正常停止且不执行后续节点。
 */
class KernelIngestionEngineTests {

    private static final String FETCHER = "fetcher";
    private static final String PARSER = "parser";
    private static final String INDEXER = "indexer";

    @Test
    void shouldExecuteLinkedFeatureNodesInOrder() {
        List<String> executedNodes = new ArrayList<>();
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        register(registry, new RecordingNodeFeature(FETCHER, executedNodes, NodeResult.ok()));
        register(registry, new RecordingNodeFeature(PARSER, executedNodes, NodeResult.ok()));
        KernelIngestionEngine engine = new KernelIngestionEngine(registry, FeatureActivationContext.empty());

        IngestionContext context = engine.execute(pipeline(node(FETCHER, PARSER), node(PARSER, null)),
                IngestionContext.builder().taskId("task-a").build());

        Assertions.assertEquals(List.of(FETCHER, PARSER), executedNodes);
        Assertions.assertEquals(IngestionStatus.COMPLETED, context.getStatus());
    }

    @Test
    void shouldStopPipelineWhenNodeFails() {
        List<String> executedNodes = new ArrayList<>();
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        register(registry, new RecordingNodeFeature(FETCHER, executedNodes, NodeResult.ok()));
        register(registry, new RecordingNodeFeature(PARSER, executedNodes,
                NodeResult.fail(new IllegalStateException("解析失败"))));
        register(registry, new RecordingNodeFeature(INDEXER, executedNodes, NodeResult.ok()));
        KernelIngestionEngine engine = new KernelIngestionEngine(registry, FeatureActivationContext.empty());

        IngestionContext context = engine.execute(pipeline(node(FETCHER, PARSER), node(PARSER, INDEXER),
                node(INDEXER, null)), IngestionContext.builder().taskId("task-a").build());

        Assertions.assertEquals(List.of(FETCHER, PARSER), executedNodes);
        Assertions.assertEquals(IngestionStatus.FAILED, context.getStatus());
        Assertions.assertNotNull(context.getError());
    }

    @Test
    void shouldSkipNodeWhenConditionPortRejectsExecutionAndRecordLog() {
        List<String> executedNodes = new ArrayList<>();
        List<NodeResult> recordedResults = new ArrayList<>();
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        register(registry, new RecordingNodeFeature(FETCHER, executedNodes, NodeResult.ok()));
        IngestionNodeLogPort logPort = (context, config, result, durationMs) -> recordedResults.add(result);
        KernelIngestionEngine engine = new KernelIngestionEngine(registry, FeatureActivationContext.empty(),
                (context, config) -> false, logPort);

        IngestionContext context = engine.execute(pipeline(node(FETCHER, null)),
                IngestionContext.builder().taskId("task-a").build());

        Assertions.assertTrue(executedNodes.isEmpty());
        Assertions.assertEquals(IngestionStatus.COMPLETED, context.getStatus());
        Assertions.assertEquals(1, recordedResults.size());
        Assertions.assertTrue(recordedResults.get(0).isSuccess());
    }

    private void register(DefaultExtensionRegistry registry, IngestionNodeFeature feature) {
        registry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), false), feature);
    }

    private PipelineDefinition pipeline(NodeConfig... nodes) {
        return PipelineDefinition.builder().id("pipeline-a").nodes(List.of(nodes)).build();
    }

    private NodeConfig node(String nodeType, String nextNodeType) {
        return NodeConfig.builder().nodeId(nodeType).nodeType(nodeType).nextNodeId(nextNodeType).build();
    }

    private record RecordingNodeFeature(String nodeType,
                                        List<String> executedNodes,
                                        NodeResult result) implements IngestionNodeFeature {

        @Override
        public String name() {
            return nodeType;
        }

        @Override
        public NodeResult execute(IngestionContext context, NodeConfig config) {
            executedNodes.add(config.getNodeType());
            return result;
        }
    }
}
