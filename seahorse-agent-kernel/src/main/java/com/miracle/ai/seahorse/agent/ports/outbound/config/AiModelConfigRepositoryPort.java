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

package com.miracle.ai.seahorse.agent.ports.outbound.config;

import com.miracle.ai.seahorse.agent.kernel.model.AiModelConfig;

import java.util.List;
import java.util.Optional;

public interface AiModelConfigRepositoryPort {

    String DEFAULT_TENANT_ID = "default";

    List<AiModelConfig> findAll();

    default List<AiModelConfig> findAll(String tenantId) {
        return findAll();
    }

    Optional<AiModelConfig> findByKey(String configKey);

    default Optional<AiModelConfig> findByKey(String tenantId, String configKey) {
        return findByKey(configKey);
    }

    void save(AiModelConfig config);

    void update(String configKey, String configValue, String updatedBy);

    default void update(String tenantId, String configKey, String configValue, String updatedBy) {
        update(configKey, configValue, updatedBy);
    }

    void delete(String configKey);

    default void delete(String tenantId, String configKey) {
        delete(configKey);
    }
}
