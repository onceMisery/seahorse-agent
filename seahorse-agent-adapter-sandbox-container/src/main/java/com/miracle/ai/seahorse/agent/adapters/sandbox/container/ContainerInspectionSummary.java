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
 * 容器巡检汇总（从 {@link ContainerSandboxRuntimeAdapter} 提取为包级类型）。
 * 由主类在 {@code inspectManagedContainers} 与 {@code sweepOrphanedResources} 间传递。
 */
record ContainerInspectionSummary(int inspectedCount,
                                  int activeCount,
                                  int orphanCount,
                                  int failedInspectionCount,
                                  List<String> activeNames,
                                  List<String> orphanNames,
                                  List<String> failureMessages) {

    ContainerInspectionSummary {
        activeNames = activeNames == null ? List.of() : List.copyOf(activeNames);
        orphanNames = orphanNames == null ? List.of() : List.copyOf(orphanNames);
        failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
    }

    static ContainerInspectionSummary failed(String message) {
        return new ContainerInspectionSummary(
                0,
                0,
                0,
                1,
                List.of(),
                List.of(),
                List.of(ContainerSandboxTextSupport.nullToEmpty(message)));
    }
}
