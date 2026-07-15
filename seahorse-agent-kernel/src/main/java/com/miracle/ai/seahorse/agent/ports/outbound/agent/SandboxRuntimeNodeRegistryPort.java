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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeRegistration;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeEndpoint;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface SandboxRuntimeNodeRegistryPort {

    Optional<SandboxRuntimeNodeRegistration> heartbeat(SandboxRuntimeNodeRegistration registration,
                                                       String ownerId,
                                                       Duration leaseTtl);

    default Optional<SandboxRuntimeNodeRegistration> heartbeat(SandboxRuntimeNodeRegistration registration,
                                                               String ownerId,
                                                               String transportUri,
                                                               Duration leaseTtl) {
        return heartbeat(registration, ownerId, leaseTtl);
    }

    default Optional<SandboxRuntimeNodeEndpoint> findLiveEndpoint(String nodeId) {
        return Optional.empty();
    }

    default List<SandboxRuntimeNodeEndpoint> listLiveEndpoints() {
        return List.of();
    }

    default boolean isLiveOwner(String nodeId, String ownerId) {
        return false;
    }

    default boolean reserveOperationLease(String nodeId, String ownerId, Duration leaseTtl) {
        return false;
    }

    default int deleteStaleRegistrations(Duration retention, int limit) {
        return 0;
    }

    boolean release(String nodeId, String ownerId);

    List<SandboxRuntimeNodeRegistration> listRegistrations(int limit);
}
