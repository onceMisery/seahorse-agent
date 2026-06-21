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

package com.miracle.ai.seahorse.agent.ports.inbound.runprofile;

import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;

import java.util.List;
import java.util.Optional;

public interface RunProfileInboundPort {

    List<RunProfileRecord> list(String userId);

    default List<String> supportedExecutorEngines() {
        return List.of("kernel");
    }

    Optional<RunProfileDetails> findById(String userId, Long id);

    default Optional<RunProfileResolvedPreview> resolvePreview(String userId, Long id) {
        return Optional.empty();
    }

    default Optional<RunProfileRiskSummary> riskSummary(String userId, Long id) {
        return Optional.empty();
    }

    default Optional<RunProfileProductionGateCheck> productionGateCheck(String userId, Long id) {
        return Optional.empty();
    }

    default void submitApproval(String userId, Long id, String comment) {
        throw new UnsupportedOperationException("run profile approval is not supported");
    }

    default void approve(String userId, Long id, String operator, String comment) {
        throw new UnsupportedOperationException("run profile approval is not supported");
    }

    default void reject(String userId, Long id, String operator, String comment) {
        throw new UnsupportedOperationException("run profile approval is not supported");
    }

    default Optional<RunProfileAuditSummary> auditSummary(String userId, Long id) {
        return Optional.empty();
    }

    default RunProfileResolvedPreview applyToConversation(String userId, String conversationId, Long id) {
        throw new UnsupportedOperationException("conversation run profile binding is not supported");
    }

    default Optional<RunProfileDetails> findAppliedToConversation(String userId, String conversationId) {
        return Optional.empty();
    }

    Long save(RunProfileCommand command);

    void activate(String userId, Long id);

    void delete(String userId, Long id);
}
