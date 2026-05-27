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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import java.time.Instant;
import java.util.Optional;

/**
 * 持久化任务队列端口。
 *
 * <p>用于 Research Web Agent 的步骤编排，支持入队、认领、确认、重试和失败。
 * 初始实现基于 JDBC（FOR UPDATE SKIP LOCKED），后续可替换为 db-scheduler 或 JobRunr。
 */
public interface DurableTaskQueuePort {

    void enqueue(DurableTask task);

    Optional<DurableTask> claimNext(String workerId);

    void ack(String taskId);

    void retry(String taskId, Instant retryAt, String reason);

    void fail(String taskId, String reason);

    void cancel(String runId);
}
