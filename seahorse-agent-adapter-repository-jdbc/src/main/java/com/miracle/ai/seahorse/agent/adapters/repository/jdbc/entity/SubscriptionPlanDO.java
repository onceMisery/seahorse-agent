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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 订阅计划 DO，映射 {@code sa_subscription_plan} 表。
 */
@TableName("sa_subscription_plan")
public class SubscriptionPlanDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("code")
    private String code;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("monthly_price")
    private BigDecimal monthlyPrice;

    @TableField("yearly_price")
    private BigDecimal yearlyPrice;

    @TableField("token_limit")
    private Long tokenLimit;

    @TableField("storage_limit_bytes")
    private Long storageLimitBytes;

    @TableField("concurrency_limit")
    private Integer concurrencyLimit;

    @TableField("active")
    private Boolean active;

    // --- Getters and Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public void setMonthlyPrice(BigDecimal monthlyPrice) { this.monthlyPrice = monthlyPrice; }

    public BigDecimal getYearlyPrice() { return yearlyPrice; }
    public void setYearlyPrice(BigDecimal yearlyPrice) { this.yearlyPrice = yearlyPrice; }

    public Long getTokenLimit() { return tokenLimit; }
    public void setTokenLimit(Long tokenLimit) { this.tokenLimit = tokenLimit; }

    public Long getStorageLimitBytes() { return storageLimitBytes; }
    public void setStorageLimitBytes(Long storageLimitBytes) { this.storageLimitBytes = storageLimitBytes; }

    public Integer getConcurrencyLimit() { return concurrencyLimit; }
    public void setConcurrencyLimit(Integer concurrencyLimit) { this.concurrencyLimit = concurrencyLimit; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}
