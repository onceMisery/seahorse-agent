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
 * 文件转换请求解析结果（从 {@link ContainerSandboxRuntimeAdapter} 提取为包级类型）。
 * 由 {@link ContainerFileConversionSupport} 与 {@link ContainerWorkspaceManager} 之间传递。
 */
record FileConversionRequest(String sourceFormat,
                             String targetFormat,
                             String contentEncoding,
                             String content) {
}
