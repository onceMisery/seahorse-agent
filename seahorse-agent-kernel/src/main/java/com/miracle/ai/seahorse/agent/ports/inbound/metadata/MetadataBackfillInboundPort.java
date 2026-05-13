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

package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;

/**
 * 元数据治理回填入站端口。
 *
 * <p>管理端、调度任务和补偿脚本只依赖该端口创建任务、推进批次和控制状态；
 * 具体文件读取、流水线执行和 Review/Quarantine 路由仍留在 kernel 编排内。
 */
public interface MetadataBackfillInboundPort {

    MetadataBackfillJobRecord createJob(MetadataBackfillCommand command);

    MetadataBackfillRunResult runNextBatch(String jobId);

    MetadataBackfillJobRecord getJob(String jobId);

    MetadataBackfillJobRecord pause(String jobId, String operator);

    MetadataBackfillJobRecord resume(String jobId, String operator);

    MetadataBackfillJobRecord cancel(String jobId, String operator);
}
