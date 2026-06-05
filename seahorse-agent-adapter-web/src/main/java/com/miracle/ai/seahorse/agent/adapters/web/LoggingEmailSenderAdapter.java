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

import com.miracle.ai.seahorse.agent.ports.outbound.email.EmailSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-time {@link EmailSenderPort} adapter that logs email content
 * instead of actually sending messages.
 *
 * <p>Replace this adapter with a production implementation (SMTP / API) in
 * real environments.
 */
public class LoggingEmailSenderAdapter implements EmailSenderPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSenderAdapter.class);

    @Override
    public void sendVerificationCode(String email, String code) {
        log.info("[EMAIL] Verification code for {}: {}", email, code);
    }

    @Override
    public void sendTrialExpiringNotice(String email, String username, long daysRemaining) {
        log.info("[EMAIL] Trial expiring notice for {} ({}): {} days remaining", email, username, daysRemaining);
    }
}
