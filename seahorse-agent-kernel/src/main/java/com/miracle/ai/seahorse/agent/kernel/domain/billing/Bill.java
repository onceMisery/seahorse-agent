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

package com.miracle.ai.seahorse.agent.kernel.domain.billing;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Monthly bill for a tenant, aggregating all charges for a billing period.
 *
 * @param id          primary key
 * @param billNo      unique bill number
 * @param tenantId    owning tenant
 * @param billPeriod  billing period in yyyy-MM format
 * @param totalAmount total charges for the period
 * @param status      bill status: GENERATED / PAID / OVERDUE
 * @param generatedAt bill generation timestamp
 * @param dueAt       payment due date
 */
public record Bill(Long id,
                   String billNo,
                   String tenantId,
                   String billPeriod,
                   BigDecimal totalAmount,
                   String status,
                   Instant generatedAt,
                   Instant dueAt) {

    public static final String STATUS_GENERATED = "GENERATED";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_OVERDUE = "OVERDUE";
}
