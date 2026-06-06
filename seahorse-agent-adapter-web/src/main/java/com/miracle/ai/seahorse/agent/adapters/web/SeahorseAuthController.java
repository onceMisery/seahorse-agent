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

import com.miracle.ai.seahorse.agent.kernel.application.auth.UserAgentParser;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.AuthInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.LoginCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.IpGeolocationPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
public class SeahorseAuthController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final ObjectProvider<AuthInboundPort> authInboundPortProvider;
    private final ObjectProvider<IpGeolocationPort> ipGeolocationPortProvider;

    public SeahorseAuthController(ObjectProvider<AuthInboundPort> authInboundPortProvider) {
        this(authInboundPortProvider, null);
    }

    @Autowired
    public SeahorseAuthController(ObjectProvider<AuthInboundPort> authInboundPortProvider,
                                  ObjectProvider<IpGeolocationPort> ipGeolocationPortProvider) {
        this.authInboundPortProvider = authInboundPortProvider;
        this.ipGeolocationPortProvider = ipGeolocationPortProvider;
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody @Valid AuthLoginRequest request, HttpServletRequest httpRequest) {
        AuthLoginRequest safeRequest = Objects.requireNonNull(request, "request must not be null");

        String ipAddress = extractIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String deviceInfo = UserAgentParser.parse(userAgent);

        // Optionally resolve IP geolocation (graceful degradation if not available)
        if (ipGeolocationPortProvider != null && ipAddress != null) {
            IpGeolocationPort geoPort = ipGeolocationPortProvider.getIfAvailable();
            if (geoPort != null) {
                try {
                    IpGeolocationPort.GeoInfo geoInfo = geoPort.resolve(ipAddress);
                    if (geoInfo != null) {
                        deviceInfo = deviceInfo + " (" + geoInfo.toDisplayString() + ")";
                    }
                } catch (Exception e) {
                    // Graceful degradation: continue without geo info
                }
            }
        }

        LoginCommand command = new LoginCommand(
                safeRequest.getUsername(),
                safeRequest.getPassword(),
                ipAddress,
                userAgent,
                deviceInfo
        );

        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                authInboundPortProvider.getIfAvailable().login(command));
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout() {
        authInboundPortProvider.getIfAvailable().logout();
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            int commaIdx = xForwardedFor.indexOf(',');
            return commaIdx > 0 ? xForwardedFor.substring(0, commaIdx).trim() : xForwardedFor.trim();
        }
        return request.getRemoteAddr();
    }
}
