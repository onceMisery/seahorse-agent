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

import cn.dev33.satoken.exception.NotLoginException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseWebExceptionHandlerTests {

    @Test
    void shouldSanitizeNotLoginMessage() {
        SeahorseWebExceptionHandler handler = new SeahorseWebExceptionHandler();

        Map<String, Object> response = handler.notLogin(new NotLoginException(
                "login",
                NotLoginException.INVALID_TOKEN,
                "token invalid: abc-raw-token"));

        assertThat(response.get("code")).isEqualTo("1");
        assertThat(response.get("message")).isEqualTo("登录已过期，请重新登录");
        assertThat(response.toString()).doesNotContain("abc-raw-token");
    }
}
