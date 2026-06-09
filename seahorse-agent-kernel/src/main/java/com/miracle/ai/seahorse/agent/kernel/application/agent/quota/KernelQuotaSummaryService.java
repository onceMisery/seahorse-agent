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

package com.miracle.ai.seahorse.agent.kernel.application.agent.quota;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.EstimatedDurationTier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaCostTier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaSummaryStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.UserQuotaSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateId;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.TaskTemplateQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaSummaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.UserQuotaSummaryQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.QuotaPolicyRepositoryPort;

import java.util.Objects;
import java.util.Optional;

public class KernelQuotaSummaryService implements QuotaSummaryInboundPort {

    private static final long DEFAULT_CALL_LIMIT = 100L;
    private static final double DEFAULT_COST_LIMIT = 20.0d;

    private final QuotaPolicyRepositoryPort quotaPolicyRepository;
    private final CostUsageRepositoryPort costUsageRepository;
    private final TaskTemplateQueryInboundPort taskTemplateQueryPort;

    public KernelQuotaSummaryService() {
        this(null, null, null);
    }

    public KernelQuotaSummaryService(QuotaPolicyRepositoryPort quotaPolicyRepository,
                                     CostUsageRepositoryPort costUsageRepository) {
        this(quotaPolicyRepository, costUsageRepository, null);
    }

    public KernelQuotaSummaryService(QuotaPolicyRepositoryPort quotaPolicyRepository,
                                     CostUsageRepositoryPort costUsageRepository,
                                     TaskTemplateQueryInboundPort taskTemplateQueryPort) {
        this.quotaPolicyRepository = quotaPolicyRepository;
        this.costUsageRepository = costUsageRepository;
        this.taskTemplateQueryPort = taskTemplateQueryPort;
    }

    @Override
    public UserQuotaSummary summary(UserQuotaSummaryQuery query) {
        UserQuotaSummaryQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        Optional<QuotaPolicy> policy = activeUserPolicy(safeQuery);
        CostUsageAggregate usage = aggregateUsage(safeQuery);
        Long callLimit = policy.map(QuotaPolicy::callLimit).orElse(DEFAULT_CALL_LIMIT);
        Double costLimit = policy.map(QuotaPolicy::costLimit).orElse(DEFAULT_COST_LIMIT);
        long usedCalls = usage == null ? 0L : usage.totalCalls();
        double usedCost = usage == null ? 0d : usage.totalCost();
        Long remainingCalls = callLimit == null ? null : Math.max(0L, callLimit - usedCalls);
        Double remainingCost = costLimit == null ? null : Math.max(0d, costLimit - usedCost);
        QuotaSummaryStatus status = status(callLimit, usedCalls, costLimit, usedCost);
        return new UserQuotaSummary(
                safeQuery.userId(),
                safeQuery.tenantId(),
                status,
                callLimit,
                usedCalls,
                remainingCalls,
                costLimit,
                usedCost,
                remainingCost,
                costTier(safeQuery.taskTemplateId()),
                durationTier(safeQuery.taskTemplateId()),
                message(status));
    }

    private Optional<QuotaPolicy> activeUserPolicy(UserQuotaSummaryQuery query) {
        if (quotaPolicyRepository == null) {
            return Optional.empty();
        }
        Optional<QuotaPolicy> userPolicy = quotaPolicyRepository.findActive(query.tenantId(), QuotaScope.USER,
                query.userId());
        if (userPolicy.isPresent()) {
            return userPolicy;
        }
        return quotaPolicyRepository.findActive(query.tenantId(), QuotaScope.TENANT, query.tenantId());
    }

    private CostUsageAggregate aggregateUsage(UserQuotaSummaryQuery query) {
        if (costUsageRepository == null) {
            return null;
        }
        return costUsageRepository.aggregate(new CostUsageQuery(query.tenantId(), null, null, null, null));
    }

    private QuotaSummaryStatus status(Long callLimit, long usedCalls, Double costLimit, double usedCost) {
        if ((callLimit != null && usedCalls >= callLimit) || (costLimit != null && usedCost >= costLimit)) {
            return QuotaSummaryStatus.EXCEEDED;
        }
        boolean nearCallLimit = callLimit != null && usedCalls >= Math.ceil(callLimit * QuotaPolicyLimits.DEFAULT_WARN_RATIO);
        boolean nearCostLimit = costLimit != null && usedCost >= costLimit * QuotaPolicyLimits.DEFAULT_WARN_RATIO;
        return nearCallLimit || nearCostLimit ? QuotaSummaryStatus.NEAR_LIMIT : QuotaSummaryStatus.AVAILABLE;
    }

    private QuotaCostTier costTier(String taskTemplateId) {
        return taskTemplate(taskTemplateId)
                .map(TaskTemplate::maxCostTier)
                .orElseGet(() -> fallbackCostTier(taskTemplateId));
    }

    private EstimatedDurationTier durationTier(String taskTemplateId) {
        return taskTemplate(taskTemplateId)
                .map(TaskTemplate::estimatedDuration)
                .orElseGet(() -> fallbackDurationTier(taskTemplateId));
    }

    private Optional<TaskTemplate> taskTemplate(String taskTemplateId) {
        if (taskTemplateQueryPort == null) {
            return Optional.empty();
        }
        return parseTemplateId(taskTemplateId)
                .flatMap(taskTemplateQueryPort::findById);
    }

    private QuotaCostTier fallbackCostTier(String taskTemplateId) {
        return parseTemplateId(taskTemplateId)
                .map(templateId -> templateId == TaskTemplateId.DEEP_RESEARCH
                        || templateId == TaskTemplateId.GITHUB_VISUAL_PROJECT_INTRO ? QuotaCostTier.HIGH
                        : templateId == TaskTemplateId.QUICK_ANSWER ? QuotaCostTier.LOW : QuotaCostTier.MEDIUM)
                .orElse(QuotaCostTier.LOW);
    }

    private EstimatedDurationTier fallbackDurationTier(String taskTemplateId) {
        return parseTemplateId(taskTemplateId)
                .map(templateId -> templateId == TaskTemplateId.DEEP_RESEARCH
                        || templateId == TaskTemplateId.GITHUB_VISUAL_PROJECT_INTRO ? EstimatedDurationTier.LONG
                        : templateId == TaskTemplateId.QUICK_ANSWER ? EstimatedDurationTier.SHORT
                                : EstimatedDurationTier.MEDIUM)
                .orElse(EstimatedDurationTier.SHORT);
    }

    private Optional<TaskTemplateId> parseTemplateId(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TaskTemplateId.fromValue(value.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private String message(QuotaSummaryStatus status) {
        return switch (status) {
            case AVAILABLE -> "Quota is available.";
            case NEAR_LIMIT -> "Quota is close to the limit.";
            case EXCEEDED -> "Quota limit has been reached.";
        };
    }
}
