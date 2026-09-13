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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentCollaborationPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffFailureCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAgentCollaborationPolicyAdapterTests {

    private JdbcAgentCollaborationPolicyAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:agent-collab-policy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Connection connection = java.sql.DriverManager.getConnection(url, "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sa_agent_collaboration_policy (
                        policy_id       VARCHAR(64)  PRIMARY KEY,
                        tenant_id       VARCHAR(64)  NOT NULL,
                        source_agent_id VARCHAR(64)  NOT NULL,
                        target_agent_id VARCHAR(64)  NOT NULL,
                        allowed         BOOLEAN      NOT NULL,
                        max_depth       INTEGER,
                        created_at      TIMESTAMP    NOT NULL,
                        updated_at      TIMESTAMP    NOT NULL
                    )
                    """);
        }
        adapter = new JdbcAgentCollaborationPolicyAdapter(new DriverManagerDataSource(url, "sa", ""));
    }

    @Test
    void shouldAllowWhenNoPolicyExistsAndDenyWhenExplicitPolicyDenies() {
        assertTrue(adapter.decide(request(1)).allowed());

        adapter.savePolicy(new AgentCollaborationPolicy(
                "p-1", "tenant-1", "agent-a", "agent-b", false, null));

        AgentCollaborationPolicyDecision decision = adapter.decide(request(1));
        assertFalse(decision.allowed());
        assertEquals(AgentHandoffFailureCode.POLICY_DENIED, decision.failureCode());
    }

    @Test
    void shouldEnforcePolicyMaxDepthOnAllowedPair() {
        adapter.savePolicy(new AgentCollaborationPolicy(
                "p-1", "tenant-1", "agent-a", "agent-b", true, 1));

        assertTrue(adapter.decide(request(1)).allowed());
        assertFalse(adapter.decide(request(2)).allowed());
    }

    @Test
    void shouldListAndDeletePoliciesByTenant() {
        adapter.savePolicy(new AgentCollaborationPolicy(
                "p-1", "tenant-1", "agent-a", "agent-b", true, null));
        adapter.savePolicy(new AgentCollaborationPolicy(
                "p-2", "tenant-2", "agent-x", "agent-y", false, null));

        List<AgentCollaborationPolicy> tenantPolicies = adapter.listPolicies("tenant-1");
        assertEquals(1, tenantPolicies.size());
        assertTrue(tenantPolicies.get(0).allowed());

        adapter.deletePolicy("p-1");
        assertEquals(0, adapter.listPolicies("tenant-1").size());
        assertEquals(1, adapter.listPolicies("tenant-2").size());
    }

    private AgentCollaborationPolicyRequest request(int depth) {
        return new AgentCollaborationPolicyRequest(
                "tenant-1", "agent-a", "agent-b", depth, List.of(), List.of());
    }
}
