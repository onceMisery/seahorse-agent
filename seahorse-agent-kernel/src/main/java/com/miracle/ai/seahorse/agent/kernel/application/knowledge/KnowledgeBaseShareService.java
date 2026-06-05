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

package com.miracle.ai.seahorse.agent.kernel.application.knowledge;

import com.miracle.ai.seahorse.agent.kernel.domain.knowledge.KnowledgeBaseShare;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseShareRepositoryPort;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 知识库分享管理服务，支持创建分享链接、访问验证、分享管理。
 */
public class KnowledgeBaseShareService {

    private static final int DEFAULT_EXPIRY_DAYS = 7;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final KnowledgeBaseShareRepositoryPort repositoryPort;

    public KnowledgeBaseShareService(KnowledgeBaseShareRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

    /**
     * 创建分享链接。
     *
     * @param kbId           知识库 ID
     * @param password       可选密码（null 表示无密码）
     * @param maxAccessCount 最大访问次数（0 表示不限制）
     * @param expiryDays     有效天数（null 使用默认 7 天）
     * @return 分享记录
     */
    public KnowledgeBaseShare createShare(Long kbId, String password, int maxAccessCount, Integer expiryDays) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        if (maxAccessCount < 0) {
            throw new IllegalArgumentException("maxAccessCount 不能为负数");
        }

        String tenantId = TenantContext.get();
        String token = UUID.randomUUID().toString().replace("-", "");
        String passwordHash = password != null && !password.isBlank() ? hashPassword(password) : null;
        Instant now = Instant.now();
        int days = expiryDays != null && expiryDays > 0 ? expiryDays : DEFAULT_EXPIRY_DAYS;
        Instant expiresAt = now.plus(days, ChronoUnit.DAYS);

        KnowledgeBaseShare share = new KnowledgeBaseShare(
                null, kbId, tenantId, token, passwordHash, expiresAt, maxAccessCount, 0, now
        );
        Long id = repositoryPort.save(share);

        return new KnowledgeBaseShare(id, kbId, tenantId, token, passwordHash, expiresAt, maxAccessCount, 0, now);
    }

    /**
     * 访问分享链接。
     *
     * @param token    分享令牌
     * @param password 密码（如果需要）
     * @return 分享记录，如果无效则返回 empty
     */
    public Optional<KnowledgeBaseShare> accessShare(String token, String password) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token 不能为空");
        }

        Optional<KnowledgeBaseShare> shareOpt = repositoryPort.findByToken(token);
        if (shareOpt.isEmpty()) {
            return Optional.empty();
        }

        KnowledgeBaseShare share = shareOpt.get();
        Instant now = Instant.now();

        // 检查是否过期
        if (share.isExpired(now)) {
            return Optional.empty();
        }

        // 检查是否达到访问上限
        if (share.isAccessLimitReached()) {
            return Optional.empty();
        }

        // 验证密码（如果设置了密码）
        if (share.passwordHash() != null && !share.passwordHash().isBlank()) {
            if (password == null || password.isBlank()) {
                return Optional.empty(); // 需要密码但未提供
            }
            String inputHash = hashPassword(password);
            if (!share.passwordHash().equals(inputHash)) {
                return Optional.empty(); // 密码错误
            }
        }

        // 增加访问次数
        repositoryPort.incrementAccessCount(share.id());

        return Optional.of(share.incrementAccess());
    }

    /**
     * 查询知识库的所有分享。
     */
    public List<KnowledgeBaseShare> listShares(Long kbId) {
        if (kbId == null) {
            throw new IllegalArgumentException("kbId 不能为空");
        }
        return repositoryPort.findByKbId(kbId);
    }

    /**
     * 删除分享。
     */
    public boolean deleteShare(Long shareId) {
        if (shareId == null) {
            throw new IllegalArgumentException("shareId 不能为空");
        }
        return repositoryPort.deleteById(shareId);
    }

    /**
     * 清理过期分享。
     */
    public int cleanupExpiredShares() {
        return repositoryPort.deleteExpired(Instant.now());
    }

    /**
     * 简单密码哈希（SHA-256）。生产环境应使用 BCrypt。
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }
}
