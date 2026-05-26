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
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationAttachmentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.UploadConversationAttachmentCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class SeahorseConversationAttachmentController {

    private static final String KEY_DELETED = "deleted";

    private final ObjectProvider<ConversationAttachmentInboundPort> attachmentPortProvider;

    public SeahorseConversationAttachmentController(
            ObjectProvider<ConversationAttachmentInboundPort> attachmentPortProvider) {
        this.attachmentPortProvider = attachmentPortProvider;
    }

    @PostMapping(value = "/api/conversations/{conversationId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ConversationAttachment> upload(
            @PathVariable String conversationId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String messageId,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId)
            throws IOException {
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        return ApiResponses.requireService(attachmentPortProvider, port -> {
            try {
                return port.upload(new UploadConversationAttachmentCommand(
                        conversationId,
                        messageId,
                        resolvedUserId,
                        file.getInputStream(),
                        file.getSize(),
                        file.getOriginalFilename(),
                        file.getContentType()));
            } catch (IOException ex) {
                throw new IllegalStateException("read attachment upload failed", ex);
            }
        });
    }

    @GetMapping("/api/conversations/{conversationId}/attachments")
    public ApiResponse<List<ConversationAttachment>> list(
            @PathVariable String conversationId,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId) {
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        return ApiResponses.requireService(attachmentPortProvider, port -> port.list(conversationId, resolvedUserId));
    }

    @DeleteMapping("/api/conversations/{conversationId}/attachments/{attachmentId}")
    public ApiResponse<Map<String, Boolean>> delete(
            @PathVariable String conversationId,
            @PathVariable String attachmentId,
            @RequestParam(required = false) String userId,
            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false) String headerUserId) {
        String resolvedUserId = WebUserIdResolver.resolve(userId, headerUserId);
        return ApiResponses.requireService(attachmentPortProvider, port -> {
            port.delete(conversationId, attachmentId, resolvedUserId);
            return Map.of(KEY_DELETED, true);
        });
    }
}
