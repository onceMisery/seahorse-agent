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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 配额策略 DO，映射 {@code sa_quota_policy} 表。
 */
@TableName("sa_quota_policy")
public class QuotaPolicyDO {

    @TableId("policy_id")
    private String policyId;

    @TableField("tenant_id")
    private String tenantId;

    @TableField("scope")
    private String scope;

    @TableField("subject_id")
    private String subjectId;

    @TableField("status")
    private String status;

    @TableField("token_limit")
    private Long tokenLimit;

    @TableField("call_limit")
    private Long callLimit;

    @TableField("cost_limit")
    private Double costLimit;

    @TableField("warn_ratio")
    private Double warnRatio;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getTokenLimit() { return tokenLimit; }
    public void setTokenLimit(Long tokenLimit) { this.tokenLimit = tokenLimit; }

    public Long getCallLimit() { return callLimit; }
    public void setCallLimit(Long callLimit) { this.callLimit = callLimit; }

    public Double getCostLimit() { return costLimit; }
    public void setCostLimit(Double costLimit) { this.costLimit = costLimit; }

    public Double getWarnRatio() { return warnRatio; }
    public void setWarnRatio(Double warnRatio) { this.warnRatio = warnRatio; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
