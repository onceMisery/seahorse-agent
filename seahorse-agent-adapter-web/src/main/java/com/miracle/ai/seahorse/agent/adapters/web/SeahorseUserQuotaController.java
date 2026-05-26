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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.UserQuotaSummary;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaSummaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.UserQuotaSummaryQuery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeahorseUserQuotaController {

    private final ObjectProvider<QuotaSummaryInboundPort> quotaSummaryPortProvider;

    public SeahorseUserQuotaController(ObjectProvider<QuotaSummaryInboundPort> quotaSummaryPortProvider) {
        this.quotaSummaryPortProvider = quotaSummaryPortProvider;
    }

    @GetMapping("/api/me/quota-summary")
    public ApiResponse<UserQuotaSummary> summary(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String taskTemplateId,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId) {
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        UserQuotaSummaryQuery query = new UserQuotaSummaryQuery(resolvedUserId, tenantId, taskTemplateId);
        return ApiResponses.requireService(quotaSummaryPortProvider, port -> port.summary(query));
    }
}
