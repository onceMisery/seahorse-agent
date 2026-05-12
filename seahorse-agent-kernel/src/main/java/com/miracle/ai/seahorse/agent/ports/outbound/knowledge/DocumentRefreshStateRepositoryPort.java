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

package com.miracle.ai.seahorse.agent.ports.outbound.knowledge;

/**
 * 文档刷新执行状态仓储端口。
 */
public interface DocumentRefreshStateRepositoryPort {

    String start(DocumentRefreshExecutionStart execution);

    void finish(DocumentRefreshExecutionFinish execution);

    static DocumentRefreshStateRepositoryPort noop() {
        return new DocumentRefreshStateRepositoryPort() {
            @Override
            public String start(DocumentRefreshExecutionStart execution) {
                return "";
            }

            @Override
            public void finish(DocumentRefreshExecutionFinish execution) {
            }
        };
    }
}
