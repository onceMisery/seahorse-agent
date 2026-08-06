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
 * OCI 运行时可用性（从 {@link ContainerSandboxRuntimeAdapter} 提取为包级类型）。
 * 由主类在 {@code inspectConfiguredOciRuntime} 中构造并汇入健康检查。
 */
record OciRuntimeAvailability(boolean available,
                              List<String> failureMessages) {

    OciRuntimeAvailability {
        failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
    }

    static OciRuntimeAvailability confirmed() {
        return new OciRuntimeAvailability(true, List.of());
    }

    static OciRuntimeAvailability unavailable(String message) {
        return new OciRuntimeAvailability(false, List.of(ContainerSandboxTextSupport.nullToEmpty(message)));
    }
}
