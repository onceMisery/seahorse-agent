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

package com.miracle.ai.seahorse.agent.adapters.local;

import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Classpath prompt 模板适配器。
 *
 * <p>模板缺失时返回空字符串，由上层 RAG Prompt 适配器使用内置默认提示词兜底。
 */
public class ClasspathPromptTemplateAdapter implements PromptTemplatePort {

    @Override
    public String load(String path) {
        String safePath = normalizePath(path);
        if (safePath.isBlank()) {
            return "";
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = openResource(classLoader, safePath)) {
            if (inputStream == null) {
                return "";
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private InputStream openResource(ClassLoader classLoader, String path) {
        if (classLoader == null) {
            return ClasspathPromptTemplateAdapter.class.getClassLoader().getResourceAsStream(path);
        }
        return classLoader.getResourceAsStream(path);
    }

    private String normalizePath(String path) {
        String safePath = Objects.requireNonNullElse(path, "").trim();
        if (safePath.startsWith("/")) {
            return safePath.substring(1);
        }
        return safePath;
    }
}
