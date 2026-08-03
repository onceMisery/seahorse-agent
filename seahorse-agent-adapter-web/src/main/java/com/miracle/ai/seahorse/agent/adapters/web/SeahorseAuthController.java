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
import com.miracle.ai.seahorse.agent.ports.inbound.auth.RefreshTokenCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.IpGeolocationPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
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

    private final AuthInboundPort authInboundPort;
    private final ObjectProvider<IpGeolocationPort> ipGeolocationPortProvider;

    public SeahorseAuthController(AuthInboundPort authInboundPort,
                                  ObjectProvider<IpGeolocationPort> ipGeolocationPortProvider) {
        this.authInboundPort = Objects.requireNonNull(authInboundPort, "authInboundPort must not be null");
        this.ipGeolocationPortProvider = ipGeolocationPortProvider;
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody @Valid AuthLoginRequest request, HttpServletRequest httpRequest) {
        AuthLoginRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String ipAddress = extractIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String deviceInfo = withOptionalGeolocation(UserAgentParser.parse(userAgent), ipAddress);
        LoginCommand command = new LoginCommand(
                safeRequest.getUsername(),
                safeRequest.getPassword(),
                ipAddress,
                userAgent,
                deviceInfo);
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, authInboundPort.login(command));
    }

    @PostMapping("/auth/refresh")
    public Map<String, Object> refresh(@RequestBody @Valid AuthRefreshRequest request) {
        AuthRefreshRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                authInboundPort.refresh(new RefreshTokenCommand(safeRequest.getRefreshToken())));
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout(@RequestBody(required = false) @Valid AuthRefreshRequest request) {
        authInboundPort.logout(request != null ? request.getRefreshToken() : null);
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    private String withOptionalGeolocation(String deviceInfo, String ipAddress) {
        if (ipGeolocationPortProvider == null || ipAddress == null) {
            return deviceInfo;
        }
        IpGeolocationPort geoPort = ipGeolocationPortProvider.getIfAvailable();
        if (geoPort == null) {
            return deviceInfo;
        }
        try {
            IpGeolocationPort.GeoInfo geoInfo = geoPort.resolve(ipAddress);
            return geoInfo != null ? deviceInfo + " (" + geoInfo.toDisplayString() + ")" : deviceInfo;
        } catch (RuntimeException ex) {
            return deviceInfo;
        }
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
