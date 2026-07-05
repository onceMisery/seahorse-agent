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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "seahorse.agentscope")
public class AgentScopeProperties {

    private final Executor executor = new Executor();
    private final Nacos nacos = new Nacos();
    private final A2a a2a = new A2a();
    private final ConfigCenter configCenter = new ConfigCenter();
    private final Studio studio = new Studio();

    public Executor getExecutor() {
        return executor;
    }

    public Nacos getNacos() {
        return nacos;
    }

    public A2a getA2a() {
        return a2a;
    }

    public ConfigCenter getConfigCenter() {
        return configCenter;
    }

    public Studio getStudio() {
        return studio;
    }

    public static class Executor {
        private String agentName = "seahorse-agent";
        private String systemPrompt = "";
        private Duration timeout = Duration.ofMinutes(2);

        public String getAgentName() {
            return agentName;
        }

        public void setAgentName(String agentName) {
            this.agentName = agentName;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class Nacos {
        private String serverAddr = "";
        private String namespace = "public";
        private String group = "DEFAULT_GROUP";
        private String username = "";
        private String password = "";
        private String accessKey = "";
        private String secretKey = "";
        private final M3 m3 = new M3();
        private Map<String, String> properties = new LinkedHashMap<>();

        public String getServerAddr() {
            return serverAddr;
        }

        public void setServerAddr(String serverAddr) {
            this.serverAddr = serverAddr;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public M3 getM3() {
            return m3;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties == null ? new LinkedHashMap<>() : new LinkedHashMap<>(properties);
        }
    }

    public static class M3 {
        private boolean enabled;
        private String mode = "M3";
        private String namespace = "";
        private String group = "";
        private String clusterName = "";
        private Map<String, String> metadata = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getClusterName() {
            return clusterName;
        }

        public void setClusterName(String clusterName) {
            this.clusterName = clusterName;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }

        public void setMetadata(Map<String, String> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        }
    }

    public static class A2a {
        private boolean enabled;
        private boolean registerEnabled;
        private String nacosServer = "";
        private String tenantId = "default";
        private String agentName = "seahorse-agent";
        private String version = "1.0.0";
        private String description = "Seahorse Agent";
        private String url = "";
        private String preferredTransport = "jsonrpc";
        private A2aAuthMode authMode = A2aAuthMode.SHARED_SECRET;
        private String authHeaderName = "X-Seahorse-A2A-Token";
        private String sharedSecret = "";
        private Duration allowedTimestampSkew = Duration.ofMinutes(5);
        private Duration nonceTtl = Duration.ofMinutes(10);
        private Duration registrationTtl = Duration.ZERO;
        private A2aDuplicateRegistrationPolicy duplicateRegistrationPolicy = A2aDuplicateRegistrationPolicy.REJECT;
        private boolean setAsLatest;
        private boolean registerEndpoint = true;
        private String transport = "jsonrpc";
        private String host = "";
        private int port;
        private String path = "/a2a";
        private boolean supportTls;
        private String protocol = "http";
        private String query = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isRegisterEnabled() {
            return registerEnabled;
        }

        public void setRegisterEnabled(boolean registerEnabled) {
            this.registerEnabled = registerEnabled;
        }

        public String getNacosServer() {
            return nacosServer;
        }

        public void setNacosServer(String nacosServer) {
            this.nacosServer = nacosServer;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getAgentName() {
            return agentName;
        }

        public void setAgentName(String agentName) {
            this.agentName = agentName;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getPreferredTransport() {
            return preferredTransport;
        }

        public void setPreferredTransport(String preferredTransport) {
            this.preferredTransport = preferredTransport;
        }

        public A2aAuthMode getAuthMode() {
            return authMode;
        }

        public void setAuthMode(A2aAuthMode authMode) {
            this.authMode = authMode == null ? A2aAuthMode.SHARED_SECRET : authMode;
        }

        public String getAuthHeaderName() {
            return authHeaderName;
        }

        public void setAuthHeaderName(String authHeaderName) {
            this.authHeaderName = authHeaderName;
        }

        public String getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(String sharedSecret) {
            this.sharedSecret = sharedSecret;
        }

        public Duration getAllowedTimestampSkew() {
            return allowedTimestampSkew;
        }

        public void setAllowedTimestampSkew(Duration allowedTimestampSkew) {
            this.allowedTimestampSkew = allowedTimestampSkew;
        }

        public Duration getNonceTtl() {
            return nonceTtl;
        }

        public void setNonceTtl(Duration nonceTtl) {
            this.nonceTtl = nonceTtl;
        }

        public Duration getRegistrationTtl() {
            return registrationTtl;
        }

        public void setRegistrationTtl(Duration registrationTtl) {
            this.registrationTtl = registrationTtl == null ? Duration.ZERO : registrationTtl;
        }

        public A2aDuplicateRegistrationPolicy getDuplicateRegistrationPolicy() {
            return duplicateRegistrationPolicy;
        }

        public void setDuplicateRegistrationPolicy(A2aDuplicateRegistrationPolicy duplicateRegistrationPolicy) {
            this.duplicateRegistrationPolicy = duplicateRegistrationPolicy == null
                    ? A2aDuplicateRegistrationPolicy.REJECT
                    : duplicateRegistrationPolicy;
        }

        public boolean isSetAsLatest() {
            return setAsLatest;
        }

        public void setSetAsLatest(boolean setAsLatest) {
            this.setAsLatest = setAsLatest;
        }

        public boolean isRegisterEndpoint() {
            return registerEndpoint;
        }

        public void setRegisterEndpoint(boolean registerEndpoint) {
            this.registerEndpoint = registerEndpoint;
        }

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isSupportTls() {
            return supportTls;
        }

        public void setSupportTls(boolean supportTls) {
            this.supportTls = supportTls;
        }

        public String getProtocol() {
            return protocol;
        }

        public void setProtocol(String protocol) {
            this.protocol = protocol;
        }

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }

    public static class ConfigCenter {
        private boolean enabled;
        private String promptKey = "";
        private String promptVersion = "";
        private String promptLabel = "";
        private String skillNamespace = "";
        private String skillVersion = "";
        private String skillLabel = "";
        private boolean strictStartup;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPromptKey() {
            return promptKey;
        }

        public void setPromptKey(String promptKey) {
            this.promptKey = promptKey;
        }

        public String getPromptVersion() {
            return promptVersion;
        }

        public void setPromptVersion(String promptVersion) {
            this.promptVersion = promptVersion;
        }

        public String getPromptLabel() {
            return promptLabel;
        }

        public void setPromptLabel(String promptLabel) {
            this.promptLabel = promptLabel;
        }

        public String getSkillNamespace() {
            return skillNamespace;
        }

        public void setSkillNamespace(String skillNamespace) {
            this.skillNamespace = skillNamespace;
        }

        public String getSkillVersion() {
            return skillVersion;
        }

        public void setSkillVersion(String skillVersion) {
            this.skillVersion = skillVersion;
        }

        public String getSkillLabel() {
            return skillLabel;
        }

        public void setSkillLabel(String skillLabel) {
            this.skillLabel = skillLabel;
        }

        public boolean isStrictStartup() {
            return strictStartup;
        }

        public void setStrictStartup(boolean strictStartup) {
            this.strictStartup = strictStartup;
        }
    }

    public static class Studio {
        private boolean enabled;
        private boolean autoInitialize = true;
        private String studioUrl = "";
        private String tracingUrl = "";
        private String project = "seahorse-agent";
        private String runName = "seahorse-agent";
        private int maxRetries = 3;
        private int reconnectAttempts = 3;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAutoInitialize() {
            return autoInitialize;
        }

        public void setAutoInitialize(boolean autoInitialize) {
            this.autoInitialize = autoInitialize;
        }

        public String getStudioUrl() {
            return studioUrl;
        }

        public void setStudioUrl(String studioUrl) {
            this.studioUrl = studioUrl;
        }

        public String getTracingUrl() {
            return tracingUrl;
        }

        public void setTracingUrl(String tracingUrl) {
            this.tracingUrl = tracingUrl;
        }

        public String getProject() {
            return project;
        }

        public void setProject(String project) {
            this.project = project;
        }

        public String getRunName() {
            return runName;
        }

        public void setRunName(String runName) {
            this.runName = runName;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getReconnectAttempts() {
            return reconnectAttempts;
        }

        public void setReconnectAttempts(int reconnectAttempts) {
            this.reconnectAttempts = reconnectAttempts;
        }
    }
}
