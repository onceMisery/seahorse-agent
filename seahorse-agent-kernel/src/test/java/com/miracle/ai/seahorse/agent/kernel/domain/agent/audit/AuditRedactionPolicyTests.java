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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditRedactionPolicyTests {

    @Test
    void shouldRedactNestedSecretsAndPreserveSecretRefs() {
        AuditRedactionPolicy policy = new AuditRedactionPolicy();

        String redacted = policy.redact("""
                {
                  "secretRef":"secret://tenant/a",
                  "secretValue":"plain-secret",
                  "nested":{"Authorization":"Bearer token-value","safe":"visible"},
                  "items":[{"apiKey":"abc123"},{"password":"pw"}]
                }
                """);

        assertTrue(redacted.contains("secret://tenant/a"));
        assertTrue(redacted.contains("visible"));
        assertTrue(redacted.contains(AuditRedactionPolicy.REDACTED_VALUE));
        assertFalse(redacted.contains("plain-secret"));
        assertFalse(redacted.contains("token-value"));
        assertFalse(redacted.contains("abc123"));
        assertFalse(redacted.contains("pw"));
    }

    @Test
    void shouldFailClosedForInvalidJsonPayload() {
        AuditRedactionPolicy policy = new AuditRedactionPolicy();

        String redacted = policy.redact("not-json-token-secret");

        assertTrue(redacted.contains(AuditRedactionPolicy.REDACTED_VALUE));
        assertFalse(redacted.contains("not-json-token-secret"));
    }
}
