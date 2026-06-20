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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ReActExecutorPortTests {

    @Test
    void kernelAgentLoopIsDefaultReActExecutor() {
        KernelAgentLoop loop = new KernelAgentLoop(new AgentLoopDependencies(
                StreamingChatModelPort.noop(),
                ToolRegistryPort.empty(),
                null,
                KernelAgentLoopOptions.defaults(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        ReActExecutorPort executor = assertInstanceOf(ReActExecutorPort.class, loop);
        assertEquals("kernel", executor.engineId());
    }

    @Test
    void kernelAgentLoopHasSingleDependenciesConstructor() {
        var constructors = Arrays.stream(KernelAgentLoop.class.getDeclaredConstructors())
                .filter(constructor -> !constructor.isSynthetic())
                .toList();

        assertEquals(1, constructors.size());
        assertEquals(Modifier.PUBLIC, constructors.get(0).getModifiers() & Modifier.PUBLIC);
        assertEquals(1, constructors.get(0).getParameterCount());
        assertEquals(AgentLoopDependencies.class, constructors.get(0).getParameterTypes()[0]);
    }

    @Test
    void kernelAgentLoopStaysBelowRefactorLineBudget() throws Exception {
        Path source = Path.of("src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/KernelAgentLoop.java");

        assertEquals(true, Files.readAllLines(source).size() < 700);
    }
}
