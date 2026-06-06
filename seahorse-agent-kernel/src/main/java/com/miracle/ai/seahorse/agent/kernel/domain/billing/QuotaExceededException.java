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

import com.miracle.ai.seahorse.agent.kernel.domain.common.exception.SeahorseBusinessException;

/**
 * Thrown when a tenant exceeds their subscription quota limits.
 *
 * <p>Carries a machine-readable {@link #getReasonCode() reasonCode} and a
 * human-readable {@link #getUpgradeHint() upgradeHint} guiding the user
 * toward a higher-tier plan. Maps to HTTP 402 Payment Required.
 */
public class QuotaExceededException extends SeahorseBusinessException {

    private static final String ERROR_CODE = "QUOTA_EXCEEDED";
    private final String reasonCode;
    private final String upgradeHint;

    /**
     * Constructs a new QuotaExceededException.
     *
     * @param reasonCode  machine-readable reason (e.g. TOKEN_LIMIT_EXCEEDED)
     * @param upgradeHint human-readable suggestion to upgrade
     */
    public QuotaExceededException(String reasonCode, String upgradeHint) {
        super(ERROR_CODE, reasonCode + ": " + upgradeHint, 402);
        this.reasonCode = reasonCode;
        this.upgradeHint = upgradeHint;
    }

    /**
     * Constructs a new QuotaExceededException with a cause.
     *
     * @param reasonCode  machine-readable reason
     * @param upgradeHint human-readable suggestion to upgrade
     * @param cause       underlying cause
     */
    public QuotaExceededException(String reasonCode, String upgradeHint, Throwable cause) {
        super(ERROR_CODE, reasonCode + ": " + upgradeHint, 402, cause);
        this.reasonCode = reasonCode;
        this.upgradeHint = upgradeHint;
    }

    /**
     * Returns the machine-readable reason code.
     */
    public String getReasonCode() {
        return reasonCode;
    }

    /**
     * Returns the human-readable upgrade hint.
     */
    public String getUpgradeHint() {
        return upgradeHint;
    }
}
