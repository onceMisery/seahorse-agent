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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.output;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialTextRedactorTests {

    @Test
    void shouldRedactCredentialShapedTextFragments() {
        String redacted = CredentialTextRedactor.redact(
                "Authorization: Bearer abcdefghijklmnop "
                        + "Cookie: sid=plain-cookie "
                        + "secret_key: plain-secret "
                        + "sk-live-secret");

        assertEquals("[REDACTED] [REDACTED] [REDACTED] [REDACTED]", redacted);
        assertFalse(redacted.contains("abcdefghijklmnop"));
        assertFalse(redacted.contains("plain-cookie"));
        assertFalse(redacted.contains("plain-secret"));
        assertFalse(redacted.contains("sk-live-secret"));
    }

    @Test
    void shouldDetectCredentialShapedTextWithoutMatchingSafeCookieMetadata() {
        assertTrue(CredentialTextRedactor.containsCredential("cookie: sid=plain-cookie"));
        assertFalse(CredentialTextRedactor.containsCredential("cookieCount=1 sessionStateCookieCount=1"));
    }

    @Test
    void shouldPreserveNullValues() {
        assertNull(CredentialTextRedactor.redact(null));
        assertFalse(CredentialTextRedactor.containsCredential(null));
    }
}
