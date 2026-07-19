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

package com.miracle.ai.seahorse.agent.kernel.application.gate;

import com.miracle.ai.seahorse.agent.ports.inbound.gate.GateResult;
import com.miracle.ai.seahorse.agent.ports.inbound.gate.GateResultInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.gate.GateResultRepositoryPort;

import java.util.Objects;
import java.util.Optional;

public class KernelGateResultService implements GateResultInboundPort {

    private final GateResultRepositoryPort repository;

    public KernelGateResultService(GateResultRepositoryPort repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public GateResult save(GateResult result) {
        return repository.save(Objects.requireNonNull(result, "result must not be null"));
    }

    @Override
    public Optional<GateResult> latest(String subjectType, String subjectId) {
        return repository.latest(subjectType, subjectId);
    }
}
