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

import com.miracle.ai.seahorse.agent.kernel.domain.billing.BillLineItem;

import java.util.List;

/**
 * Outbound port for bill line item persistence.
 */
public interface BillLineItemRepositoryPort {

    /**
     * Saves (inserts) a bill line item.
     *
     * @param item the line item to save
     * @return the saved line item
     */
    BillLineItem save(BillLineItem item);

    /**
     * Finds all line items for a given bill.
     *
     * @param billId the bill primary key
     * @return line items for the bill
     */
    List<BillLineItem> findByBillId(Long billId);
}
