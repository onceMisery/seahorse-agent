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

import com.miracle.ai.seahorse.agent.ports.inbound.user.ChangePasswordCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.user.UserCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.user.UserInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.user.UserUpdateCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
public class SeahorseUserController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final ObjectProvider<UserInboundPort> userInboundPortProvider;

    public SeahorseUserController(ObjectProvider<UserInboundPort> userInboundPortProvider) {
        this.userInboundPortProvider = userInboundPortProvider;
    }

    @GetMapping("/user/me")
    public Map<String, Object> currentUser() {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, userInboundPortProvider.getIfAvailable().currentUser());
    }

    @GetMapping("/users")
    public Map<String, Object> pageQuery(@RequestParam(required = false, defaultValue = "1") long current,
                                         @RequestParam(required = false, defaultValue = "10") long size,
                                         @RequestParam(required = false) String keyword) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, userInboundPortProvider.getIfAvailable().page(current, size, keyword));
    }

    @PostMapping("/users")
    public Map<String, Object> create(@RequestBody UserSaveRequest request) {
        UserSaveRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Long id = userInboundPortProvider.getIfAvailable().create(new UserCreateCommand(
                safeRequest.getUsername(), safeRequest.getPassword(), safeRequest.getRole(), safeRequest.getAvatar()));
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, id);
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody UserSaveRequest request) {
        UserSaveRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        userInboundPortProvider.getIfAvailable().update(Long.parseLong(id), new UserUpdateCommand(
                safeRequest.getUsername(), safeRequest.getPassword(), safeRequest.getRole(), safeRequest.getAvatar()));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        userInboundPortProvider.getIfAvailable().delete(Long.parseLong(id));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @PutMapping("/user/password")
    public Map<String, Object> changePassword(@RequestBody UserPasswordRequest request) {
        UserPasswordRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        userInboundPortProvider.getIfAvailable().changePassword(new ChangePasswordCommand(
                safeRequest.getCurrentPassword(), safeRequest.getNewPassword()));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }
}
