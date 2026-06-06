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

package com.miracle.ai.seahorse.agent.kernel.domain.common.exception;

/**
 * 外部服务调用失败时抛出（如 AI 模型、向量数据库等）。映射到 HTTP 502。
 */
public class ExternalServiceException extends SeahorseException {

    private static final String DEFAULT_CODE = "EXTERNAL_SERVICE_ERROR";
    private final String serviceName;

    public ExternalServiceException(String serviceName, String message) {
        super(DEFAULT_CODE, "External service '" + serviceName + "' error: " + message);
        this.serviceName = serviceName;
    }

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super(DEFAULT_CODE, "External service '" + serviceName + "' error: " + message, cause);
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }
}
