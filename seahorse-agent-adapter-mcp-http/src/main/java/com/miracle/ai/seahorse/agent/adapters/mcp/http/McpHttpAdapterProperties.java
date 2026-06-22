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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * HTTP MCP adapter 配置。
 *
 * <p>配置前缀采用 Seahorse 原生命名空间，Spring bridge 会继续把旧 {@code rag.mcp.servers}
 * 映射到新配置，保证迁移期部署文件可兼容。
 */
@ConfigurationProperties(prefix = "seahorse-agent.adapters.mcp")
public class McpHttpAdapterProperties {

    private boolean enabled = true;

    private Duration callTimeout = Duration.ofSeconds(30);

    private List<Server> servers = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getCallTimeout() {
        return callTimeout;
    }

    public void setCallTimeout(Duration callTimeout) {
        this.callTimeout = Objects.requireNonNullElse(callTimeout, Duration.ofSeconds(30));
    }

    public List<Server> getServers() {
        return servers;
    }

    public void setServers(List<Server> servers) {
        this.servers = new ArrayList<>(Objects.requireNonNullElse(servers, List.of()));
    }

    /**
     * 单个远程 MCP Server 配置。
     */
    public static class Server {

        private static final String DEFAULT_TENANT_ID = "default";

        private String name = "";

        private String url = "";

        private boolean enabled = true;

        @Getter
        @Setter
        private Transport transport = Transport.STREAMABLE_HTTP;

        @Getter
        @Setter
        private String command = "";

        @Getter
        private List<String> args = new ArrayList<>();

        @Getter
        private Map<String, String> env = new LinkedHashMap<>();

        @Getter
        @Setter
        private String workingDir = "";

        private CredentialAuthType authType = CredentialAuthType.NONE;

        private String tenantId = DEFAULT_TENANT_ID;

        private String authorizationServerMetadataUrl = "";

        private String protectedResourceMetadataUrl = "";

        private String clientId = "";

        private String clientSecretRef = "";

        private List<String> scopes = new ArrayList<>();

        private String audience = "";

        private String resource = "";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = Objects.requireNonNullElse(name, "");
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = Objects.requireNonNullElse(url, "");
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public CredentialAuthType getAuthType() {
            return authType;
        }

        public void setAuthType(CredentialAuthType authType) {
            this.authType = Objects.requireNonNullElse(authType, CredentialAuthType.NONE);
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = Objects.requireNonNullElse(tenantId, DEFAULT_TENANT_ID);
        }

        public String getAuthorizationServerMetadataUrl() {
            return authorizationServerMetadataUrl;
        }

        public void setAuthorizationServerMetadataUrl(String authorizationServerMetadataUrl) {
            this.authorizationServerMetadataUrl = Objects.requireNonNullElse(authorizationServerMetadataUrl, "");
        }

        public String getProtectedResourceMetadataUrl() {
            return protectedResourceMetadataUrl;
        }

        public void setProtectedResourceMetadataUrl(String protectedResourceMetadataUrl) {
            this.protectedResourceMetadataUrl = Objects.requireNonNullElse(protectedResourceMetadataUrl, "");
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = Objects.requireNonNullElse(clientId, "");
        }

        public String getClientSecretRef() {
            return clientSecretRef;
        }

        public void setClientSecretRef(String clientSecretRef) {
            this.clientSecretRef = Objects.requireNonNullElse(clientSecretRef, "");
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = new ArrayList<>(Objects.requireNonNullElse(scopes, List.of()));
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = Objects.requireNonNullElse(audience, "");
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = Objects.requireNonNullElse(resource, "");
        }

        public void setArgs(List<String> args) {
            this.args = new ArrayList<>(Objects.requireNonNullElse(args, List.of()));
        }

        public void setEnv(Map<String, String> env) {
            this.env = new LinkedHashMap<>(Objects.requireNonNullElse(env, Map.of()));
        }
    }

    public enum Transport {
        STREAMABLE_HTTP,
        STDIO
    }
}
