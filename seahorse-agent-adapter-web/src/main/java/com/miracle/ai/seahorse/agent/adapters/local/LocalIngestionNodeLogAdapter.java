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

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.IngestionContext;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeConfig;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeLog;
import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionNodeLogPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 原生入库节点日志适配器。
 *
 * <p>该适配器不依赖旧 NodeOutputExtractor，只记录通用执行信息。需要节点输出时可由具体 L3 adapter
 * 替换该端口实现。
 */
public class LocalIngestionNodeLogAdapter implements IngestionNodeLogPort {

    @Override
    public void record(IngestionContext context, NodeConfig config, NodeResult result, long durationMs) {
        IngestionContext safeContext = Objects.requireNonNull(context, "context must not be null");
        NodeConfig safeConfig = Objects.requireNonNull(config, "config must not be null");
        List<NodeLog> logs = safeContext.getLogs();
        if (logs == null) {
            logs = new ArrayList<>();
            safeContext.setLogs(logs);
        }
        logs.add(toLog(safeConfig, result, durationMs));
    }

    private NodeLog toLog(NodeConfig config, NodeResult result, long durationMs) {
        Throwable error = result == null ? null : result.getError();
        return NodeLog.builder()
                .nodeId(config.getNodeId())
                .nodeType(config.getNodeType())
                .message(result == null ? null : result.getMessage())
                .durationMs(durationMs)
                .success(result != null && result.isSuccess())
                .error(error == null ? null : error.getMessage())
                .build();
    }
}
