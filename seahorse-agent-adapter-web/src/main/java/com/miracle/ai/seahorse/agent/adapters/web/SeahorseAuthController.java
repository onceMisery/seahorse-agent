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

import com.miracle.ai.seahorse.agent.ports.inbound.auth.AuthInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.LoginCommand;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
@ConditionalOnBean(AuthInboundPort.class)
public class SeahorseAuthController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final AuthInboundPort authInboundPort;

    public SeahorseAuthController(AuthInboundPort authInboundPort) {
        this.authInboundPort = Objects.requireNonNull(authInboundPort, "authInboundPort must not be null");
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody AuthLoginRequest request) {
        AuthLoginRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                authInboundPort.login(new LoginCommand(safeRequest.getUsername(), safeRequest.getPassword())));
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout() {
        authInboundPort.logout();
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }
}
