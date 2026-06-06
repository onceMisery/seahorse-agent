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

import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SpringCurrentUserAdapterTests {

    @AfterEach
    void clearRequestAttributes() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void currentUserShouldUseHeaderWhenQueryUserIdIsAlsoPresent() {
        UserRepositoryPort repository = mock(UserRepositoryPort.class);
        when(repository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(repository.findById(2L)).thenReturn(Optional.of(user(2L)));
        SpringCurrentUserAdapter adapter = new SpringCurrentUserAdapter(repository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "1");
        request.addParameter("userId", "2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var currentUser = adapter.currentUser();

        assertThat(currentUser).isPresent();
        assertThat(currentUser.orElseThrow().userId()).isEqualTo(1L);
        verify(repository).findById(1L);
        verify(repository, never()).findById(2L);
    }

    @Test
    void currentUserShouldIgnoreQueryUserIdWithoutHeader() {
        UserRepositoryPort repository = mock(UserRepositoryPort.class);
        when(repository.findById(1L)).thenReturn(Optional.of(user(1L)));
        SpringCurrentUserAdapter adapter = new SpringCurrentUserAdapter(repository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("userId", "1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var currentUser = adapter.currentUser();

        assertThat(currentUser).isEmpty();
        verifyNoInteractions(repository);
    }

    private static UserRecord user(Long id) {
        return new UserRecord(id, id.toString(), "", "user", null, Instant.EPOCH, Instant.EPOCH);
    }
}
