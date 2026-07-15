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

public record SandboxRuntimeNodeHeartbeatResult(String status,
                                                String nodeId,
                                                SandboxRuntimeNodeRegistration registration) {

    public static final String STATUS_REGISTERED = "REGISTERED";
    public static final String STATUS_UNSUPPORTED = "UNSUPPORTED";
    public static final String STATUS_CONFLICT = "CONFLICT";
    public static final String STATUS_CLOSED = "CLOSED";

    public static SandboxRuntimeNodeHeartbeatResult registered(SandboxRuntimeNodeRegistration registration) {
        return new SandboxRuntimeNodeHeartbeatResult(STATUS_REGISTERED, registration.nodeId(), registration);
    }

    public static SandboxRuntimeNodeHeartbeatResult unsupported() {
        return new SandboxRuntimeNodeHeartbeatResult(STATUS_UNSUPPORTED, null, null);
    }

    public static SandboxRuntimeNodeHeartbeatResult conflict(String nodeId) {
        return new SandboxRuntimeNodeHeartbeatResult(STATUS_CONFLICT, nodeId, null);
    }

    public static SandboxRuntimeNodeHeartbeatResult closed() {
        return new SandboxRuntimeNodeHeartbeatResult(STATUS_CLOSED, null, null);
    }
}
