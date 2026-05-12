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

package com.miracle.ai.seahorse.agent.ports.inbound.dashboard;

import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardOverview;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardPerformance;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardTrends;

/**
 * Dashboard 管理面入站端口。
 */
public interface DashboardInboundPort {

    DashboardOverview overview(String window);

    DashboardPerformance performance(String window);

    DashboardTrends trends(String metric, String window, String granularity);
}
