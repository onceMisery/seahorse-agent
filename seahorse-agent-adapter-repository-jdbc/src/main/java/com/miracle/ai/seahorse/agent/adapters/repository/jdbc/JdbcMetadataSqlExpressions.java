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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;

import java.util.Objects;

/**
 * JDBC/PostgreSQL metadata 字段 SQL 表达式工具。
 *
 * <p>这里统一生成查询和表达式索引的字段表达式，避免动态 metadata 的数值/时间字段继续按字符串字典序比较。
 */
final class JdbcMetadataSqlExpressions {

    private static final String NUMERIC_PATTERN = "^[+-]?[0-9]+([.][0-9]+)?$";

    private JdbcMetadataSqlExpressions() {
    }

    static String textValueExpression(String metadataColumn, String fieldKey) {
        return metadataColumn + "->>'" + safeFieldKey(fieldKey) + "'";
    }

    static String comparableValueExpression(String metadataColumn, String fieldKey, MetadataValueType valueType) {
        String textExpression = textValueExpression(metadataColumn, fieldKey);
        MetadataValueType safeType = Objects.requireNonNullElse(valueType, MetadataValueType.STRING);
        return switch (safeType) {
            case NUMBER -> """
                    CASE
                      WHEN NULLIF(btrim(%s), '') IS NULL THEN NULL
                      WHEN btrim(%s) ~ '%s' THEN CAST(%s AS NUMERIC)
                      ELSE NULL
                    END
                    """.formatted(textExpression, textExpression, NUMERIC_PATTERN, textExpression).trim();
            default -> textExpression;
        };
    }

    static String parameterPlaceholder(MetadataValueType valueType) {
        MetadataValueType safeType = Objects.requireNonNullElse(valueType, MetadataValueType.STRING);
        return switch (safeType) {
            case NUMBER -> "CAST(? AS NUMERIC)";
            default -> "?";
        };
    }

    static String safeFieldKey(String fieldKey) {
        String safeKey = Objects.requireNonNullElse(fieldKey, "").trim();
        if (!safeKey.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("invalid metadata field key: " + fieldKey);
        }
        return safeKey;
    }
}
