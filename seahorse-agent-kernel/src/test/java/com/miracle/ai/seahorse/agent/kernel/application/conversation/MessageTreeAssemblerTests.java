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

package com.miracle.ai.seahorse.agent.kernel.application.conversation;

import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.MessageTreeNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class MessageTreeAssemblerTests {

    private final MessageTreeAssembler assembler = new MessageTreeAssembler();

    @Test
    void shouldAssembleActivePathWithSiblingNavigation() {
        List<MessageTreeNode> tree = assembler.assemble(List.of(
                message(1L, null, 1, 0),
                message(2L, 1L, 1, 0),
                message(3L, 2L, 0, 0),
                message(4L, 2L, 1, 1),
                message(5L, 4L, 1, 0)
        ));

        assertIterableEquals(List.of("1", "2", "4", "5"),
                tree.stream().map(node -> node.message().getId()).toList());
        MessageTreeNode branched = tree.get(2);
        assertEquals(2, branched.branchIndex());
        assertEquals(2, branched.branchTotal());
        assertIterableEquals(List.of(3L), branched.preSiblings());
        assertEquals(List.of(), branched.nextSiblings());
    }

    @Test
    void shouldSelectTargetAncestorsAndDefaultDescendantsForNonLeafSwitch() {
        Set<Long> activeIds = assembler.selectActivePathIds(List.of(
                message(1L, null, 1, 0),
                message(2L, 1L, 1, 0),
                message(3L, 2L, 0, 0),
                message(4L, 2L, 1, 1),
                message(5L, 3L, 0, 0),
                message(6L, 5L, 0, 0)
        ), 3L);

        assertEquals(Set.of(1L, 2L, 3L, 5L, 6L), activeIds);
    }

    private static ConversationMessageRecord message(Long id, Long parentId, int active, int siblingSeq) {
        ConversationMessageRecord record = new ConversationMessageRecord();
        record.setId(String.valueOf(id));
        record.setConversationId("1");
        record.setRole("user");
        record.setContent("message-" + id);
        record.setParentId(parentId);
        record.setActive(active);
        record.setSiblingSeq(siblingSeq);
        return record;
    }
}
