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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 文档刷新调度配置仓储端口。
 */
public interface DocumentRefreshSchedulePort {

    Optional<DocumentRefreshSchedule> findByDocumentId(String docId);

    List<DocumentRefreshSchedule> findDueSchedules(Instant now, int limit);

    void upsert(DocumentRefreshSchedule schedule);

    void updateState(DocumentRefreshScheduleUpdate update);

    default void disableByDocumentId(String docId, String reason) {
        findByDocumentId(docId).ifPresent(schedule -> updateState(new DocumentRefreshScheduleUpdate(
                schedule.id(), "failed", reason, Instant.now(), null,
                schedule.lastContentHash(), schedule.lastEtag(), schedule.lastModified())));
    }

    static DocumentRefreshSchedulePort noop() {
        return new DocumentRefreshSchedulePort() {
            @Override
            public Optional<DocumentRefreshSchedule> findByDocumentId(String docId) {
                return Optional.empty();
            }

            @Override
            public List<DocumentRefreshSchedule> findDueSchedules(Instant now, int limit) {
                return List.of();
            }

            @Override
            public void upsert(DocumentRefreshSchedule schedule) {
            }

            @Override
            public void updateState(DocumentRefreshScheduleUpdate update) {
            }
        };
    }
}
