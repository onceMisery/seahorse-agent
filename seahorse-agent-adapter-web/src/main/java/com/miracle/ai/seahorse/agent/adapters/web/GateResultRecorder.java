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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.inbound.gate.GateResult;
import com.miracle.ai.seahorse.agent.ports.inbound.gate.GateResultInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class GateResultRecorder {

    private final ObjectProvider<GateResultInboundPort> gateResultPortProvider;

    public GateResultRecorder(ObjectProvider<GateResultInboundPort> gateResultPortProvider) {
        this.gateResultPortProvider = gateResultPortProvider;
    }

    public GateResult record(GateResult result) {
        return record(result, null);
    }

    public GateResult record(GateResult result, String sourceTenantId) {
        GateResult safeResult = Objects.requireNonNull(result, "result must not be null");
        if (gateResultPortProvider == null) {
            return safeResult;
        }
        GateResultInboundPort port = gateResultPortProvider.getIfAvailable();
        if (port == null) {
            return safeResult;
        }
        if (sourceTenantId == null || sourceTenantId.isBlank()) {
            return port.save(safeResult);
        }
        String previousTenantId = TenantContext.capture();
        try {
            TenantContext.set(sourceTenantId.trim());
            return port.save(safeResult);
        } finally {
            TenantContext.restore(previousTenantId);
        }
    }

    public static GateResultRecorder passthrough() {
        return new GateResultRecorder(null);
    }
}
