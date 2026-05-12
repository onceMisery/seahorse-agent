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

package com.miracle.ai.seahorse.agent.ports.outbound.dashboard;

/**
 * Dashboard 性能统计。使用 JavaBean 形态避免超长构造参数。
 */
public class DashboardPerformance {

    private String window;
    private Long avgLatencyMs;
    private Long p95LatencyMs;
    private Double successRate;
    private Double errorRate;
    private Double noDocRate;
    private Double slowRate;

    public String getWindow() {
        return window;
    }

    public void setWindow(String window) {
        this.window = window;
    }

    public Long getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public void setAvgLatencyMs(Long avgLatencyMs) {
        this.avgLatencyMs = avgLatencyMs;
    }

    public Long getP95LatencyMs() {
        return p95LatencyMs;
    }

    public void setP95LatencyMs(Long p95LatencyMs) {
        this.p95LatencyMs = p95LatencyMs;
    }

    public Double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }

    public Double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(Double errorRate) {
        this.errorRate = errorRate;
    }

    public Double getNoDocRate() {
        return noDocRate;
    }

    public void setNoDocRate(Double noDocRate) {
        this.noDocRate = noDocRate;
    }

    public Double getSlowRate() {
        return slowRate;
    }

    public void setSlowRate(Double slowRate) {
        this.slowRate = slowRate;
    }
}
