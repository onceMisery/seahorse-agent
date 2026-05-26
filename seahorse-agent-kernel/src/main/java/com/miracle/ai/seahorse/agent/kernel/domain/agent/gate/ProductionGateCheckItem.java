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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.gate;

import java.util.Objects;

public record ProductionGateCheckItem(ProductionGateCheckCode code,
                                      ProductionGateStatus status,
                                      String message) {

    public ProductionGateCheckItem {
        code = Objects.requireNonNull(code, "code must not be null");
        status = Objects.requireNonNullElse(status, ProductionGateStatus.FAIL);
        message = defaultText(message, code.name());
    }

    public static ProductionGateCheckItem pass(ProductionGateCheckCode code, String message) {
        return new ProductionGateCheckItem(code, ProductionGateStatus.PASS, message);
    }

    public static ProductionGateCheckItem warn(ProductionGateCheckCode code, String message) {
        return new ProductionGateCheckItem(code, ProductionGateStatus.WARN, message);
    }

    public static ProductionGateCheckItem fail(ProductionGateCheckCode code, String message) {
        return new ProductionGateCheckItem(code, ProductionGateStatus.FAIL, message);
    }

    private static String defaultText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
