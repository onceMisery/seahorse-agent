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

    private String browserImage = "seahorse-sandbox-browser:playwright-1.48.0";

    private String browserProxyServer = "";

    private String browserProxyServers = "";

    private String browserProxyUsername = "";

    private String browserProxyPassword = "";

    private String browserPrivateNetworkAllowedHosts = "";

    private String workspaceRoot = "";

    private String workspaceMountSourceRoot = "";

    private Duration executionTimeout = Duration.ofSeconds(30);

    private int stdoutLimitBytes = 16 * 1024;

    private int stderrLimitBytes = 16 * 1024;

    private String memory = "256m";

    private String browserMemory = "768m";

    private String cpus = "1.0";

    private long pidsLimit = 128L;

    private boolean dropAllCapabilities = true;

    private boolean noNewPrivileges = true;

    private boolean readOnlyRootFilesystem = true;

    private String runAsUser = "65532:65532";

    private long maxSessionFileBytes = 64L * 1024L * 1024L;

    private boolean externalVirusScannerEnabled;

    private String externalVirusScannerHost = "clamav";

    private int externalVirusScannerPort = 3310;

    private Duration externalVirusScannerTimeout = Duration.ofSeconds(10);

    private Duration orphanWorkspaceMinAge = Duration.ofMinutes(5);

    private int maxActiveSessions = 0;

    private long minWorkspaceFreeBytes = 0L;

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

    public String getBrowserImage() {
        return browserImage;
    }

    public void setBrowserImage(String browserImage) {
        this.browserImage = requireTextOrDefault(
                browserImage,
                "seahorse-sandbox-browser:playwright-1.48.0");
    }

    public String getBrowserProxyServer() {
        return browserProxyServer;
    }

    public void setBrowserProxyServer(String browserProxyServer) {
        this.browserProxyServer = Objects.requireNonNullElse(browserProxyServer, "").trim();
    }

    public String getBrowserProxyServers() {
        return browserProxyServers;
    }

    public void setBrowserProxyServers(String browserProxyServers) {
        this.browserProxyServers = Objects.requireNonNullElse(browserProxyServers, "").trim();
    }

    public String getBrowserProxyUsername() {
        return browserProxyUsername;
    }

    public void setBrowserProxyUsername(String browserProxyUsername) {
        this.browserProxyUsername = Objects.requireNonNullElse(browserProxyUsername, "").trim();
    }

    public String getBrowserProxyPassword() {
        return browserProxyPassword;
    }

    public void setBrowserProxyPassword(String browserProxyPassword) {
        this.browserProxyPassword = Objects.requireNonNullElse(browserProxyPassword, "");
    }

    public String getBrowserPrivateNetworkAllowedHosts() {
        return browserPrivateNetworkAllowedHosts;
    }

    public void setBrowserPrivateNetworkAllowedHosts(String browserPrivateNetworkAllowedHosts) {
        this.browserPrivateNetworkAllowedHosts = Objects.requireNonNullElse(browserPrivateNetworkAllowedHosts, "").trim();
    }

    public String getWorkspaceRoot() {
        return workspaceRoot;
    }

    public void setWorkspaceRoot(String workspaceRoot) {
        this.workspaceRoot = Objects.requireNonNullElse(workspaceRoot, "");
    }

    public String getWorkspaceMountSourceRoot() {
        return workspaceMountSourceRoot;
    }

    public void setWorkspaceMountSourceRoot(String workspaceMountSourceRoot) {
        this.workspaceMountSourceRoot = Objects.requireNonNullElse(workspaceMountSourceRoot, "").trim();
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

    public String getBrowserMemory() {
        return browserMemory;
    }

    public void setBrowserMemory(String browserMemory) {
        this.browserMemory = requireTextOrDefault(browserMemory, "768m");
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

    public boolean isDropAllCapabilities() {
        return dropAllCapabilities;
    }

    public void setDropAllCapabilities(boolean dropAllCapabilities) {
        this.dropAllCapabilities = dropAllCapabilities;
    }

    public boolean isNoNewPrivileges() {
        return noNewPrivileges;
    }

    public void setNoNewPrivileges(boolean noNewPrivileges) {
        this.noNewPrivileges = noNewPrivileges;
    }

    public boolean isReadOnlyRootFilesystem() {
        return readOnlyRootFilesystem;
    }

    public void setReadOnlyRootFilesystem(boolean readOnlyRootFilesystem) {
        this.readOnlyRootFilesystem = readOnlyRootFilesystem;
    }

    public String getRunAsUser() {
        return runAsUser;
    }

    public void setRunAsUser(String runAsUser) {
        this.runAsUser = requireTextOrDefault(runAsUser, "65532:65532");
    }

    public long getMaxSessionFileBytes() {
        return maxSessionFileBytes;
    }

    public void setMaxSessionFileBytes(long maxSessionFileBytes) {
        this.maxSessionFileBytes = maxSessionFileBytes > 0 ? maxSessionFileBytes : 64L * 1024L * 1024L;
    }

    public boolean isExternalVirusScannerEnabled() {
        return externalVirusScannerEnabled;
    }

    public void setExternalVirusScannerEnabled(boolean externalVirusScannerEnabled) {
        this.externalVirusScannerEnabled = externalVirusScannerEnabled;
    }

    public String getExternalVirusScannerHost() {
        return externalVirusScannerHost;
    }

    public void setExternalVirusScannerHost(String externalVirusScannerHost) {
        this.externalVirusScannerHost = requireTextOrDefault(externalVirusScannerHost, "clamav");
    }

    public int getExternalVirusScannerPort() {
        return externalVirusScannerPort;
    }

    public void setExternalVirusScannerPort(int externalVirusScannerPort) {
        this.externalVirusScannerPort = externalVirusScannerPort > 0 && externalVirusScannerPort <= 65535
                ? externalVirusScannerPort
                : 3310;
    }

    public Duration getExternalVirusScannerTimeout() {
        return externalVirusScannerTimeout;
    }

    public void setExternalVirusScannerTimeout(Duration externalVirusScannerTimeout) {
        if (externalVirusScannerTimeout == null || externalVirusScannerTimeout.isZero()
                || externalVirusScannerTimeout.isNegative()) {
            this.externalVirusScannerTimeout = Duration.ofSeconds(10);
            return;
        }
        this.externalVirusScannerTimeout = externalVirusScannerTimeout;
    }

    public Duration getOrphanWorkspaceMinAge() {
        return orphanWorkspaceMinAge;
    }

    public void setOrphanWorkspaceMinAge(Duration orphanWorkspaceMinAge) {
        if (orphanWorkspaceMinAge == null || orphanWorkspaceMinAge.isNegative()) {
            this.orphanWorkspaceMinAge = Duration.ofMinutes(5);
            return;
        }
        this.orphanWorkspaceMinAge = orphanWorkspaceMinAge;
    }

    public int getMaxActiveSessions() {
        return maxActiveSessions;
    }

    public void setMaxActiveSessions(int maxActiveSessions) {
        this.maxActiveSessions = Math.max(maxActiveSessions, 0);
    }

    public long getMinWorkspaceFreeBytes() {
        return minWorkspaceFreeBytes;
    }

    public void setMinWorkspaceFreeBytes(long minWorkspaceFreeBytes) {
        this.minWorkspaceFreeBytes = Math.max(minWorkspaceFreeBytes, 0L);
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
