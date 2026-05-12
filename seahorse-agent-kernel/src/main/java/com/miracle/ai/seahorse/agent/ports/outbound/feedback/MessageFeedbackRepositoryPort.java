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

package com.miracle.ai.seahorse.agent.ports.outbound.feedback;

import java.util.List;
import java.util.Map;

/**
 * 消息反馈仓储端口。
 */
public interface MessageFeedbackRepositoryPort {

    /**
     * 新增或更新当前用户对助手消息的反馈。
     *
     * @param feedback 反馈记录
     */
    void upsert(MessageFeedbackSubmission feedback);

    /**
     * 查询用户对消息集合的投票值。
     *
     * @param userId     用户 ID
     * @param messageIds 消息 ID 集合
     * @return messageId 到 vote 的映射
     */
    Map<String, Integer> findUserVotes(String userId, List<String> messageIds);
}
