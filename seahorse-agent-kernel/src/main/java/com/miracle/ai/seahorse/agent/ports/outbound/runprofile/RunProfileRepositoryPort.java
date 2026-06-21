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

package com.miracle.ai.seahorse.agent.ports.outbound.runprofile;

import java.util.List;
import java.util.Optional;

public interface RunProfileRepositoryPort {

    List<RunProfileRecord> listByUser(String userId);

    Optional<RunProfileRecord> findById(String userId, Long id);

    Long save(RunProfileRecord record);

    void replaceTools(Long profileId, List<RunProfileToolBindingRecord> tools);

    List<RunProfileToolBindingRecord> listTools(Long profileId);

    void disableAll(String userId);

    void setEnabled(String userId, Long id, boolean enabled);

    void delete(String userId, Long id);

    default void updateApprovalStatus(
            String userId,
            Long id,
            String approvalStatus,
            String approvalOperator,
            String approvalComment) {
        throw new UnsupportedOperationException("run profile approval status is not supported");
    }

    default void applyToConversation(String userId, String conversationId, Long profileId) {
        throw new UnsupportedOperationException("conversation run profile binding is not supported");
    }

    default Optional<Long> findAppliedProfileId(String userId, String conversationId) {
        return Optional.empty();
    }
}
