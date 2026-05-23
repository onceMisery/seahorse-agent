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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunLease;

import java.time.Instant;
import java.util.Optional;

public interface AgentRunLeaseRepositoryPort {

    boolean acquire(String runId, String workerId, Instant leaseUntil, Instant now);

    boolean heartbeat(String runId, String workerId, Instant leaseUntil, Instant now);

    boolean release(String runId, String workerId);

    Optional<AgentRunLease> findByRunId(String runId);

    static AgentRunLeaseRepositoryPort empty() {
        return new AgentRunLeaseRepositoryPort() {
            @Override
            public boolean acquire(String runId, String workerId, Instant leaseUntil, Instant now) {
                return true;
            }

            @Override
            public boolean heartbeat(String runId, String workerId, Instant leaseUntil, Instant now) {
                return true;
            }

            @Override
            public boolean release(String runId, String workerId) {
                return true;
            }

            @Override
            public Optional<AgentRunLease> findByRunId(String runId) {
                return Optional.empty();
            }
        };
    }
}
