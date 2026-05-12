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

package com.miracle.ai.seahorse.agent.kernel.application.trace;

import com.miracle.ai.seahorse.agent.ports.inbound.trace.RagTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.trace.RagTracePageCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;

import java.util.List;
import java.util.Objects;

/**
 * Kernel 层 RAG Trace 查询服务。
 */
public class KernelRagTraceService implements RagTraceInboundPort {

    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 10L;
    private static final long MAX_SIZE = 200L;

    private final RagTraceRepositoryPort repositoryPort;

    public KernelRagTraceService(RagTraceRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

    @Override
    public RagTracePage<RagTraceRun> pageRuns(RagTracePageCommand command) {
        RagTracePageCommand safeCommand = command == null
                ? new RagTracePageCommand(DEFAULT_CURRENT, DEFAULT_SIZE, null, null, null, null)
                : command;
        return repositoryPort.pageRuns(new RagTracePageRequest(
                normalizeCurrent(safeCommand.current()),
                normalizeSize(safeCommand.size()),
                safeCommand.traceId(),
                safeCommand.conversationId(),
                safeCommand.taskId(),
                safeCommand.status()));
    }

    @Override
    public RagTraceDetail detail(String traceId) {
        RagTraceRun run = repositoryPort.findRun(traceId).orElse(null);
        return new RagTraceDetail(run, listNodes(traceId));
    }

    @Override
    public List<RagTraceNode> listNodes(String traceId) {
        return repositoryPort.listNodes(traceId);
    }

    private long normalizeCurrent(long current) {
        return current <= 0 ? DEFAULT_CURRENT : current;
    }

    private long normalizeSize(long size) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
