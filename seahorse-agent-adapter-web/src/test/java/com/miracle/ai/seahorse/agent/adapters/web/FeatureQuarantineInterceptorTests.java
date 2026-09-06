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

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeatureQuarantineInterceptorTests {

    @Test
    void quarantinedPathShouldReturnStable403WhenFeatureDisabled() throws Exception {
        MockMvc mvc = mvc(AdvancedFeatureGate.demoDefaults());

        mvc.perform(get("/api/billing/plans"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"));
        mvc.perform(get("/marketplace/listings"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"));
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADVANCED_FEATURE_DISABLED"));
    }

    @Test
    void quarantinedPathShouldPassThroughWhenFeatureEnabled() throws Exception {
        MockMvc mvc = mvc(AdvancedFeatureGate.allEnabledForTests());

        mvc.perform(get("/api/billing/plans")).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users")).andExpect(status().isOk());
    }

    @Test
    void nonQuarantinedPathShouldPassThroughWhenDisabled() throws Exception {
        MockMvc mvc = mvc(AdvancedFeatureGate.demoDefaults());

        mvc.perform(get("/rag/v3/chat")).andExpect(status().isOk());
    }

    @Test
    void prefixMatchingShouldBeSegmentSafe() throws Exception {
        MockMvc mvc = mvc(AdvancedFeatureGate.demoDefaults());

        mvc.perform(get("/api/billing-xyz")).andExpect(status().isOk());
    }

    private MockMvc mvc(AdvancedFeatureGate gate) {
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .addInterceptors(new FeatureQuarantineInterceptor(gate))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();
    }

    @RestController
    static class ProbeController {

        @GetMapping({"/api/billing/plans", "/api/billing-xyz", "/marketplace/listings", "/api/admin/users",
                "/rag/v3/chat"})
        public Map<String, String> ok() {
            return Map.of("code", "0");
        }
    }
}
