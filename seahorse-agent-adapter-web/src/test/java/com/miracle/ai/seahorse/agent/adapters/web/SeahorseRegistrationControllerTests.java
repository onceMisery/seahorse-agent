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

import com.miracle.ai.seahorse.agent.ports.inbound.auth.RegistrationInboundPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseRegistrationControllerTests {

    @Test
    void shouldSendVerificationCodeWhenRegistrationServiceExists() throws Exception {
        RegistrationInboundPort port = mock(RegistrationInboundPort.class);
        MockMvc mvc = mvc(provider(RegistrationInboundPort.class, port));

        mvc.perform(post("/auth/send-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"e2e@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        verify(port).sendVerificationCode("e2e@example.com");
    }

    @Test
    void shouldReturnControlledErrorWhenRegistrationServiceIsMissing() throws Exception {
        MockMvc mvc = mvc(emptyProvider(RegistrationInboundPort.class));

        mvc.perform(get("/auth/email-available").param("email", "e2e@example.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value(ApiResponses.SERVICE_NOT_AVAILABLE_MESSAGE));
    }

    private static MockMvc mvc(ObjectProvider<RegistrationInboundPort> provider) {
        return MockMvcBuilders.standaloneSetup(new SeahorseRegistrationController(provider))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(type.getName(), instance);
        return beanFactory.getBeanProvider(type);
    }

    private static <T> ObjectProvider<T> emptyProvider(Class<T> type) {
        return new StaticListableBeanFactory().getBeanProvider(type);
    }
}
