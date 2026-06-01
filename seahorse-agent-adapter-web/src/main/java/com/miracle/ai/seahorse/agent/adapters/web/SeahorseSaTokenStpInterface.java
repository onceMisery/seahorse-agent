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

import cn.dev33.satoken.stp.StpInterface;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SeahorseSaTokenStpInterface implements StpInterface {

    private final UserRepositoryPort userRepositoryPort;

    public SeahorseSaTokenStpInterface(UserRepositoryPort userRepositoryPort) {
        this.userRepositoryPort = Objects.requireNonNull(userRepositoryPort, "userRepositoryPort must not be null");
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null) {
            return Collections.emptyList();
        }
        return userRepositoryPort.findById(Long.parseLong(loginId.toString()))
                .filter(user -> user.role() != null && !user.role().isBlank())
                .map(user -> List.of(user.role()))
                .orElseGet(Collections::emptyList);
    }
}
