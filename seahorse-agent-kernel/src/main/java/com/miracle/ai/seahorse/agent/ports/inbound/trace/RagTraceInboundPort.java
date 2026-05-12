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

package com.miracle.ai.seahorse.agent.ports.inbound.trace;

import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;

import java.util.List;

/**
 * RAG Trace 查询入站端口。
 */
public interface RagTraceInboundPort {

    RagTracePage<RagTraceRun> pageRuns(RagTracePageCommand command);

    RagTraceDetail detail(String traceId);

    List<RagTraceNode> listNodes(String traceId);
}
