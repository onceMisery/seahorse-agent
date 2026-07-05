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
import com.miracle.ai.seahorse.agent.kernel.model.AiModelConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.config.AiModelConfigRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiModelConfigControllerTests {

    @Test
    void shouldExposeAiModelConfigGateResult() throws Exception {
        AiModelConfigRepositoryPort repository = mock(AiModelConfigRepositoryPort.class);
        when(repository.findByKey("tenant-a", "openai.apiKey")).thenReturn(Optional.of(config()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AiModelConfigController(repository)).build();

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            mvc.perform(get("/admin/ai-config/openai.apiKey/gate-result")
                            .param("tenantId", "tenant-a"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data.subjectType").value("MODEL_CONFIG"))
                    .andExpect(jsonPath("$.data.subjectId").value("tenant-a:openai.apiKey"))
                    .andExpect(jsonPath("$.data.status").value("PASS"))
                    .andExpect(jsonPath("$.data.passed").value(true))
                    .andExpect(jsonPath("$.data.sourceType").value("AiModelConfig"))
                    .andExpect(jsonPath("$.data.sourceId").value("cfg-1"))
                    .andExpect(jsonPath("$.data.items[4].code").value("MODEL_CONFIG_SENSITIVE_VALUE_ENCRYPTED"));
        }

        verify(repository).findByKey("tenant-a", "openai.apiKey");
    }

    private static AiModelConfig config() {
        AiModelConfig config = new AiModelConfig();
        config.setId("cfg-1");
        config.setTenantId("tenant-a");
        config.setConfigKey("openai.apiKey");
        config.setConfigValue("sk-test");
        config.setConfigType(AiModelConfig.ConfigType.STRING);
        config.setEncrypted(true);
        config.setCreatedAt(LocalDateTime.parse("2026-07-05T03:30:00"));
        config.setUpdatedAt(LocalDateTime.parse("2026-07-05T04:00:00"));
        return config;
    }
}
