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

import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * Seahorse 原生会话管理 Web adapter。
 *
 * <p>用户 ID 优先从请求参数或 {@code X-User-Id} 请求头传入；未显式传入时使用当前登录用户。
 */
@RestController
public class SeahorseConversationController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final ObjectProvider<ConversationManagementInboundPort> conversationPortProvider;

    public SeahorseConversationController(ObjectProvider<ConversationManagementInboundPort> conversationPortProvider) {
        this.conversationPortProvider = conversationPortProvider;
    }

    @PostMapping({"/conversations", "/api/conversations"})
    public Map<String, Object> create(@RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                conversationPortProvider.getIfAvailable().create(resolveUserId(userId, headerUserId)));
    }

    @GetMapping({"/conversations", "/api/conversations"})
    public Map<String, Object> listConversations(@RequestParam(required = false) String userId,
                                                 @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                                 String headerUserId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                conversationPortProvider.getIfAvailable().listConversations(resolveUserId(userId, headerUserId)));
    }

    @PutMapping({"/conversations/{conversationId}", "/api/conversations/{conversationId}"})
    public Map<String, Object> rename(@PathVariable String conversationId,
                                      @RequestBody ConversationUpdateRequest request,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        ConversationUpdateRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        conversationPortProvider.getIfAvailable().rename(conversationId, resolveUserId(userId, headerUserId), safeRequest.title());
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @DeleteMapping({"/conversations/{conversationId}", "/api/conversations/{conversationId}"})
    public Map<String, Object> delete(@PathVariable String conversationId,
                                      @RequestParam(required = false) String userId,
                                      @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                      String headerUserId) {
        conversationPortProvider.getIfAvailable().delete(conversationId, resolveUserId(userId, headerUserId));
        return Map.of(KEY_CODE, SUCCESS_CODE);
    }

    @GetMapping({"/conversations/{conversationId}/messages", "/api/conversations/{conversationId}/messages"})
    public Map<String, Object> listMessages(@PathVariable String conversationId,
                                            @RequestParam(required = false) String userId,
                                            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                            String headerUserId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                conversationPortProvider.getIfAvailable().listMessages(conversationId, resolveUserId(userId, headerUserId)));
    }

    private String resolveUserId(String userId, String headerUserId) {
        return WebUserIdResolver.resolve(userId, headerUserId);
    }
}
