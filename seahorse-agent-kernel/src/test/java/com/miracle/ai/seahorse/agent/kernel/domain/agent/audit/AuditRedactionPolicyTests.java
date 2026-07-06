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
                  "nested":{
                    "Authorization":"Bearer token-value",
                    "Cookie":"sid=plain-cookie-header",
                    "cookieCount":1,
                    "setCookie":"sid=plain-cookie",
                    "token":"plain-token",
                    "private_key":"plain-private-key",
                    "sessionId":"plain-session-id",
                    "safe":"visible"
                  },
                  "items":[{"apiKey":"abc123"},{"password":"pw"}]
                  ,"usage":{"tokenCount":2}
                }
                """);

        assertTrue(redacted.contains("secret://tenant/a"));
        assertTrue(redacted.contains("visible"));
        assertTrue(redacted.contains("\"cookieCount\":1"));
        assertTrue(redacted.contains("\"tokenCount\":2"));
        assertTrue(redacted.contains(AuditRedactionPolicy.REDACTED_VALUE));
        assertFalse(redacted.contains("plain-secret"));
        assertFalse(redacted.contains("token-value"));
        assertFalse(redacted.contains("plain-token"));
        assertFalse(redacted.contains("plain-cookie-header"));
        assertFalse(redacted.contains("plain-cookie"));
        assertFalse(redacted.contains("plain-private-key"));
        assertFalse(redacted.contains("plain-session-id"));
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

    @Test
    void shouldRedactCredentialShapedStringValuesUnderSafeKeys() {
        AuditRedactionPolicy policy = new AuditRedactionPolicy();

        String redacted = policy.redact("""
                {
                  "message":"upstream failed with Bearer abcdefghijklmnop",
                  "url":"https://example.test/callback?access_token=token-secret-value",
                  "notes":["safe note","cookie: secret-api-key-value","session_token: session-token-value","password: hunter2"],
                  "safe":"ordinary business text"
                }
                """);

        assertTrue(redacted.contains("ordinary business text"));
        assertTrue(redacted.contains(AuditRedactionPolicy.REDACTED_VALUE));
        assertFalse(redacted.contains("abcdefghijklmnop"));
        assertFalse(redacted.contains("token-secret-value"));
        assertFalse(redacted.contains("secret-api-key-value"));
        assertFalse(redacted.contains("session-token-value"));
        assertFalse(redacted.contains("hunter2"));
    }
}
