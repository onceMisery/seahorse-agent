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

package com.miracle.ai.seahorse.agent.ports.outbound.conversation;

import lombok.Data;

import java.time.Instant;

/**
 * Conversation message record.
 */
@Data
public class ConversationMessageRecord {

    private String id;
    private String conversationId;
    private String userId;
    private String role;
    private String content;
    private String agentRunId;
    private String thinkingContent;
    private Integer thinkingDuration;
    private Integer vote;
    private Instant createTime;
    private Long parentId;
    private Integer active;
    private Long branchRootId;
    private Integer siblingSeq;
}
