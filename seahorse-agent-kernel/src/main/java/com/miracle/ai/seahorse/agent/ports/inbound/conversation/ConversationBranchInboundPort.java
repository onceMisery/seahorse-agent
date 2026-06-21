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

package com.miracle.ai.seahorse.agent.ports.inbound.conversation;

import com.miracle.ai.seahorse.agent.ports.outbound.conversation.MessageTreeNode;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchCursor;

import java.util.List;
import java.util.Optional;

/**
 * Inbound port for conversation message branching.
 */
public interface ConversationBranchInboundPort {

    ForkResult fork(ForkCommand command);

    List<MessageTreeNode> switchBranch(String conversationId, String userId, Long targetNodeId);

    List<MessageTreeNode> loadActiveTree(String conversationId, String userId);

    ConversationBranchCursor saveCursor(String conversationId, String userId, Long leafMessageId);

    Optional<ConversationBranchCursor> loadCursor(String conversationId, String userId);
}
