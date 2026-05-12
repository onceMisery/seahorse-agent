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

package com.miracle.ai.seahorse.agent.ports.outbound.ingestion;

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition;

import java.util.Optional;

/**
 * 入库流水线定义仓储端口。
 */
public interface PipelineDefinitionRepositoryPort {

    /**
     * 按 ID 查询流水线定义。
     *
     * @param pipelineId 流水线 ID
     * @return 流水线定义
     */
    Optional<PipelineDefinition> findById(String pipelineId);
}
