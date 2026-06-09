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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAgentLoopMarkdownOutputTests {

    @Test
    void finalMarkdownNormalizesCompressedMermaidFences() {
        String compressedMarkdown = """
                # Redis intro---## Project overviewIntro text### Evidence|Path |Purpose ||------|------|
                - **Client layer**: Redis clients- **Network layer**: RESP protocol
                ## Architecture
                ```mermaidflowchart TD
                A[Client] --> B[Redis]
                ```---## Flow
                ```mermaidgraph LR
                X[Read] --> Y[Write]
                ```---## Summary
                """;
        KernelAgentLoop loop = new KernelAgentLoop(
                new FinalAnswerModel(compressedMarkdown),
                ToolRegistryPort.empty(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("render markdown")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(1)
                .build());

        String answer = result.finalAnswer();
        assertTrue(answer.contains("```mermaid\nflowchart TD"));
        assertTrue(answer.contains("```mermaid\ngraph LR"));
        assertTrue(answer.contains("```mermaid\nflowchart TD\n"));
        assertTrue(answer.contains("# Redis intro"));
        assertTrue(answer.contains("\n- **"));
        assertFalse(answer.contains("```mermaidflowchart"));
        assertFalse(answer.contains("```mermaidgraph"));
        assertFalse(answer.contains("---##"));
        assertFalse(answer.contains("```---"));
    }

    @Test
    void finalMarkdownSeparatesCompressedHeadingsAndMermaidClosers() {
        String compressedMarkdown = """
                All tools succeeded.---# Redis project intro---##一、项目概览**Redis** is fast.###关键用途|用途 |说明 |
                |------|------|
                | Cache | Fast |
                ---##二、架构设计Redis keeps data in memory.###核心架构组件```mermaid
                flowchart TD
                A[Client] --> B[Redis]```
                ###架构要点1. **RESP**: protocol.2. **Command**: execution.
                ---##四、流程图###命令执行流程```mermaid
                sequenceDiagram
                C->>R: SET key value```
                ###持久化流程```mermaid
                flowchart LR
                W[Write] --> AOF[AOF]```
                ---##六、重点特性###6.1丰富的数据结构Redis supports strings.###6.2高性能-数据主要保存在内存中-单线程模型避免锁竞争
                """;
        KernelAgentLoop loop = new KernelAgentLoop(
                new FinalAnswerModel(compressedMarkdown),
                ToolRegistryPort.empty(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("render markdown")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(1)
                .build());

        String answer = result.finalAnswer();
        assertTrue(answer.contains("All tools succeeded.\n\n---\n\n# Redis project intro"));
        assertTrue(answer.contains("## 一、项目概览\n\n**Redis** is fast."));
        assertTrue(answer.contains("### 关键用途\n\n|用途 |说明 |"));
        assertTrue(answer.contains("## 二、架构设计\n\nRedis keeps data in memory."));
        assertTrue(answer.contains("### 核心架构组件\n\n```mermaid\nflowchart TD"));
        assertTrue(answer.contains("A[Client] --> B[Redis]\n```"));
        assertTrue(answer.contains("### 架构要点\n\n1. **RESP**: protocol.\n2. **Command**: execution."));
        assertTrue(answer.contains("### 命令执行流程\n\n```mermaid\nsequenceDiagram"));
        assertTrue(answer.contains("C->>R: SET key value\n```"));
        assertTrue(answer.contains("### 持久化流程\n\n```mermaid\nflowchart LR"));
        assertTrue(answer.contains("## 六、重点特性"));
        assertTrue(answer.contains("### 6.1丰富的数据结构\n\nRedis supports strings."));
        assertTrue(answer.contains("### 6.2高性能\n\n- 数据主要保存在内存中\n- 单线程模型避免锁竞争"));
        assertFalse(answer.contains("---#"));
        assertFalse(answer.contains("---##"));
        assertFalse(answer.contains("###核心架构组件```"));
        assertFalse(answer.contains("```mermaidflowchart"));
        assertFalse(answer.contains("Redis]```"));
        assertFalse(answer.contains(".2. **"));
        assertFalse(answer.contains("高性能-数据"));
    }

    @Test
    void finalMarkdownDoesNotSplitHyphenatedWordsOrUrlsAsLists() {
        String compressedMarkdown = """
                ##一、项目概览The real-time project uses BSD3-Clause and feature-rich APIs.
                ##六、生成图片引用![Redis](https://platform-outputs.agnes-ai.space/images/text-to-image/2026/06/example-image.png)
                ###6.2高性能-数据主要保存在内存中-单线程模型避免锁竞争
                """;
        KernelAgentLoop loop = new KernelAgentLoop(
                new FinalAnswerModel(compressedMarkdown),
                ToolRegistryPort.empty(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("render markdown")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(1)
                .build());

        String answer = result.finalAnswer();
        assertTrue(answer.contains("real-time"));
        assertTrue(answer.contains("BSD3-Clause"));
        assertTrue(answer.contains("feature-rich"));
        assertTrue(answer.contains("https://platform-outputs.agnes-ai.space/images/text-to-image/2026/06/example-image.png"));
        assertTrue(answer.contains("### 6.2高性能\n\n- 数据主要保存在内存中\n- 单线程模型避免锁竞争"));
        assertFalse(answer.contains("real\n- time"));
        assertFalse(answer.contains("BSD3\n- Clause"));
        assertFalse(answer.contains("platform\n- outputs"));
        assertFalse(answer.contains("text\n- to\n- image"));
    }

    @Test
    void finalMarkdownSeparatesCommonGeneratedSections() {
        String compressedMarkdown = """
                ##四、核心逻辑###1.事件驱动模型Redis采用单线程事件循环。
                ###2.数据结构实现Redis使用SDS和跳表。
                ##七、生成图片引用###项目介绍视觉图![Redis项目介绍图](https://platform-outputs.agnes-ai.space/images/text-to-image/2026/06/example-image.png)*图注：Redis架构图*
                ##八、生成稿件摘要###长文 Markdown草稿已生成中文长文，涵盖：-项目概述-核心数据结构-主要应用场景
                ###演示文稿结构已生成10页演示文稿，包含：- Redis开源项目概览-核心价值-核心数据结构详解
                ##九、总结Redis提供坚实技术支撑。---*本文档基于 Redis 官方仓库生成。*
                """;
        KernelAgentLoop loop = new KernelAgentLoop(
                new FinalAnswerModel(compressedMarkdown),
                ToolRegistryPort.empty(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("render markdown")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(1)
                .build());

        String answer = result.finalAnswer();
        assertTrue(answer.contains("### 1.事件驱动模型\n\nRedis采用单线程事件循环。"), answer);
        assertTrue(answer.contains("### 2.数据结构实现\n\nRedis使用SDS和跳表。"));
        assertTrue(answer.contains("### 项目介绍视觉图\n\n![Redis项目介绍图](https://platform-outputs.agnes-ai.space/images/text-to-image/2026/06/example-image.png)"));
        assertTrue(answer.contains("### 长文 Markdown草稿\n\n已生成中文长文，涵盖：\n\n- 项目概述\n- 核心数据结构\n- 主要应用场景"));
        assertTrue(answer.contains("### 演示文稿结构\n\n已生成10页演示文稿，包含：\n\n- Redis开源项目概览\n- 核心价值\n- 核心数据结构详解"));
        assertTrue(answer.contains("## 九、总结\n\nRedis提供坚实技术支撑。\n\n---\n\n*本文档基于 Redis 官方仓库生成。*"));
        assertFalse(answer.contains("事件驱动模型Redis"));
        assertFalse(answer.contains("项目介绍视觉图!["));
        assertFalse(answer.contains("概述-核心"));
        assertFalse(answer.contains("---*"));
    }

    @Test
    void finalMarkdownDoesNotRewriteMermaidCodeBlockContent() {
        String compressedMarkdown = """
                ##三、架构图```mermaid
                flowchart TD
                A[Redis] --> B[Memory]
                style A fill:#e1f5fe,stroke:#01579b,stroke-width:2px
                ```
                ##四、流程图```mermaid
                sequenceDiagram
                User->>Agent:生成介绍
                ```
                """;
        KernelAgentLoop loop = new KernelAgentLoop(
                new FinalAnswerModel(compressedMarkdown),
                ToolRegistryPort.empty(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("render markdown")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(1)
                .build());

        String answer = result.finalAnswer();
        assertTrue(answer.contains("style A fill:#e1f5fe,stroke:#01579b,stroke-width:2px"));
        assertTrue(answer.contains("## 三、架构图\n\n```mermaid\nflowchart TD"));
        assertTrue(answer.contains("## 四、流程图\n\n```mermaid\nsequenceDiagram"));
        assertFalse(answer.contains("fill:\n\n# e1f5fe"));
        assertFalse(answer.contains("stroke:\n\n# 01579b"));
    }

    @Test
    void finalMarkdownSplitsCompressedMermaidStatementsWithoutTouchingStyles() {
        String compressedMarkdown = """
                ##三、架构图```mermaid
                flowchart TD Client[客户端] --> Network[网络引擎] Network --> Parser[命令解析器] Parser --> Engine[数据结构引擎] style Client fill:#e1f5fe,stroke:#01579b,stroke-width:2px
                ```
                ##四、流程图```mermaid
                sequenceDiagram participant Client as 客户端 participant Server as Redis服务器 Client->>Server:发送命令 Server->>Client:返回结果
                ```
                """;
        KernelAgentLoop loop = new KernelAgentLoop(
                new FinalAnswerModel(compressedMarkdown),
                ToolRegistryPort.empty(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("render markdown")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(1)
                .build());

        String answer = result.finalAnswer();
        assertTrue(answer.contains("```mermaid\nflowchart TD\nClient[客户端] --> Network[网络引擎]\nNetwork --> Parser[命令解析器]\nParser --> Engine[数据结构引擎]\nstyle Client fill:#e1f5fe,stroke:#01579b,stroke-width:2px\n```"), answer);
        assertTrue(answer.contains("```mermaid\nsequenceDiagram\nparticipant Client as 客户端\nparticipant Server as Redis服务器\nClient->>Server:发送命令\nServer->>Client:返回结果\n```"), answer);
        assertFalse(answer.contains("flowchart TD Client[客户端]"));
        assertFalse(answer.contains("sequenceDiagram participant Client"));
        assertFalse(answer.contains("fill:\n\n# e1f5fe"));
    }

    @Test
    void finalMarkdownSplitsCompressedMermaidSubgraphsDirectionsAndStandaloneNodes() {
        String compressedMarkdown = """
                ##三、架构图```mermaid
                flowchart TD subgraph ServerSide [Redis Server内部架构] direction TB N[网络层 Network] P[RESP协议解析] Cmd[命令执行引擎] end Mem --> Pers RedisServer[Redis Server] RedisServer --> Rep
                ```
                """;
        KernelAgentLoop loop = new KernelAgentLoop(
                new FinalAnswerModel(compressedMarkdown),
                ToolRegistryPort.empty(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("render markdown")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(1)
                .build());

        String answer = result.finalAnswer();
        assertTrue(answer.contains("```mermaid\nflowchart TD\nsubgraph ServerSide [Redis Server内部架构]\ndirection TB\nN[网络层 Network]\nP[RESP协议解析]\nCmd[命令执行引擎]\nend\nMem --> Pers\nRedisServer[Redis Server]\nRedisServer --> Rep\n```"), answer);
        assertFalse(answer.contains("direction TB N["));
        assertFalse(answer.contains("Pers RedisServer["));
    }

    @Test
    void finalMarkdownSeparatesAdjacentMermaidFences() {
        String compressedMarkdown = """
                ##三、架构图```mermaid
                sequenceDiagram Client->>Redis:发送命令 Redis-->>Client:返回响应``````mermaid
                flowchart TD Client[客户端] --> Redis[Redis服务]
                ```
                """;
        KernelAgentLoop loop = new KernelAgentLoop(
                new FinalAnswerModel(compressedMarkdown),
                ToolRegistryPort.empty(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("render markdown")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(1)
                .build());

        String answer = result.finalAnswer();
        assertTrue(answer.contains("```mermaid\nsequenceDiagram\nClient->>Redis:发送命令\nRedis-->>Client:返回响应\n```\n\n```mermaid\nflowchart TD\nClient[客户端] --> Redis[Redis服务]\n```"), answer);
        assertFalse(answer.contains("返回响应``````mermaid"));
        assertFalse(answer.contains("sequenceDiagram Client"));
    }

    @Test
    void finalMarkdownDoesNotRewriteHtmlArtifacts() {
        String compressedMarkdown = """
                ##一、项目概览Redis is fast.
                <artifact language="html" title="project-intro-web-preview.html">
                <!DOCTYPE html>
                <html>
                <head>
                <style>
                :root {
                  --redis-red: #DC382D;
                  --bg-dark: #0f1115;
                }
                h1 { color: #ffffff; }
                </style>
                </head>
                <body><h1>Redis</h1></body>
                </html>
                </artifact>
                """;
        KernelAgentLoop loop = new KernelAgentLoop(
                new FinalAnswerModel(compressedMarkdown),
                ToolRegistryPort.empty(),
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("render markdown")
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .maxSteps(1)
                .build());

        String answer = result.finalAnswer();
        assertTrue(answer.contains("## 一、项目概览\n\nRedis is fast."), answer);
        assertTrue(answer.contains("--redis-red: #DC382D;"), answer);
        assertTrue(answer.contains("--bg-dark: #0f1115;"), answer);
        assertTrue(answer.contains("h1 { color: #ffffff; }"), answer);
        assertFalse(answer.contains("# DC382D"));
        assertFalse(answer.contains("# 0f1115"));
        assertFalse(answer.contains("# ffffff"));
    }

    private static final class FinalAnswerModel implements StreamingChatModelPort {
        private final String answer;

        private FinalAnswerModel(String answer) {
            this.answer = answer;
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            callback.onContent(answer);
            toolCallCollector.onToolCalls(List.of());
            callback.onComplete();
            return () -> {
            };
        }
    }
}
