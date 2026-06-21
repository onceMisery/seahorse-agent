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

import com.miracle.ai.seahorse.agent.ports.inbound.rolecard.RoleCardCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.rolecard.RoleCardInboundPort;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * Web adapter for runtime role-card management.
 */
@RestController
@RequiredArgsConstructor
public class SeahorseRoleCardController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    @NonNull
    private final ObjectProvider<RoleCardInboundPort> roleCardPortProvider;

    @GetMapping({"/role-cards", "/api/role-cards"})
    public Map<String, Object> list(@RequestParam(required = false) String userId,
                                    @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                    String headerUserId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, roleCardPort().list(resolveUserId(userId, headerUserId)));
    }

    @PostMapping({"/role-cards", "/api/role-cards"})
    public Map<String, Object> create(@RequestBody RoleCardRequest request,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        RoleCardRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String resolvedUserId = resolveUserId(userId, headerUserId);
        Long id = roleCardPort().save(new RoleCardCommand(
                null,
                resolvedUserId,
                safeRequest.getName(),
                safeRequest.getDefinition(),
                safeRequest.getAvatarRef(),
                safeRequest.isHigherPermEnabled()));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, id);
    }

    @PutMapping({"/role-cards/{roleCardId}", "/api/role-cards/{roleCardId}"})
    public Map<String, Object> update(@PathVariable Long roleCardId,
                                      @RequestBody RoleCardRequest request,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        RoleCardRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String resolvedUserId = resolveUserId(userId, headerUserId);
        Long id = roleCardPort().save(new RoleCardCommand(
                roleCardId,
                resolvedUserId,
                safeRequest.getName(),
                safeRequest.getDefinition(),
                safeRequest.getAvatarRef(),
                safeRequest.isHigherPermEnabled()));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, id);
    }

    @PutMapping({"/role-cards/{roleCardId}/activate", "/api/role-cards/{roleCardId}/activate"})
    public Map<String, Object> activate(@PathVariable Long roleCardId,
                                        @RequestParam(required = false) String userId,
                                        @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                        String headerUserId) {
        roleCardPort().activate(resolveUserId(userId, headerUserId), roleCardId);
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @DeleteMapping({"/role-cards/{roleCardId}", "/api/role-cards/{roleCardId}"})
    public Map<String, Object> delete(@PathVariable Long roleCardId,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        roleCardPort().delete(resolveUserId(userId, headerUserId), roleCardId);
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    private String resolveUserId(String userId, String headerUserId) {
        return WebUserIdResolver.resolve(userId, headerUserId);
    }

    private RoleCardInboundPort roleCardPort() {
        RoleCardInboundPort port = roleCardPortProvider == null ? null : roleCardPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("RoleCardInboundPort is not configured");
        }
        return port;
    }
}
