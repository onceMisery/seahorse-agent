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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.adapters.web.AdvancedFeatureGate;
import com.miracle.ai.seahorse.agent.adapters.web.ReadinessController;
import com.miracle.ai.seahorse.agent.kernel.application.readiness.KernelReadinessService;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.readiness.ReadinessProbePort;
import com.miracle.ai.seahorse.agent.ports.outbound.readiness.ReadinessProbePort.ComponentStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统就绪诊断自动配置。
 * <p>
 * 注册 ReadinessProbePort（检查各适配器 Bean 可用性）、
 * KernelReadinessService（业务逻辑）和 ReadinessController（REST 端点）。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        SeahorseAgentVectorAdapterAutoConfiguration.class,
        SeahorseAgentKeywordAdapterAutoConfiguration.class,
        SeahorseAgentAiAdapterAutoConfiguration.class,
        SeahorseAgentCacheAdapterAutoConfiguration.class,
        SeahorseAgentMqAdapterAutoConfiguration.class,
        SeahorseAgentStorageAdapterAutoConfiguration.class
})
@ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SeahorseAgentReadinessAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ReadinessProbePort.class)
    public ReadinessProbePort seahorseReadinessProbe(
            ObjectProvider<DataSource> dataSource,
            ObjectProvider<StreamingChatModelPort> chatModel,
            ObjectProvider<VectorSearchPort> vectorSearch,
            ObjectProvider<KeywordSearchPort> keywordSearch,
            ObjectProvider<KeyValueCachePort> cache,
            ObjectProvider<MessageQueuePort> mq,
            ObjectProvider<ObjectStoragePort> storage,
            @Value("${seahorse-agent.adapters.vector.type:noop}") String vectorType,
            @Value("${seahorse-agent.adapters.cache.type:local}") String cacheType,
            @Value("${seahorse-agent.adapters.mq.type:direct}") String mqType,
            @Value("${seahorse-agent.adapters.storage.type:local}") String storageType,
            @Value("${seahorse-agent.adapters.ai.type:unknown}") String aiType,
            @Value("${seahorse-agent.adapters.ai.embedding-type:unknown}") String embeddingType,
            @Value("${seahorse-agent.adapters.ai.embedding-dimension:}") String embeddingDimensionValue,
            @Value("${seahorse-agent.adapters.vector.dimension:}") String vectorDimensionValue,
            @Value("${seahorse-agent.adapters.mq.readiness-probe.enabled:${seahorse.agent.adapters.mq.readiness-probe.enabled:true}}")
            boolean mqReadinessProbeEnabled,
            @Value("${seahorse-agent.adapters.mq.readiness-probe.topic:${seahorse.agent.adapters.mq.readiness-probe.topic:persistent://seahorse-agent/ai/readiness-probe}}")
            String mqReadinessProbeTopic,
            Environment environment
    ) {
        String configuredVectorType = resolveSeahorseAgentProperty(environment,
                "seahorse-agent.adapters.vector.type", vectorType);
        String configuredCacheType = resolveSeahorseAgentProperty(environment,
                "seahorse-agent.adapters.cache.type", cacheType);
        String configuredMqType = resolveSeahorseAgentProperty(environment,
                "seahorse-agent.adapters.mq.type", mqType);
        String configuredStorageType = resolveSeahorseAgentProperty(environment,
                "seahorse-agent.adapters.storage.type", storageType);
        String configuredAiType = resolveSeahorseAgentProperty(environment,
                "seahorse-agent.adapters.ai.type", aiType);
        String configuredEmbeddingType = resolveSeahorseAgentProperty(environment,
                "seahorse-agent.adapters.ai.embedding-type", embeddingType);
        String configuredKeywordSearchType = resolveSeahorseAgentProperty(environment,
                "seahorse-agent.adapters.keyword-search.type", "jdbc");
        String configuredKeywordIndexType = resolveSeahorseAgentProperty(environment,
                "seahorse-agent.adapters.keyword-index.type", "jdbc");
        int embeddingDimension = resolveIntSeahorseAgentProperty(environment,
                "seahorse-agent.adapters.ai.embedding-dimension", embeddingDimensionValue, 0);
        int vectorDimension = resolveIntSeahorseAgentProperty(environment,
                "seahorse-agent.adapters.vector.dimension", vectorDimensionValue, 0);

        return new ReadinessProbePort() {
            private static final long MQ_PROBE_TTL_MILLIS = 30_000L;

            private volatile ComponentStatus lastMqProbe;
            private volatile long lastMqProbeAt;

            @Override
            public Map<String, ComponentStatus> probeComponents() {
                Map<String, ComponentStatus> map = new LinkedHashMap<>();

                DataSource ds = dataSource.getIfAvailable();
                map.put("database", ds != null
                        ? ComponentStatus.available("postgresql")
                        : ComponentStatus.unavailable("DataSource not available"));

                // db.migration — 核心表存在即视为迁移已应用（幂等升级器在启动时执行）
                map.put("db.migration", probeMigration(ds));

                // auth.default-admin — 是否存在至少一个用户（默认管理员已 seed）
                map.put("auth.default-admin", probeDefaultAdmin(ds));

                map.put("chat-model", chatModel.getIfAvailable() != null
                        ? ComponentStatus.available(configuredAiType)
                        : ComponentStatus.unavailable("StreamingChatModelPort not available"));

                // Embedding model availability — check if a separate embedding bean exists
                // In practice, the embedding model is often part of the AI adapter
                map.put("embedding-model", chatModel.getIfAvailable() != null
                        ? ComponentStatus.available(configuredEmbeddingType)
                        : ComponentStatus.unavailable("Embedding model not available"));

                // embedding.dimension — 配置维度与向量库维度一致性
                map.put("embedding.dimension", probeEmbeddingDimension(embeddingDimension, vectorDimension));

                map.put("vector-store", vectorSearch.getIfAvailable() != null
                        ? ComponentStatus.available(configuredVectorType)
                        : ComponentStatus.unavailable("VectorSearchPort not available"));

                map.put("keyword-search", keywordSearch.getIfAvailable() != null
                        ? ComponentStatus.available(configuredKeywordSearchType)
                        : ComponentStatus.unavailable("KeywordSearchPort not available"));

                map.put("cache", cache.getIfAvailable() != null
                        ? ComponentStatus.available(configuredCacheType)
                        : ComponentStatus.unavailable("KeyValueCachePort not available"));

                map.put("mq", probeMessageQueue(mq.getIfAvailable()));

                map.put("storage", storage.getIfAvailable() != null
                        ? ComponentStatus.available(configuredStorageType)
                        : ComponentStatus.unavailable("ObjectStoragePort not available"));

                return map;
            }

            private ComponentStatus probeMessageQueue(MessageQueuePort mqPort) {
                if (mqPort == null) {
                    return ComponentStatus.unavailable("MessageQueuePort not available");
                }
                if (!"pulsar".equalsIgnoreCase(configuredMqType) || !mqReadinessProbeEnabled) {
                    return ComponentStatus.available(configuredMqType);
                }
                long now = System.currentTimeMillis();
                ComponentStatus cached = lastMqProbe;
                if (cached != null && now - lastMqProbeAt < MQ_PROBE_TTL_MILLIS) {
                    return cached;
                }
                try {
                    String topic = hasText(mqReadinessProbeTopic)
                            ? mqReadinessProbeTopic.trim()
                            : "persistent://seahorse-agent/ai/readiness-probe";
                    mqPort.send(topic, "readiness-" + now, "readiness-probe",
                            Map.of("probe", "readiness", "timestamp", String.valueOf(now)));
                    return cacheMqProbe(ComponentStatus.available(configuredMqType, "probe sent to " + topic), now);
                } catch (Exception ex) {
                    return cacheMqProbe(ComponentStatus.unavailable(
                            "Pulsar readiness probe failed: " + rootMessage(ex)), now);
                }
            }

            private ComponentStatus cacheMqProbe(ComponentStatus status, long checkedAt) {
                lastMqProbe = status;
                lastMqProbeAt = checkedAt;
                return status;
            }

            private String rootMessage(Throwable throwable) {
                Throwable root = throwable;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                String message = root.getMessage();
                return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
            }

            private boolean hasText(String value) {
                return value != null && !value.isBlank();
            }

            private ComponentStatus probeMigration(DataSource ds) {
                if (ds == null) {
                    return ComponentStatus.unavailable("数据库不可用，无法校验迁移");
                }
                try (var conn = ds.getConnection()) {
                    var meta = conn.getMetaData();
                    // 检查若干核心表是否存在（大小写无关，覆盖 t_/sa_ 前缀）
                    for (String table : new String[]{"t_conversation", "t_user", "sa_task"}) {
                        boolean found = tableExists(meta, table);
                        if (!found) {
                            return ComponentStatus.unavailable("核心表缺失: " + table);
                        }
                    }
                    return ComponentStatus.available("applied", "核心表齐全");
                } catch (Exception e) {
                    return ComponentStatus.unavailable("迁移校验失败: " + e.getMessage());
                }
            }

            private boolean tableExists(java.sql.DatabaseMetaData meta, String table) throws java.sql.SQLException {
                for (String t : new String[]{table, table.toUpperCase(), table.toLowerCase()}) {
                    try (var rs = meta.getTables(null, null, t, new String[]{"TABLE"})) {
                        if (rs.next()) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private ComponentStatus probeDefaultAdmin(DataSource ds) {
                if (ds == null) {
                    return ComponentStatus.unavailable("数据库不可用");
                }
                try (var conn = ds.getConnection();
                     var st = conn.createStatement();
                     var rs = st.executeQuery("SELECT COUNT(*) FROM t_user")) {
                    if (rs.next() && rs.getLong(1) > 0) {
                        return ComponentStatus.available("seeded", rs.getLong(1) + " 个用户");
                    }
                    return ComponentStatus.unavailable("未发现任何用户账号");
                } catch (Exception e) {
                    return ComponentStatus.unavailable("用户表查询失败: " + e.getMessage());
                }
            }

            private ComponentStatus probeEmbeddingDimension(int embedDim, int vecDim) {
                if (embedDim <= 0 || vecDim <= 0) {
                    // 未显式配置维度 → 跳过（不阻断），交由检查项标记为 skipped
                    return new ComponentStatus(true, "unknown", "未配置显式维度，跳过一致性校验");
                }
                if (embedDim == vecDim) {
                    return ComponentStatus.available("consistent", "维度一致: " + embedDim);
                }
                return ComponentStatus.unavailable("维度不一致: embedding=" + embedDim + ", vector=" + vecDim);
            }

            @Override
            public Map<String, String> adapterTypes() {
                Map<String, String> types = new LinkedHashMap<>();
                types.put("vector-store", configuredVectorType);
                types.put("keyword-search", configuredKeywordSearchType);
                types.put("keyword-index", configuredKeywordIndexType);
                types.put("cache", configuredCacheType);
                types.put("mq", configuredMqType);
                types.put("storage", configuredStorageType);
                types.put("ai", configuredAiType);
                types.put("embedding", configuredEmbeddingType);
                types.put("embedding-dimension", String.valueOf(embeddingDimension));
                types.put("vector-dimension", String.valueOf(vectorDimension));
                return types;
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(ReadinessInboundPort.class)
    public ReadinessInboundPort seahorseReadinessService(
            ReadinessProbePort probePort,
            @Value("${seahorse-agent.product-mode:demo}") String productMode
    ) {
        return new KernelReadinessService(productMode, probePort);
    }

    @Bean
    @ConditionalOnMissingBean(ReadinessController.class)
    public ReadinessController seahorseReadinessController(
            ReadinessInboundPort readinessPort,
            AdvancedFeatureGate featureGate
    ) {
        return new ReadinessController(readinessPort, featureGate);
    }

    private static String resolveSeahorseAgentProperty(Environment environment, String canonicalName, String fallback) {
        String value = environment.getProperty(canonicalName);
        if (value == null || value.isBlank()) {
            value = environment.getProperty(canonicalName.replace("seahorse-agent.", "seahorse.agent."));
        }
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int resolveIntSeahorseAgentProperty(
            Environment environment,
            String canonicalName,
            String placeholderValue,
            int fallback) {
        String value = resolveSeahorseAgentProperty(environment, canonicalName, placeholderValue);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Integer.parseInt(value.trim());
    }
}
