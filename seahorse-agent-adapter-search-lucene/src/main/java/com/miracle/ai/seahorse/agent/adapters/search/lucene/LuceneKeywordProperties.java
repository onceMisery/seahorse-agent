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

package com.miracle.ai.seahorse.agent.adapters.search.lucene;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lucene 嵌入式关键词检索配置。
 *
 * <p>该适配器面向单机轻量部署，索引目录必须由部署侧显式持久化；默认目录只适合本地开发。
 */
public record LuceneKeywordProperties(Path indexDirectory, List<String> searchFields) {

    public LuceneKeywordProperties {
        indexDirectory = indexDirectory == null
                ? Path.of(System.getProperty("java.io.tmpdir"), "seahorse-agent-lucene-keyword")
                : indexDirectory;
        searchFields = List.copyOf(Objects.requireNonNullElse(searchFields, List.of(LuceneKeywordFields.CONTENT + "^3")));
        if (searchFields.isEmpty()) {
            searchFields = List.of(LuceneKeywordFields.CONTENT + "^3");
        }
    }

    String[] queryFields() {
        return searchFields.stream()
                .map(this::stripBoost)
                .filter(field -> !field.isBlank())
                .distinct()
                .toArray(String[]::new);
    }

    Map<String, Float> fieldBoosts() {
        Map<String, Float> boosts = new LinkedHashMap<>();
        for (String searchField : searchFields) {
            String field = stripBoost(searchField);
            if (field.isBlank()) {
                continue;
            }
            boosts.put(field, boost(searchField));
        }
        return boosts;
    }

    private String stripBoost(String field) {
        String safeField = Objects.requireNonNullElse(field, "").trim();
        int boostIndex = safeField.indexOf('^');
        return boostIndex > 0 ? safeField.substring(0, boostIndex).trim() : safeField;
    }

    private float boost(String field) {
        int boostIndex = field == null ? -1 : field.indexOf('^');
        if (boostIndex < 0 || boostIndex == field.length() - 1) {
            return 1.0F;
        }
        try {
            float value = Float.parseFloat(field.substring(boostIndex + 1).trim());
            return value > 0 ? value : 1.0F;
        } catch (NumberFormatException ex) {
            return 1.0F;
        }
    }
}
