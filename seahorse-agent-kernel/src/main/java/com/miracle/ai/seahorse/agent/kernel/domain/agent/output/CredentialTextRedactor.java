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

import java.util.regex.Pattern;

public final class CredentialTextRedactor {

    public static final String REDACTED_VALUE = "[REDACTED]";

    private static final Pattern OPENAI_KEY_PATTERN = Pattern.compile("sk-[A-Za-z0-9][A-Za-z0-9_-]*");
    private static final Pattern CREDENTIAL_VALUE_PATTERN = Pattern.compile(
            "(?i)(authorization\\s*[:=]\\s*(?:bearer|basic)\\s+[a-z0-9._~+/=-]{8,}"
                    + "|bearer\\s+[a-z0-9._~+/=-]{8,}"
                    + "|(?:access[_-]?token|refresh[_-]?token|api[_-]?key|client[_-]?secret|password|session[_-]?id"
                    + "|session[_-]?token|secret[_-]?key|private[_-]?key|set[_-]?cookie|cookie)"
                    + "\\s*[:=]\\s*[^\\s&;]+)");

    private CredentialTextRedactor() {
    }

    public static String redact(String value) {
        if (value == null) {
            return null;
        }
        String redacted = OPENAI_KEY_PATTERN.matcher(value).replaceAll(REDACTED_VALUE);
        return CREDENTIAL_VALUE_PATTERN.matcher(redacted).replaceAll(REDACTED_VALUE);
    }

    public static boolean containsCredential(String value) {
        if (value == null) {
            return false;
        }
        return OPENAI_KEY_PATTERN.matcher(value).find()
                || CREDENTIAL_VALUE_PATTERN.matcher(value).find();
    }
}
