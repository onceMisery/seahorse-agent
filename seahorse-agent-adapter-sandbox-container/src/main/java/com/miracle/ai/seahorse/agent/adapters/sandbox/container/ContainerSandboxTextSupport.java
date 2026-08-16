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

/**
 * 容器沙箱公共文本/编码工具（从 {@link ContainerSandboxRuntimeAdapter} 提取）。
 * 按 §7 收敛原则外提：跨 {@link ContainerWorkspaceManager}、{@link ContainerFileConversionSupport}、
 * {@link ContainerBrowserAutomationSupport}、{@link ContainerNetworkBoundarySupport}、
 * {@link ContainerArtifactCollector} 共享的纯静态字符串辅助。
 */
final class ContainerSandboxTextSupport {

    static final String BASE64_ENCODING = "base64";

    private ContainerSandboxTextSupport() {
    }

    static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}
