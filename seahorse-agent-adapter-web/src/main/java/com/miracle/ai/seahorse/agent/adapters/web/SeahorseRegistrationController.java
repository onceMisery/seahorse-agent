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

import com.miracle.ai.seahorse.agent.ports.inbound.auth.RegistrationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RegistrationResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * REST controller for user self-registration.
 *
 * <p>All endpoints live under the {@code /auth/} prefix so that the existing
 * authentication interceptor excludes them automatically.
 */
@RestController
public class SeahorseRegistrationController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String KEY_MESSAGE = "message";
    private static final String SUCCESS_CODE = "0";

    private final ObjectProvider<RegistrationInboundPort> registrationPortProvider;

    public SeahorseRegistrationController(ObjectProvider<RegistrationInboundPort> registrationPortProvider) {
        this.registrationPortProvider = registrationPortProvider;
    }

    /**
     * Send a 6-digit verification code to the given email address.
     */
    @PostMapping("/auth/send-code")
    public Map<String, Object> sendCode(@RequestBody SendCodeRequest request) {
        SendCodeRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        registrationPortProvider.getIfAvailable().sendVerificationCode(safeRequest.getEmail());
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_MESSAGE, "验证码已发送");
    }

    /**
     * Complete the registration: verify code, create account, activate trial.
     */
    @PostMapping("/auth/register")
    public Map<String, Object> register(@RequestBody RegisterRequest request) {
        RegisterRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        RegistrationResult result = registrationPortProvider.getIfAvailable()
                .register(safeRequest.getEmail(), safeRequest.getCode(), safeRequest.getPassword());
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, result);
    }

    /**
     * Check whether the given email is available for registration.
     */
    @GetMapping("/auth/email-available")
    public Map<String, Object> isEmailAvailable(@RequestParam("email") String email) {
        boolean available = registrationPortProvider.getIfAvailable().isEmailAvailable(email);
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, available);
    }

    // ─── request DTOs ─────────────────────────────────────────────────────

    /**
     * Request body for {@code POST /auth/send-code}.
     */
    public static class SendCodeRequest {

        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    /**
     * Request body for {@code POST /auth/register}.
     */
    public static class RegisterRequest {

        private String email;
        private String code;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
