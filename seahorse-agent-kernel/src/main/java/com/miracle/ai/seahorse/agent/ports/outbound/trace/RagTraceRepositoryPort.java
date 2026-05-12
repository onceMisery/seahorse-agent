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

package com.miracle.ai.seahorse.agent.ports.outbound.trace;

import java.util.List;
import java.util.Optional;

/**
 * RAG Trace DB 仓储端口。
 *
 * <p>DB Trace 是管理后台产品功能，不能被指标观测端口替代。
 */
public interface RagTraceRepositoryPort {

    RagTracePage<RagTraceRun> pageRuns(RagTracePageRequest request);

    Optional<RagTraceRun> findRun(String traceId);

    List<RagTraceNode> listNodes(String traceId);

    void startRun(RagTraceRun run);

    void finishRun(RagTraceRunFinish finish);

    void startNode(RagTraceNode node);

    void finishNode(RagTraceNodeFinish finish);
}
