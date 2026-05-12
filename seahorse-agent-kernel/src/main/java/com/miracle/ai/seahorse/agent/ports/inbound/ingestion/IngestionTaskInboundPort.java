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

package com.miracle.ai.seahorse.agent.ports.inbound.ingestion;

import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskPage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRecord;

import java.util.List;

/**
 * 入库任务管理入口端口。
 */
public interface IngestionTaskInboundPort {

    IngestionTaskExecutionResult execute(IngestionTaskCreateCommand command);

    IngestionTaskExecutionResult upload(IngestionTaskUploadCommand command);

    IngestionTaskRecord get(String taskId);

    List<IngestionTaskNodeRecord> listNodes(String taskId);

    IngestionTaskPage page(long current, long size, String status);
}
