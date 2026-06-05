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

package com.miracle.ai.seahorse.agent.ports.outbound.tenant;

/**
 * Outbound port for tenant lifecycle operations (provision / de-provision).
 *
 * <p>Implementations may create database schemas, initialize default data,
 * or simply return a generated tenant identifier.
 */
public interface TenantProvisioningPort {

    /**
     * Provision a new tenant for the given owner.
     *
     * @param ownerUserId the user-id of the tenant owner
     * @param ownerEmail  the owner email (used for display / notifications)
     * @return the newly created tenant identifier
     */
    String provisionTenant(String ownerUserId, String ownerEmail);

    /**
     * Tear down the given tenant and all associated resources.
     *
     * @param tenantId the tenant to remove
     */
    void deprovisionTenant(String tenantId);
}
