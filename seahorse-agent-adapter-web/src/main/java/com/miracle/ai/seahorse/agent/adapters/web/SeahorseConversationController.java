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

import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationBranchInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ForkCommand;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ObjectProvider<ConversationBranchInboundPort> branchPortProvider;

    public SeahorseConversationController(ObjectProvider<ConversationManagementInboundPort> conversationPortProvider) {
        this(conversationPortProvider, null);
    }

    @Autowired
    public SeahorseConversationController(ObjectProvider<ConversationManagementInboundPort> conversationPortProvider,
            ObjectProvider<ConversationBranchInboundPort> branchPortProvider) {
        this.conversationPortProvider = conversationPortProvider;
        this.branchPortProvider = branchPortProvider;
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

    @GetMapping({"/conversations/{conversationId}/messages/tree", "/api/conversations/{conversationId}/messages/tree"})
    public Map<String, Object> loadActiveTree(@PathVariable String conversationId,
                                              @RequestParam(required = false) String userId,
                                              @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                              String headerUserId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                branchPort().loadActiveTree(conversationId, resolveUserId(userId, headerUserId)));
    }

    @PostMapping({"/conversations/{conversationId}/messages/fork", "/api/conversations/{conversationId}/messages/fork"})
    public Map<String, Object> fork(@PathVariable String conversationId,
                                    @RequestBody ConversationForkRequest request,
                                    @RequestParam(required = false) String userId,
                                    @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                    String headerUserId) {
        ConversationForkRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String resolvedUserId = resolveUserId(userId, headerUserId);
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                branchPort().fork(new ForkCommand(
                        conversationId,
                        resolvedUserId,
                        safeRequest.anchorMessageId(),
                        safeRequest.content(),
                        safeRequest.role(),
                        safeRequest.regenerate())));
    }

    @PostMapping({
            "/conversations/{conversationId}/messages/branch/switch",
            "/api/conversations/{conversationId}/messages/branch/switch"
    })
    public Map<String, Object> switchBranch(@PathVariable String conversationId,
                                            @RequestBody ConversationBranchSwitchRequest request,
                                            @RequestParam(required = false) String userId,
                                            @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                            String headerUserId) {
        ConversationBranchSwitchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                branchPort().switchBranch(conversationId, resolveUserId(userId, headerUserId), safeRequest.targetNodeId()));
    }

    @PostMapping({"/conversations/{conversationId}/branch-cursor", "/api/conversations/{conversationId}/branch-cursor"})
    public Map<String, Object> saveBranchCursor(@PathVariable String conversationId,
                                                @RequestBody ConversationBranchCursorRequest request,
                                                @RequestParam(required = false) String userId,
                                                @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                                String headerUserId) {
        ConversationBranchCursorRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                branchPort().saveCursor(
                        conversationId,
                        resolveUserId(userId, headerUserId),
                        safeRequest.getLeafMessageId()));
    }

    @GetMapping({"/conversations/{conversationId}/branch-cursor", "/api/conversations/{conversationId}/branch-cursor"})
    public Map<String, Object> loadBranchCursor(@PathVariable String conversationId,
                                                @RequestParam(required = false) String userId,
                                                @RequestHeader(value = WebUserIdResolver.HEADER_USER_ID, required = false)
                                                String headerUserId) {
        return branchPort()
                .loadCursor(conversationId, resolveUserId(userId, headerUserId))
                .<Map<String, Object>>map(cursor -> Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, cursor))
                .orElseGet(() -> Map.of(KEY_CODE, SUCCESS_CODE));
    }

    private String resolveUserId(String userId, String headerUserId) {
        return WebUserIdResolver.resolve(userId, headerUserId);
    }

    private ConversationBranchInboundPort branchPort() {
        ConversationBranchInboundPort port = branchPortProvider == null ? null : branchPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException("ConversationBranchInboundPort is not configured");
        }
        return port;
    }
}
