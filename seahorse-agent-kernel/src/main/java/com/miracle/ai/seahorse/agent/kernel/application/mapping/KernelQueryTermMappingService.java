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

package com.miracle.ai.seahorse.agent.kernel.application.mapping;

import com.miracle.ai.seahorse.agent.ports.inbound.mapping.QueryTermMappingInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingPage;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRepositoryPort;

import java.util.Objects;

/**
 * Kernel 层术语映射管理服务。
 */
public class KernelQueryTermMappingService implements QueryTermMappingInboundPort {

    private static final String CACHE_KEY = "seahorse-agent:query-term:mappings";
    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 10L;
    private static final long MAX_SIZE = 100L;

    private final QueryTermMappingRepositoryPort repositoryPort;
    private final KeyValueCachePort cachePort;

    public KernelQueryTermMappingService(QueryTermMappingRepositoryPort repositoryPort, KeyValueCachePort cachePort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.cachePort = Objects.requireNonNull(cachePort, "cachePort must not be null");
    }

    @Override
    public QueryTermMappingPage page(long current, long size, String keyword) {
        return repositoryPort.page(normalizeCurrent(current), normalizeSize(size), trimToNull(keyword));
    }

    @Override
    public QueryTermMappingRecord queryById(String id) {
        return repositoryPort.findById(requireText(id, "id must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("mapping not found"));
    }

    @Override
    public String create(QueryTermMappingPayload payload) {
        QueryTermMappingPayload safePayload = validateCreate(payload);
        String id = repositoryPort.create(safePayload);
        clearCache();
        return id;
    }

    @Override
    public void update(String id, QueryTermMappingPayload payload) {
        QueryTermMappingPayload safePayload = validateUpdate(payload);
        if (!repositoryPort.update(requireText(id, "id must not be blank"), safePayload)) {
            throw new IllegalArgumentException("mapping not found");
        }
        clearCache();
    }

    @Override
    public void delete(String id) {
        if (!repositoryPort.delete(requireText(id, "id must not be blank"))) {
            throw new IllegalArgumentException("mapping not found");
        }
        clearCache();
    }

    private QueryTermMappingPayload validateCreate(QueryTermMappingPayload payload) {
        QueryTermMappingPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        return new QueryTermMappingPayload(
                requireText(safePayload.sourceTerm(), "sourceTerm must not be blank"),
                requireText(safePayload.targetTerm(), "targetTerm must not be blank"),
                safePayload.matchType(),
                safePayload.priority(),
                safePayload.enabled(),
                trimToNull(safePayload.remark()));
    }

    private QueryTermMappingPayload validateUpdate(QueryTermMappingPayload payload) {
        QueryTermMappingPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        String sourceTerm = safePayload.sourceTerm() == null
                ? null
                : requireText(safePayload.sourceTerm(), "sourceTerm must not be blank");
        String targetTerm = safePayload.targetTerm() == null
                ? null
                : requireText(safePayload.targetTerm(), "targetTerm must not be blank");
        return new QueryTermMappingPayload(sourceTerm, targetTerm, safePayload.matchType(),
                safePayload.priority(), safePayload.enabled(), trimToNull(safePayload.remark()));
    }

    private long normalizeCurrent(long current) {
        return current < 1L ? DEFAULT_CURRENT : current;
    }

    private long normalizeSize(long size) {
        if (size < 1L) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private String requireText(String value, String message) {
        String safeValue = trimToNull(value);
        if (safeValue == null) {
            throw new IllegalArgumentException(message);
        }
        return safeValue;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void clearCache() {
        cachePort.delete(CACHE_KEY);
    }
}
