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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownNormalizerTests {

    private final MarkdownNormalizer normalizer = new MarkdownNormalizer();

    @Test
    void normalizesCompressedHeadingsAndMermaidBlocks() {
        String normalized = normalizer.normalizeFinalMarkdown("""
                Intro---##三、架构图```mermaid
                flowchart TD Client[客户端] --> Server[服务端] Server --> Done[完成]```
                """);

        assertTrue(normalized.contains("Intro\n\n---\n\n## 三、架构图\n\n```mermaid\nflowchart TD"));
        assertTrue(normalized.contains("Client[客户端] --> Server[服务端]\nServer --> Done[完成]\n```"));
        assertFalse(normalized.contains("---##"));
        assertFalse(normalized.contains("TD Client["));
    }

    @Test
    void leavesArtifactBodiesUntouched() {
        String normalized = normalizer.normalizeFinalMarkdown("""
                ##一、项目概览Redis is fast.
                <artifact language="html">
                <style>
                :root { --redis-red: #DC382D; }
                </style>
                </artifact>
                """);

        assertTrue(normalized.contains("## 一、项目概览\n\nRedis is fast."));
        assertTrue(normalized.contains("--redis-red: #DC382D;"));
        assertFalse(normalized.contains("# DC382D"));
    }
}
