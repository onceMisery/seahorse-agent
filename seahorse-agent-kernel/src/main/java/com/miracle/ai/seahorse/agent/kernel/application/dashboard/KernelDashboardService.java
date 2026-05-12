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

package com.miracle.ai.seahorse.agent.kernel.application.dashboard;

import com.miracle.ai.seahorse.agent.ports.inbound.dashboard.DashboardInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardOverview;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardPerformance;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardTrends;

import java.util.Objects;

/**
 * Kernel 层 Dashboard 查询服务。
 */
public class KernelDashboardService implements DashboardInboundPort {

    private final DashboardRepositoryPort repositoryPort;

    public KernelDashboardService(DashboardRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

    @Override
    public DashboardOverview overview(String window) {
        return repositoryPort.overview(window);
    }

    @Override
    public DashboardPerformance performance(String window) {
        return repositoryPort.performance(window);
    }

    @Override
    public DashboardTrends trends(String metric, String window, String granularity) {
        return repositoryPort.trends(metric, window, granularity);
    }
}
