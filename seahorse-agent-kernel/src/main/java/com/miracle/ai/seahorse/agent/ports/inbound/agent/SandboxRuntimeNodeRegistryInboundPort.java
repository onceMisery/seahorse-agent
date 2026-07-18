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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeRegistration;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeAdmissionOverride;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantConstants;

import java.time.Duration;
import java.util.List;

public interface SandboxRuntimeNodeRegistryInboundPort {

    SandboxRuntimeNodeHeartbeatResult heartbeat(Duration leaseTtl);

    List<SandboxRuntimeNodeRegistration> listRegistrations(int limit);

    default SandboxRuntimeNodeAdmissionOverride setOperatorDraining(String nodeId,
                                                                    boolean draining,
                                                                    String operatorId) {
        return setOperatorDraining(nodeId, draining, operatorId, TenantConstants.DEFAULT_TENANT_ID);
    }

    default SandboxRuntimeNodeAdmissionOverride setOperatorDraining(String nodeId,
                                                                    boolean draining,
                                                                    String operatorId,
                                                                    String tenantId) {
        throw new UnsupportedOperationException("sandbox runtime node admission control is unavailable");
    }

    default int cleanupStaleRegistrations(Duration retention, int limit) {
        return 0;
    }
}
