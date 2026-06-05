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

package com.miracle.ai.seahorse.agent.ports.outbound.credential;

import com.miracle.ai.seahorse.agent.kernel.domain.credential.SecretMetadata;
import com.miracle.ai.seahorse.agent.kernel.domain.credential.SecretStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Query and status-update port for secret metadata.
 * Implementations back this with a relational table or document store.
 */
public interface SecretMetadataQueryPort {

    /**
     * Find secret metadata by tenant and reference.
     */
    Optional<SecretMetadata> findByRef(String tenantId, String secretRef);

    /**
     * Update the lifecycle status of a secret.
     *
     * @param secretRef  the secret reference
     * @param status     new status
     * @param rotatedBy  identifier of the actor performing the rotation (may be null)
     * @param rotatedAt  timestamp of the status change
     */
    void updateStatus(String secretRef, SecretStatus status, String rotatedBy, Instant rotatedAt);

    /**
     * Find all secrets whose expiresAt is before the given cutoff and are still ACTIVE.
     */
    List<SecretMetadata> findExpiredBefore(Instant cutoff);
}
