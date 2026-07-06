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

import java.util.Set;

public final class CredentialJsonFieldClassifier {

    private static final String SECRET_REF_KEY = "secretref";
    private static final Set<String> EXACT_SENSITIVE_FIELDS = Set.of(
            "cookie",
            "token");
    private static final Set<String> BASIC_SENSITIVE_KEYWORDS = Set.of(
            "accesstoken",
            "refreshtoken",
            "sessiontoken",
            "apikey",
            "clientsecret",
            "password",
            "sessionid",
            "authorization",
            "setcookie",
            "secretkey",
            "privatekey");
    private static final Set<String> EXTENDED_SENSITIVE_KEYWORDS = Set.of(
            "secret");

    private CredentialJsonFieldClassifier() {
    }

    public static boolean isSensitiveOutputField(String fieldName) {
        String normalized = normalize(fieldName);
        if (normalized.isEmpty() || SECRET_REF_KEY.equals(normalized)) {
            return false;
        }
        return EXACT_SENSITIVE_FIELDS.contains(normalized)
                || containsKeyword(normalized, BASIC_SENSITIVE_KEYWORDS);
    }

    public static boolean isSensitiveProviderOrAuditField(String fieldName) {
        String normalized = normalize(fieldName);
        if (normalized.isEmpty() || SECRET_REF_KEY.equals(normalized)) {
            return false;
        }
        return EXACT_SENSITIVE_FIELDS.contains(normalized)
                || containsKeyword(normalized, BASIC_SENSITIVE_KEYWORDS)
                || containsKeyword(normalized, EXTENDED_SENSITIVE_KEYWORDS);
    }

    private static String normalize(String fieldName) {
        if (fieldName == null) {
            return "";
        }
        return fieldName.trim()
                .replace("-", "")
                .replace("_", "")
                .toLowerCase();
    }

    private static boolean containsKeyword(String normalized, Set<String> keywords) {
        return keywords.stream().anyMatch(normalized::contains);
    }
}
