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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachment;
import com.miracle.ai.seahorse.agent.kernel.domain.conversation.ConversationAttachmentParseStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationAttachmentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.UploadConversationAttachmentCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseConversationAttachmentControllerTests {

    @Test
    void shouldUploadAndListConversationAttachmentForCurrentUser() throws Exception {
        ConversationAttachmentInboundPort port = mock(ConversationAttachmentInboundPort.class);
        ConversationAttachment attachment = attachment("attachment-1", "conversation-1", "user-1");
        when(port.upload(org.mockito.ArgumentMatchers.any())).thenReturn(attachment);
        when(port.list("conversation-1", "user-1")).thenReturn(List.of(attachment));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(port)).build();

        mvc.perform(multipart("/api/conversations/conversation-1/attachments")
                        .file(new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes()))
                        .header(WebUserIdResolver.HEADER_USER_ID, "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.attachmentId").value("attachment-1"))
                .andExpect(jsonPath("$.data.parseStatus").value("PENDING"));
        mvc.perform(get("/api/conversations/conversation-1/attachments")
                        .header(WebUserIdResolver.HEADER_USER_ID, "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].attachmentId").value("attachment-1"));

        ArgumentCaptor<UploadConversationAttachmentCommand> captor =
                ArgumentCaptor.forClass(UploadConversationAttachmentCommand.class);
        verify(port).upload(captor.capture());
        assertThat(captor.getValue().conversationId()).isEqualTo("conversation-1");
        assertThat(captor.getValue().userId()).isEqualTo("user-1");
        assertThat(captor.getValue().fileName()).isEqualTo("note.txt");
    }

    @Test
    void shouldMapAttachmentOwnershipViolationToForbidden() throws Exception {
        ConversationAttachmentInboundPort port = mock(ConversationAttachmentInboundPort.class);
        org.mockito.Mockito.doThrow(new SecurityException("attachment does not belong to current user"))
                .when(port).delete("conversation-1", "attachment-1", "intruder");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller(port))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();

        mvc.perform(delete("/api/conversations/conversation-1/attachments/attachment-1")
                        .header(WebUserIdResolver.HEADER_USER_ID, "intruder"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static SeahorseConversationAttachmentController controller(ConversationAttachmentInboundPort port) {
        return new SeahorseConversationAttachmentController(provider(ConversationAttachmentInboundPort.class, port));
    }

    private static ConversationAttachment attachment(String attachmentId, String conversationId, String userId) {
        return new ConversationAttachment(
                attachmentId,
                conversationId,
                null,
                userId,
                "note.txt",
                "text/plain",
                5L,
                "storage://note.txt",
                ConversationAttachmentParseStatus.PENDING,
                "{}",
                Instant.parse("2026-05-26T00:00:00Z"));
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T instance) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        if (instance != null) {
            beanFactory.addBean(type.getName(), instance);
        }
        return beanFactory.getBeanProvider(type);
    }
}
