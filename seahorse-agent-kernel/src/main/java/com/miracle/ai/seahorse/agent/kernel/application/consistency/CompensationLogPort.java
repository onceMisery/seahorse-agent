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

package com.miracle.ai.seahorse.agent.kernel.application.consistency;

import java.util.List;

/**
 * 补偿日志持久化端口。
 */
public interface CompensationLogPort {

    /**
     * 保存补偿日志。
     */
    void save(CompensationLog log);

    /**
     * 查找所有待重试的补偿日志。
     */
    List<CompensationLog> findPendingRetries(int limit);

    /**
     * 更新补偿日志状态。
     */
    void updateStatus(Long id, CompensationLog.CompensationStatus status, String lastError);

    /**
     * 增加重试次数。
     */
    void incrementRetryCount(Long id);
}
