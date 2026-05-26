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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sre;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record SreHealthReport(String reportId,
                              SreHealthStatus status,
                              List<SreHealthItem> items,
                              Instant checkedAt) {

    public SreHealthReport {
        reportId = requireText(reportId, "reportId must not be blank");
        items = items == null ? List.of() : List.copyOf(items);
        status = status == null ? aggregate(items) : status;
        checkedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
    }

    private static SreHealthStatus aggregate(List<SreHealthItem> items) {
        SreHealthStatus worst = SreHealthStatus.GREEN;
        for (SreHealthItem item : items) {
            if (item.status().isMoreSevereThan(worst)) {
                worst = item.status();
            }
        }
        return worst;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
