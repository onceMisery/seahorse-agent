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

package com.miracle.ai.seahorse.agent.kernel.application.agent.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebFetchSafetyPolicyTests {

    private final WebFetchSafetyPolicy policy = new WebFetchSafetyPolicy();

    @Test
    void shouldAllowPublicHttpAndHttpsUrls() {
        assertTrue(policy.decide("https://example.com/article").allowed());
        assertTrue(policy.decide("http://news.example.org/report").allowed());
    }

    @Test
    void shouldRejectNonHttpSchemesAndLocalNetworkTargets() {
        assertRejected("file:///etc/passwd", WebFetchSafetyReason.SCHEME_NOT_ALLOWED);
        assertRejected("https://localhost:8080", WebFetchSafetyReason.LOCALHOST_BLOCKED);
        assertRejected("https://127.0.0.1/private", WebFetchSafetyReason.PRIVATE_NETWORK_BLOCKED);
        assertRejected("https://10.0.0.2/private", WebFetchSafetyReason.PRIVATE_NETWORK_BLOCKED);
        assertRejected("https://172.16.1.1/private", WebFetchSafetyReason.PRIVATE_NETWORK_BLOCKED);
        assertRejected("https://192.168.1.1/private", WebFetchSafetyReason.PRIVATE_NETWORK_BLOCKED);
        assertRejected("https://169.254.169.254/latest/meta-data",
                WebFetchSafetyReason.METADATA_ENDPOINT_BLOCKED);
        assertRejected("https://[::1]/private", WebFetchSafetyReason.PRIVATE_NETWORK_BLOCKED);
    }

    private void assertRejected(String url, WebFetchSafetyReason reason) {
        WebFetchSafetyDecision decision = policy.decide(url);
        assertFalse(decision.allowed());
        assertEquals(reason, decision.reason());
    }
}
