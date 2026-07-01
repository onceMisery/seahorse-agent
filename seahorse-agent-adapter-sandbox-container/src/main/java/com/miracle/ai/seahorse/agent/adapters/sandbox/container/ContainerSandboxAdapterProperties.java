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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "seahorse-agent.adapters.sandbox.container")
public class ContainerSandboxAdapterProperties {

    private String engine = "docker";

    private String pythonImage = "python:3.11-alpine";

    private String workspaceRoot = "";

    private Duration executionTimeout = Duration.ofSeconds(30);

    private int stdoutLimitBytes = 16 * 1024;

    private int stderrLimitBytes = 16 * 1024;

    private String memory = "256m";

    private String cpus = "1.0";

    private long pidsLimit = 128L;

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = requireTextOrDefault(engine, "docker");
    }

    public String getPythonImage() {
        return pythonImage;
    }

    public void setPythonImage(String pythonImage) {
        this.pythonImage = requireTextOrDefault(pythonImage, "python:3.11-alpine");
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = Objects.requireNonNullElse(workspaceRoot, "");
    }

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(Duration executionTimeout) {
        if (executionTimeout == null || executionTimeout.isZero() || executionTimeout.isNegative()) {
            this.executionTimeout = Duration.ofSeconds(30);
            return;
        }
        this.executionTimeout = executionTimeout;
    }

    public int getStdoutLimitBytes() {
        return stdoutLimitBytes;
    }

    public void setStdoutLimitBytes(int stdoutLimitBytes) {
        this.stdoutLimitBytes = positiveOrDefault(stdoutLimitBytes, 16 * 1024);
    }

    public int getStderrLimitBytes() {
        return stderrLimitBytes;
    }

    public void setStderrLimitBytes(int stderrLimitBytes) {
        this.stderrLimitBytes = positiveOrDefault(stderrLimitBytes, 16 * 1024);
    }

    public String getMemory() {
        return memory;
    }

    public void setMemory(String memory) {
        this.memory = requireTextOrDefault(memory, "256m");
    }

    public String getCpus() {
        return cpus;
    }

    public void setCpus(String cpus) {
        this.cpus = requireTextOrDefault(cpus, "1.0");
    }

    public long getPidsLimit() {
        return pidsLimit;
    }

    public void setPidsLimit(long pidsLimit) {
        this.pidsLimit = pidsLimit > 0 ? pidsLimit : 128L;
    }

    private static int positiveOrDefault(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static String requireTextOrDefault(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
