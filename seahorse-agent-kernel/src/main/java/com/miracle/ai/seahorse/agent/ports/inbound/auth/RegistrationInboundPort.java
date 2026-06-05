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

package com.miracle.ai.seahorse.agent.ports.inbound.auth;

/**
 * Inbound port for user self-registration with email verification.
 */
public interface RegistrationInboundPort {

    /**
     * Generate a 6-digit verification code and send it to the given email.
     *
     * @param email the recipient email address (also used as the login username)
     */
    void sendVerificationCode(String email);

    /**
     * Complete the registration flow: verify the code, provision a tenant,
     * create the user account, activate the free trial, and return a login token.
     *
     * @param email    the email address that received the code
     * @param code     the 6-digit verification code
     * @param password the user-chosen password
     * @return a {@link RegistrationResult} containing the new user/token information
     */
    RegistrationResult register(String email, String code, String password);

    /**
     * Check whether the given email address is available for registration.
     *
     * @param email the email address to check
     * @return {@code true} if no account exists with this email
     */
    boolean isEmailAvailable(String email);
}
