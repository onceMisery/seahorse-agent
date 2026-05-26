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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorOperationRiskMapperTests {

    @Test
    void shouldMapHttpMethodToRiskAndAction() {
        ConnectorOperationRisk get = ConnectorOperationRiskMapper.map(OpenApiHttpMethod.GET);
        ConnectorOperationRisk post = ConnectorOperationRiskMapper.map(OpenApiHttpMethod.POST);
        ConnectorOperationRisk put = ConnectorOperationRiskMapper.map(OpenApiHttpMethod.PUT);
        ConnectorOperationRisk patch = ConnectorOperationRiskMapper.map(OpenApiHttpMethod.PATCH);
        ConnectorOperationRisk delete = ConnectorOperationRiskMapper.map(OpenApiHttpMethod.DELETE);

        assertEquals(ToolActionType.READ, get.actionType());
        assertEquals(ToolRiskLevel.LOW, get.riskLevel());
        assertFalse(get.requiresApproval());

        assertEquals(ToolActionType.WRITE, post.actionType());
        assertEquals(ToolRiskLevel.MEDIUM, post.riskLevel());
        assertEquals(put, post);
        assertEquals(patch, post);

        assertEquals(ToolActionType.DELETE, delete.actionType());
        assertEquals(ToolRiskLevel.HIGH, delete.riskLevel());
        assertTrue(delete.requiresApproval());
    }
}
