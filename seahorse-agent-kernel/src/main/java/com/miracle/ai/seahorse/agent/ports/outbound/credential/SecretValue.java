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

package com.miracle.ai.seahorse.agent.ports.outbound.credential;

import java.util.Objects;

/**
 * Secret value wrapper that keeps accidental string rendering redacted.
 */
public final class SecretValue {

    public static final String REDACTED_TEXT = "[REDACTED]";

    private static final SecretValue EMPTY = new SecretValue("");

    private final String value;

    private SecretValue(String value) {
        this.value = Objects.requireNonNullElse(value, "");
    }

    public static SecretValue of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("secret value must not be blank");
        }
        return new SecretValue(value);
    }

    public static SecretValue empty() {
        return EMPTY;
    }

    public String reveal() {
        return value;
    }

    public boolean hasText() {
        return !value.isBlank();
    }

    @Override
    public String toString() {
        return REDACTED_TEXT;
    }
}
