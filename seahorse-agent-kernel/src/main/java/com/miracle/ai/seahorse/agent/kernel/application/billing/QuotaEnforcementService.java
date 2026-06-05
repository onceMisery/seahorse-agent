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

package com.miracle.ai.seahorse.agent.kernel.application.billing;

import com.miracle.ai.seahorse.agent.kernel.domain.billing.QuotaExceededException;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.Subscription;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Quota enforcement service that checks subscription limits before resource-consuming operations.
 *
 * <p>All checks are designed to be fail-open: if no active subscription exists or the
 * quota system is not configured, the check passes silently. This ensures backward
 * compatibility when the billing module is not yet active.
 */
public class QuotaEnforcementService {

    private final SubscriptionRepositoryPort subscriptionRepository;
    private final KnowledgeDocumentRepositoryPort documentRepository;
    private final AgentRunRepositoryPort agentRunRepository;

    public QuotaEnforcementService(SubscriptionRepositoryPort subscriptionRepository,
                                    KnowledgeDocumentRepositoryPort documentRepository,
                                    AgentRunRepositoryPort agentRunRepository) {
        this.subscriptionRepository = Objects.requireNonNull(subscriptionRepository,
                "subscriptionRepository must not be null");
        this.documentRepository = Objects.requireNonNull(documentRepository,
                "documentRepository must not be null");
        this.agentRunRepository = Objects.requireNonNull(agentRunRepository,
                "agentRunRepository must not be null");
    }

    /**
     * Checks storage quota before a document upload.
     *
     * <p>Verifies that the tenant's current storage usage plus the additional bytes
     * does not exceed the subscription's storage limit.
     *
     * @param tenantId        the tenant identifier
     * @param additionalBytes the size of the file being uploaded, in bytes
     * @throws QuotaExceededException if the storage quota would be exceeded
     */
    public void checkStorageQuota(String tenantId, long additionalBytes) {
        Subscription subscription = loadActiveSubscription(tenantId);
        if (subscription == null) {
            return; // No active subscription — skip enforcement
        }

        long limit = subscription.storageLimitBytes();
        if (limit <= 0) {
            return; // Unlimited or not configured
        }

        long currentUsage = safeCall(() -> documentRepository.sumTotalFileSizeByTenantId(tenantId));
        if (currentUsage + additionalBytes > limit) {
            throw new QuotaExceededException(
                    "STORAGE_LIMIT_EXCEEDED",
                    "Storage quota exceeded. Current usage: " + formatBytes(currentUsage)
                            + ", limit: " + formatBytes(limit)
                            + ". Please upgrade your plan for more storage.");
        }
    }

    /**
     * Checks token quota before an agent run starts.
     *
     * <p>Verifies that the tenant has not exhausted their token allocation.
     * Note: This is a simplified check based on subscription limits; actual
     * token consumption tracking would require a usage metrics port.
     *
     * @param tenantId the tenant identifier
     * @throws QuotaExceededException if the token quota is exhausted
     */
    public void checkTokenQuota(String tenantId) {
        Subscription subscription = loadActiveSubscription(tenantId);
        if (subscription == null) {
            return;
        }

        long tokenLimit = subscription.tokenLimit();
        if (tokenLimit <= 0) {
            return; // Unlimited or not configured
        }

        // Token usage tracking would require a dedicated usage metrics port.
        // For now, this is a placeholder that can be enhanced with actual usage data.
    }

    /**
     * Checks concurrency quota before an agent run starts.
     *
     * <p>Verifies that the tenant's number of active (RUNNING) agent runs
     * does not reach the subscription's concurrency limit.
     *
     * @param tenantId the tenant identifier
     * @throws QuotaExceededException if the concurrency limit is reached
     */
    public void checkConcurrencyQuota(String tenantId) {
        Subscription subscription = loadActiveSubscription(tenantId);
        if (subscription == null) {
            return;
        }

        int concurrencyLimit = subscription.concurrencyLimit();
        if (concurrencyLimit <= 0) {
            return; // Unlimited or not configured
        }

        long activeRuns = safeCall(() -> agentRunRepository.countActiveRunsByTenantId(tenantId));
        if (activeRuns >= concurrencyLimit) {
            throw new QuotaExceededException(
                    "CONCURRENCY_LIMIT_EXCEEDED",
                    "Concurrency limit reached. Active runs: " + activeRuns
                            + ", limit: " + concurrencyLimit
                            + ". Please upgrade your plan for higher concurrency.");
        }
    }

    /**
     * Loads the active, non-expired subscription for a tenant.
     *
     * @return the active subscription, or null if none exists
     */
    private Subscription loadActiveSubscription(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        try {
            Optional<Subscription> opt = subscriptionRepository.findActiveByTenantId(tenantId);
            if (opt.isEmpty()) {
                return null;
            }
            Subscription sub = opt.get();
            if (!sub.isActive() || sub.isExpired(Instant.now())) {
                return null;
            }
            return sub;
        } catch (Exception ex) {
            // Fail-open: if subscription lookup fails, don't block the operation
            return null;
        }
    }

    private long safeCall(java.util.function.Supplier<Long> supplier) {
        try {
            Long result = supplier.get();
            return result != null ? result : 0L;
        } catch (Exception ex) {
            return 0L;
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
