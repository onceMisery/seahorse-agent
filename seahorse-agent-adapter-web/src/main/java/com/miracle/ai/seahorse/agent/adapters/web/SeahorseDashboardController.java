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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.ports.inbound.dashboard.DashboardInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Seahorse 原生 Dashboard Web adapter。
 */
@RestController
@RequestMapping("/admin/dashboard")
public class SeahorseDashboardController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final ObjectProvider<DashboardInboundPort> dashboardPortProvider;

    public SeahorseDashboardController(ObjectProvider<DashboardInboundPort> dashboardPortProvider) {
        this.dashboardPortProvider = dashboardPortProvider;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(required = false) String window) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, dashboardPortProvider.getIfAvailable().overview(window));
    }

    @GetMapping("/performance")
    public Map<String, Object> performance(@RequestParam(required = false) String window) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, dashboardPortProvider.getIfAvailable().performance(window));
    }

    @GetMapping("/trends")
    public Map<String, Object> trends(@RequestParam String metric,
                                      @RequestParam(required = false) String window,
                                      @RequestParam(required = false) String granularity) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, dashboardPortProvider.getIfAvailable().trends(metric, window, granularity));
    }
}
