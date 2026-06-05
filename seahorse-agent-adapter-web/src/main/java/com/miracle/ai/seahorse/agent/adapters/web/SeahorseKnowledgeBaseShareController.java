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

import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeBaseShareService;
import com.miracle.ai.seahorse.agent.kernel.domain.knowledge.KnowledgeBaseShare;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class SeahorseKnowledgeBaseShareController {

    private final ObjectProvider<KnowledgeBaseShareService> shareServiceProvider;

    public SeahorseKnowledgeBaseShareController(ObjectProvider<KnowledgeBaseShareService> shareServiceProvider) {
        this.shareServiceProvider = shareServiceProvider;
    }

    @PostMapping("/api/knowledge-bases/{kbId}/share")
    public ApiResponse<ShareResponse> createShare(@PathVariable Long kbId,
                                                  @RequestBody CreateShareRequest request) {
        CreateShareRequest safe = request == null
                ? new CreateShareRequest(null, 0, null)
                : request;
        return ApiResponses.requireService(shareServiceProvider,
                svc -> ShareResponse.from(svc.createShare(kbId, safe.password(), safe.maxAccessCount(), safe.expiryDays())));
    }

    @GetMapping("/api/knowledge-bases/share/{token}")
    public ApiResponse<ShareResponse> accessShare(@PathVariable String token,
                                                  @RequestParam(required = false) String password) {
        return ApiResponses.requireService(shareServiceProvider,
                svc -> svc.accessShare(token, password)
                        .map(ShareResponse::from)
                        .orElse(null));
    }

    @GetMapping("/api/knowledge-bases/{kbId}/shares")
    public ApiResponse<List<ShareResponse>> listShares(@PathVariable Long kbId) {
        return ApiResponses.requireService(shareServiceProvider,
                svc -> svc.listShares(kbId).stream()
                        .map(ShareResponse::from)
                        .toList());
    }

    @DeleteMapping("/api/knowledge-bases/{kbId}/shares/{shareId}")
    public ApiResponse<Boolean> deleteShare(@PathVariable Long kbId,
                                            @PathVariable Long shareId) {
        return ApiResponses.requireService(shareServiceProvider,
                svc -> svc.deleteShare(shareId));
    }

    public record CreateShareRequest(String password, int maxAccessCount, Integer expiryDays) {
    }

    public record ShareResponse(Long id,
                                Long kbId,
                                String tenantId,
                                String shareToken,
                                boolean hasPassword,
                                Instant expiresAt,
                                int maxAccessCount,
                                int currentAccessCount,
                                Instant createdAt) {

        static ShareResponse from(KnowledgeBaseShare share) {
            return new ShareResponse(
                    share.id(),
                    share.kbId(),
                    share.tenantId(),
                    share.shareToken(),
                    share.passwordHash() != null && !share.passwordHash().isBlank(),
                    share.expiresAt(),
                    share.maxAccessCount(),
                    share.currentAccessCount(),
                    share.createdAt());
        }
    }
}
