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
        when(repository.findById("attacker")).thenReturn(Optional.of(user("attacker")));
        when(repository.findById("owner")).thenReturn(Optional.of(user("owner")));
        SpringCurrentUserAdapter adapter = new SpringCurrentUserAdapter(repository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "attacker");
        request.addParameter("userId", "owner");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var currentUser = adapter.currentUser();

        assertThat(currentUser).isPresent();
        assertThat(currentUser.orElseThrow().userId()).isEqualTo("attacker");
        verify(repository).findById("attacker");
        verify(repository, never()).findById("owner");
    }

    @Test
    void currentUserShouldIgnoreQueryUserIdWithoutHeader() {
        UserRepositoryPort repository = mock(UserRepositoryPort.class);
        when(repository.findById("owner")).thenReturn(Optional.of(user("owner")));
        SpringCurrentUserAdapter adapter = new SpringCurrentUserAdapter(repository);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("userId", "owner");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var currentUser = adapter.currentUser();

        assertThat(currentUser).isEmpty();
        verifyNoInteractions(repository);
    }

    private static UserRecord user(String id) {
        return new UserRecord(id, id, "", "user", null, Instant.EPOCH, Instant.EPOCH);
    }
}
