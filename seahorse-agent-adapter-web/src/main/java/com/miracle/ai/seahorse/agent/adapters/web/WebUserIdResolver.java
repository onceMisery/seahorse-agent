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

import cn.dev33.satoken.stp.StpUtil;

final class WebUserIdResolver {

    static final String DEFAULT_USER_ID = "default";
    static final String HEADER_USER_ID = "X-User-Id";

    private WebUserIdResolver() {
    }

    static String resolve(String userId, String headerUserId) {
        String explicitUserId = trimToNull(userId);
        if (explicitUserId != null) {
            return truncate(explicitUserId);
        }
        String explicitHeaderUserId = trimToNull(headerUserId);
        if (explicitHeaderUserId != null) {
            return truncate(explicitHeaderUserId);
        }
        String loginUserId = currentLoginId();
        if (loginUserId != null) {
            return truncate(loginUserId);
        }
        return DEFAULT_USER_ID;
    }

    static String resolveOperator(String headerUserId) {
        String loginUserId = currentLoginId();
        if (loginUserId != null) {
            return truncate(loginUserId);
        }
        String explicitHeaderUserId = trimToNull(headerUserId);
        if (explicitHeaderUserId != null) {
            return truncate(explicitHeaderUserId);
        }
        return DEFAULT_USER_ID;
    }

    private static String currentLoginId() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            return trimToNull(StpUtil.getLoginIdAsString());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String truncate(String value) {
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
