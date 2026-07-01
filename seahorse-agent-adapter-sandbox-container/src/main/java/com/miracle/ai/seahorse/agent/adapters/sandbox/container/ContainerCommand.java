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

package com.miracle.ai.seahorse.agent.adapters.sandbox.container;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

record ContainerCommand(List<String> commandLine,
                        Path workingDirectory,
                        Duration timeout,
                        int stdoutLimitBytes,
                        int stderrLimitBytes) {

    ContainerCommand {
        commandLine = List.copyOf(Objects.requireNonNull(commandLine, "commandLine must not be null"));
        if (commandLine.isEmpty()) {
            throw new IllegalArgumentException("commandLine must not be empty");
        }
        workingDirectory = Objects.requireNonNull(workingDirectory, "workingDirectory must not be null")
                .toAbsolutePath()
                .normalize();
        timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        stdoutLimitBytes = requirePositive(stdoutLimitBytes, "stdoutLimitBytes");
        stderrLimitBytes = requirePositive(stderrLimitBytes, "stderrLimitBytes");
    }

    private static int requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
