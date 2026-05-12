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

import java.time.Instant;
import java.util.Objects;

/**
 * 消息反馈提交快照。
 *
 * @param messageId  消息 ID
 * @param userId     用户 ID
 * @param vote       反馈值，1 为赞，-1 为踩
 * @param reason     反馈原因
 * @param comment    反馈备注
 * @param submitTime 提交时间
 */
public record MessageFeedbackSubmission(
        String messageId,
        String userId,
        int vote,
        String reason,
        String comment,
        Instant submitTime
) {

    public MessageFeedbackSubmission {
        messageId = requireText(messageId, "messageId");
        userId = requireText(userId, "userId");
        if (vote != 1 && vote != -1) {
            throw new IllegalArgumentException("vote must be 1 or -1");
        }
        reason = Objects.requireNonNullElse(reason, "");
        comment = Objects.requireNonNullElse(comment, "");
        submitTime = Objects.requireNonNullElseGet(submitTime, Instant::now);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
