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

package com.miracle.ai.seahorse.agent.kernel.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 模型配置实体
 */
@Setter
@Getter
public class AiModelConfig {

    private String id;
    private String configKey;
    private String configValue;
    private ConfigType configType;
    private boolean encrypted;
    private String description;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean deleted;

    public enum ConfigType {
        STRING,
        INTEGER,
        BOOLEAN,
        JSON
    }

    public AiModelConfig() {
    }

}
