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

import java.time.Duration;
import java.util.Objects;

record ContainerCommandResult(int exitCode,
                              boolean timedOut,
                              String stdout,
                              String stderr,
                              Duration duration) {

    ContainerCommandResult {
        stdout = Objects.requireNonNullElse(stdout, "");
        stderr = Objects.requireNonNullElse(stderr, "");
        duration = Objects.requireNonNullElse(duration, Duration.ZERO);
    }

    static ContainerCommandResult succeeded(String stdout, Duration duration) {
        return new ContainerCommandResult(0, false, stdout, "", duration);
    }

    static ContainerCommandResult failed(int exitCode, String stdout, String stderr, Duration duration) {
        return new ContainerCommandResult(exitCode, false, stdout, stderr, duration);
    }

    static ContainerCommandResult timedOut(String stdout, String stderr, Duration duration) {
        return new ContainerCommandResult(-1, true, stdout, stderr, duration);
    }
}
