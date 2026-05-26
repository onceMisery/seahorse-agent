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

package com.miracle.ai.seahorse.agent.kernel.application.agent.connector;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperationRisk;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiHttpMethod;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;

import java.util.Objects;

final class ConnectorOperationRiskMapper {

    private ConnectorOperationRiskMapper() {
    }

    static ConnectorOperationRisk map(OpenApiHttpMethod method) {
        return switch (Objects.requireNonNull(method, "method must not be null")) {
            case GET -> new ConnectorOperationRisk(ToolRiskLevel.LOW, ToolActionType.READ, false);
            case POST, PUT, PATCH -> new ConnectorOperationRisk(ToolRiskLevel.MEDIUM, ToolActionType.WRITE, false);
            case DELETE -> new ConnectorOperationRisk(ToolRiskLevel.HIGH, ToolActionType.DELETE, true);
        };
    }
}
