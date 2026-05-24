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

package com.miracle.ai.seahorse.agent.kernel.application.agent.context;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextResourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAccessReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceRef;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAccessPolicyPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AuditedResourceAccessPolicyPortTests {

    private static final Instant NOW = Instant.parse("2026-05-24T00:00:00Z");

    @Test
    void shouldRecordDelegateDecision() {
        AccessDecision decision = new AccessDecision(
                "decision-1",
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                ContextResourceType.MEMORY.value(),
                "memory-1",
                AccessDecisionEffect.ALLOW,
                ResourceAccessReasonCodes.OWNER_MATCH,
                NOW);
        ResourceAccessPolicyPort delegate = request -> decision;
        RecordingAccessDecisionLogPort logPort = new RecordingAccessDecisionLogPort();
        AuditedResourceAccessPolicyPort policy = new AuditedResourceAccessPolicyPort(delegate, logPort);

        AccessDecision result = policy.decide(new ResourceAccessRequest(
                "tenant-1",
                AccessSubjectType.USER_DELEGATED_AGENT,
                "user-1",
                ResourceAction.READ,
                new ResourceRef(ContextResourceType.MEMORY, "memory-1", "tenant-1", "user-1", "{}")));

        assertSame(decision, result);
        assertSame(decision, logPort.recordedDecision);
        assertEquals(1, logPort.recordCount);
    }

    private static final class RecordingAccessDecisionLogPort implements AccessDecisionLogPort {

        private AccessDecision recordedDecision;
        private int recordCount;

        @Override
        public void record(AccessDecision decision) {
            recordedDecision = decision;
            recordCount++;
        }
    }
}
