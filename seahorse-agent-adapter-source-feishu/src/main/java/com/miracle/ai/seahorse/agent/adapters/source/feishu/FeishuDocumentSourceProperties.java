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

package com.miracle.ai.seahorse.agent.adapters.source.feishu;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feishu/Lark 文档来源配置。
 */
@ConfigurationProperties(prefix = "seahorse-agent.adapters.source.feishu")
public class FeishuDocumentSourceProperties {

    private String baseUrl = "https://open.feishu.cn";
    private String tenantAccessTokenPath = "/open-apis/auth/v3/tenant_access_token/internal";
    private String downloadPathTemplate = "/open-apis/drive/v1/files/{fileToken}/download";
    private String tenantAccessToken = "";
    private String appId = "";
    private String appSecret = "";
    private int maxAttempts = 2;
    private long retryBackoffMillis = 200L;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTenantAccessTokenPath() {
        return tenantAccessTokenPath;
    }

    public void setTenantAccessTokenPath(String tenantAccessTokenPath) {
        this.tenantAccessTokenPath = tenantAccessTokenPath;
    }

    public String getDownloadPathTemplate() {
        return downloadPathTemplate;
    }

    public void setDownloadPathTemplate(String downloadPathTemplate) {
        this.downloadPathTemplate = downloadPathTemplate;
    }

    public String getTenantAccessToken() {
        return tenantAccessToken;
    }

    public void setTenantAccessToken(String tenantAccessToken) {
        this.tenantAccessToken = tenantAccessToken;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public long getRetryBackoffMillis() {
        return retryBackoffMillis;
    }

    public void setRetryBackoffMillis(long retryBackoffMillis) {
        this.retryBackoffMillis = retryBackoffMillis;
    }
}
