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

package com.miracle.ai.seahorse.agent.ports.outbound.user;

import com.miracle.ai.seahorse.agent.kernel.domain.user.UserTrial;

import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link UserTrial} records.
 */
public interface TrialRepositoryPort {

    /**
     * Persist a new or updated trial record.
     *
     * @param trial the trial to save
     * @return the saved trial (with generated id if applicable)
     */
    UserTrial save(UserTrial trial);

    /**
     * Find the trial associated with the given user.
     *
     * @param userId the user identifier
     * @return the trial, if any
     */
    Optional<UserTrial> findByUserId(Long userId);

    /**
     * Find the trial associated with the given tenant.
     *
     * @param tenantId the tenant identifier
     * @return the trial, if any
     */
    Optional<UserTrial> findByTenantId(String tenantId);

    /**
     * Transition the trial to a new status.
     *
     * @param trialId the trial primary key
     * @param status  the target status (e.g. {@code EXPIRED}, {@code CONVERTED})
     */
    void updateStatus(Long trialId, String status);
}
