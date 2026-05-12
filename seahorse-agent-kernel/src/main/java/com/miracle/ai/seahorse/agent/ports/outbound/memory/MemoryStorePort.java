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

import java.util.List;
import java.util.Optional;

/**
 * 记忆存储端口。
 * <p>
 * 该端口是 working、short-term、long-term、semantic 等分层端口的公共基线，
 * 具体层级可以继承或组合该能力。
 */
public interface MemoryStorePort {

    /**
     * 按 ID 读取记忆。
     *
     * @param id 记忆 ID
     * @return 记忆记录
     */
    Optional<MemoryRecord> findById(String id);

    /**
     * 查询会话相关记忆。
     *
     * @param conversationId 会话 ID
     * @param limit          最大返回数量
     * @return 记忆记录列表
     */
    List<MemoryRecord> listByConversation(String conversationId, int limit);

    /**
     * 查询用户记忆。
     *
     * @param userId 用户 ID
     * @param limit  最大返回数量
     * @return 记忆记录列表
     */
    List<MemoryRecord> listByUser(String userId, int limit);

    /**
     * 保存记忆。
     *
     * @param record 记忆记录
     */
    void save(MemoryRecord record);

    /**
     * 按 ID 逻辑删除记忆。
     *
     * @param id 记忆 ID
     * @return 是否删除成功
     */
    boolean deleteById(String id);
}
