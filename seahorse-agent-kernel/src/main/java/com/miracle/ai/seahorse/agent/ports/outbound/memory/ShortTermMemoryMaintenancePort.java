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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.time.Instant;
import java.util.List;

/**
 * 短期记忆后台维护端口。
 *
 * <p>该端口只负责治理任务需要的扫描和清理能力，避免把后台批处理职责塞进常规读写端口。</p>
 */
public interface ShortTermMemoryMaintenancePort {

    List<MemoryRecord> scanExpiredOrDecayed(Instant now, double decayThreshold, int limit);

    int markDeleted(List<String> memoryIds);

    static ShortTermMemoryMaintenancePort noop() {
        return new ShortTermMemoryMaintenancePort() {
            @Override
            public List<MemoryRecord> scanExpiredOrDecayed(Instant now, double decayThreshold, int limit) {
                return List.of();
            }

            @Override
            public int markDeleted(List<String> memoryIds) {
                return 0;
            }
        };
    }
}
