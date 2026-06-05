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

package com.miracle.ai.seahorse.agent.ports.outbound.billing;

import com.miracle.ai.seahorse.agent.kernel.domain.billing.Bill;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for bill persistence.
 */
public interface BillRepositoryPort {

    /**
     * Saves (inserts or updates) a bill.
     *
     * @param bill the bill to save
     * @return the saved bill
     */
    Bill save(Bill bill);

    /**
     * Finds all bills for a tenant, ordered by period descending.
     *
     * @param tenantId the tenant identifier
     * @return bills for the tenant
     */
    List<Bill> findByTenantId(String tenantId);

    /**
     * Finds a bill by its unique bill number.
     *
     * @param billNo the bill number
     * @return the bill, or empty if not found
     */
    Optional<Bill> findByBillNo(String billNo);

    /**
     * Checks whether a bill already exists for the given tenant and period.
     *
     * @param tenantId   the tenant identifier
     * @param billPeriod the billing period (yyyy-MM)
     * @return {@code true} if a bill already exists
     */
    boolean existsForPeriod(String tenantId, String billPeriod);
}
