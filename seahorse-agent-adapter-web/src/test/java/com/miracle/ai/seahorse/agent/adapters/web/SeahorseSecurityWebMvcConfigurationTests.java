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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(SeahorseSecurityWebMvcConfigurationTests.TestWebConfiguration.class)
class SeahorseSecurityWebMvcConfigurationTests {

    private MockMvc mvc;

    @BeforeEach
    void setUp(@Autowired WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void shouldAllowAiInfraPrototypeRoutesWithoutLogin() throws Exception {
        mvc.perform(get("/prototype/ai-infra"))
                .andExpect(status().isOk())
                .andExpect(content().string("prototype"));
    }

    @Test
    void shouldNotTreatProtectedApiRoutesAsPublic() {
        assertThat(SeahorseSecurityWebMvcConfiguration.isPublicPath("/prototype/ai-infra")).isTrue();
        assertThat(SeahorseSecurityWebMvcConfiguration.isPublicPath("/features")).isTrue();
        assertThat(SeahorseSecurityWebMvcConfiguration.isPublicPath("/api/features")).isTrue();
        assertThat(SeahorseSecurityWebMvcConfiguration.isPublicPath("/admin/ai-infra")).isFalse();
        assertThat(SeahorseSecurityWebMvcConfiguration.isPublicPath("/user/me")).isFalse();
        assertThat(SeahorseSecurityWebMvcConfiguration.isPublicPath("/agents")).isFalse();
        assertThat(SeahorseSecurityWebMvcConfiguration.isPublicPath("/agent-runs/run-1")).isFalse();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(SeahorseSecurityWebMvcConfiguration.class)
    static class TestWebConfiguration {

        @Controller
        static class TestController {

            @GetMapping({ "/prototype/ai-infra", "/admin/ai-infra" })
            @ResponseBody
            String prototype() {
                return "prototype";
            }

            @GetMapping("/user/me")
            @ResponseBody
            String user() {
                return "user";
            }
        }
    }
}
