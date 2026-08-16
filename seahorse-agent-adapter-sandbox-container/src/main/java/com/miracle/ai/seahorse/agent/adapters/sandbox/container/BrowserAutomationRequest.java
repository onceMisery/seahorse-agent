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

import java.util.List;

/**
 * 浏览器自动化请求解析结果（从 {@link ContainerSandboxRuntimeAdapter} 提取为包级类型）。
 * 由 {@link ContainerBrowserAutomationSupport} 与 {@link ContainerWorkspaceManager} 之间传递。
 */
record BrowserAutomationRequest(String action,
                                String html,
                                String url,
                                List<String> allowedHosts,
                                List<BrowserCookie> cookies,
                                int viewportWidth,
                                int viewportHeight,
                                boolean screenshot,
                                boolean har,
                                boolean video,
                                boolean captureSessionState,
                                String sessionStateJson,
                                List<String> browserPrivateNetworkAllowedHosts) {

    BrowserAutomationRequest {
        browserPrivateNetworkAllowedHosts = browserPrivateNetworkAllowedHosts == null
                ? List.of()
                : List.copyOf(browserPrivateNetworkAllowedHosts);
    }
}
