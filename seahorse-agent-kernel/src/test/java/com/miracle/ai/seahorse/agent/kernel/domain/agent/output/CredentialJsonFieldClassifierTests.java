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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialJsonFieldClassifierTests {

    @Test
    void shouldClassifySharedSensitiveOutputFields() {
        assertTrue(CredentialJsonFieldClassifier.isSensitiveOutputField("apiKey"));
        assertTrue(CredentialJsonFieldClassifier.isSensitiveOutputField("Authorization"));
        assertTrue(CredentialJsonFieldClassifier.isSensitiveOutputField("Cookie"));
        assertTrue(CredentialJsonFieldClassifier.isSensitiveOutputField("sessionToken"));
        assertTrue(CredentialJsonFieldClassifier.isSensitiveOutputField("private_key"));
    }

    @Test
    void shouldPreserveReferenceAndCountMetadata() {
        assertFalse(CredentialJsonFieldClassifier.isSensitiveOutputField("secretRef"));
        assertFalse(CredentialJsonFieldClassifier.isSensitiveProviderOrAuditField("secretRef"));
        assertFalse(CredentialJsonFieldClassifier.isSensitiveOutputField("tokenCount"));
        assertFalse(CredentialJsonFieldClassifier.isSensitiveProviderOrAuditField("tokenCount"));
        assertFalse(CredentialJsonFieldClassifier.isSensitiveOutputField("cookieCount"));
        assertFalse(CredentialJsonFieldClassifier.isSensitiveProviderOrAuditField("cookieCount"));
    }

    @Test
    void shouldKeepProviderAuditSecretKeywordExtensionOutOfBasicOutputFields() {
        assertFalse(CredentialJsonFieldClassifier.isSensitiveOutputField("secretValue"));
        assertTrue(CredentialJsonFieldClassifier.isSensitiveProviderOrAuditField("secretValue"));
    }
}
