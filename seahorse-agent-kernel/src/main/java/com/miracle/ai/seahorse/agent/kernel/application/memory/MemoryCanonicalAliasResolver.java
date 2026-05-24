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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Slice 3 续 cut 7：在 capture metadata 上挂载 canonical alias 信息（如 "k8s" → "Kubernetes"）。
 *
 * <p>原 facade 私有方法 {@code attachCanonicalAliasMetadata} + {@code aliasLookupTokens} 合并
 * 到本 service，对外只暴露 {@link #attachIfResolved(Map, String, String, String)} 单一入口。
 *
 * <p>行为约定保持与 facade 完全一致：
 * <ul>
 *     <li>已含 {@code canonicalEntityId} key 时直接返回（首次命中即终止，不覆盖）。</li>
 *     <li>{@code userId} 空时返回；</li>
 *     <li>按 {@code ALIAS_TOKEN_PATTERN} 提取 content 中的候选 token（最多 {@value MAX_ALIAS_TOKEN_LOOKUPS} 个），
 *         逐个调用 {@link MemoryAliasPort#resolveAlias(String, String, String)}；</li>
 *     <li>首个返回 canonical entity 的命中即写入 5 个 metadata key 并 short-circuit；</li>
 *     <li>所有异常 swallow，仅打 debug 日志，保持 capture 主流程稳健。</li>
 * </ul>
 *
 * <p>5 个 metadata key 与 alias 配置常量也随之搬入：facade 之后不再持有 alias 相关 magic value。
 */
public final class MemoryCanonicalAliasResolver {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryCanonicalAliasResolver.class);

    static final String METADATA_CANONICAL_ENTITY_ID = "canonicalEntityId";
    static final String METADATA_CANONICAL_NAME = "canonicalName";
    static final String METADATA_CANONICAL_ENTITY_TYPE = "canonicalEntityType";
    static final String METADATA_ALIAS_TEXT = "aliasText";
    static final String METADATA_ALIAS_CONFIDENCE_LEVEL = "aliasConfidenceLevel";

    private static final Pattern ALIAS_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:+#-]{1,63}");
    private static final int MAX_ALIAS_TOKEN_LOOKUPS = 16;

    private final MemoryAliasPort memoryAliasPort;

    public MemoryCanonicalAliasResolver(MemoryAliasPort memoryAliasPort) {
        this.memoryAliasPort = Objects.requireNonNull(memoryAliasPort, "memoryAliasPort must not be null");
    }

    /**
     * 在 capture metadata 上尝试挂载 canonical alias；若已挂载或解析失败则 no-op。
     */
    public void attachIfResolved(Map<String, Object> metadata, String userId, String tenantId, String content) {
        if (metadata == null
                || metadata.containsKey(METADATA_CANONICAL_ENTITY_ID)
                || isBlank(userId)) {
            return;
        }
        for (String aliasText : aliasLookupTokens(content)) {
            try {
                Optional<MemoryAliasResolution> resolved =
                        memoryAliasPort.resolveAlias(userId, tenantId, aliasText);
                if (resolved.isEmpty() || isBlank(resolved.get().canonicalEntityId())) {
                    continue;
                }
                MemoryAliasResolution alias = resolved.get();
                metadata.put(METADATA_CANONICAL_ENTITY_ID, alias.canonicalEntityId());
                metadata.put(METADATA_CANONICAL_NAME, alias.canonicalName());
                metadata.put(METADATA_CANONICAL_ENTITY_TYPE, alias.entityType());
                metadata.put(METADATA_ALIAS_TEXT, isBlank(alias.aliasText()) ? aliasText : alias.aliasText());
                metadata.put(METADATA_ALIAS_CONFIDENCE_LEVEL, alias.confidenceLevel());
                return;
            } catch (RuntimeException ex) {
                LOG.debug("Memory alias resolution failed during write: userId={}, tenantId={}, aliasText={}, error={}",
                        userId, tenantId, aliasText,
                        Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            }
        }
    }

    private static List<String> aliasLookupTokens(String content) {
        if (isBlank(content)) {
            return List.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = ALIAS_TOKEN_PATTERN.matcher(content);
        while (matcher.find() && tokens.size() < MAX_ALIAS_TOKEN_LOOKUPS) {
            String token = matcher.group();
            if (!isBlank(token)) {
                tokens.add(token.trim());
            }
        }
        return List.copyOf(tokens);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
