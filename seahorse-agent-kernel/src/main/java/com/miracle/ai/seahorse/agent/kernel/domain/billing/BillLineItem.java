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
 * Individual line item within a bill, representing a specific charge.
 *
 * @param id          primary key
 * @param billId      parent bill foreign key
 * @param itemType    charge type: SUBSCRIPTION_FEE / TOKEN_USAGE / CALL_USAGE
 * @param description human-readable charge description
 * @param amount      charge amount
 * @param quantity    usage quantity (tokens, calls, etc.)
 */
public record BillLineItem(Long id,
                           Long billId,
                           String itemType,
                           String description,
                           BigDecimal amount,
                           long quantity) {

    public static final String TYPE_SUBSCRIPTION_FEE = "SUBSCRIPTION_FEE";
    public static final String TYPE_TOKEN_USAGE = "TOKEN_USAGE";
    public static final String TYPE_CALL_USAGE = "CALL_USAGE";
}
