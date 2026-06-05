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

/**
 * Immutable definition of a subscription plan tier with pricing and quota limits.
 *
 * @param id                primary key
 * @param code              plan tier code
 * @param name              display name
 * @param description       human-readable description
 * @param monthlyPrice      monthly subscription price
 * @param yearlyPrice       yearly subscription price
 * @param tokenLimit        maximum tokens per billing period
 * @param storageLimitBytes maximum storage in bytes
 * @param concurrencyLimit  maximum concurrent agent runs
 * @param active            whether this plan is currently offered
 */
public record SubscriptionPlan(Long id,
                               PlanCode code,
                               String name,
                               String description,
                               BigDecimal monthlyPrice,
                               BigDecimal yearlyPrice,
                               long tokenLimit,
                               long storageLimitBytes,
                               int concurrencyLimit,
                               boolean active) {
}
