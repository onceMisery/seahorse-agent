/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */
package com.miracle.ai.seahorse.agent.kernel.application.readiness;

import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessCheck;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessCheck.Severity;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessCheck.Status;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.readiness.ReadinessProbePort;
import com.miracle.ai.seahorse.agent.ports.outbound.readiness.ReadinessProbePort.ComponentStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 系统就绪检查服务。
 * <p>
 * 根据当前产品模式判断各项基础设施的可用状态，
 * 并为不可用项生成影响说明和修复建议。
 */
public class KernelReadinessService implements ReadinessInboundPort {

    private final String productMode;
    private final ReadinessProbePort probePort;

    public KernelReadinessService(String productMode, ReadinessProbePort probePort) {
        this.productMode = Objects.requireNonNull(productMode, "productMode");
        this.probePort = Objects.requireNonNull(probePort, "probePort");
    }

    @Override
    public ReadinessSummary getSummary() {
        List<ReadinessCheck> checks = runAllChecks();
        return ReadinessSummary.of(productMode, checks);
    }

    @Override
    public ReadinessCheck runCheck(String checkId) {
        return runAllChecks().stream()
                .filter(c -> c.id().equals(checkId))
                .findFirst()
                .orElse(null);
    }

    private List<ReadinessCheck> runAllChecks() {
        Map<String, ComponentStatus> components = probePort.probeComponents();
        Map<String, String> adapterTypes = probePort.adapterTypes();
        List<ReadinessCheck> checks = new ArrayList<>();

        checks.add(checkAppBoot());
        checks.add(checkDatabase(components));
        checks.add(checkChatModel(components));
        checks.add(checkEmbeddingModel(components));
        checks.add(checkVectorStore(components, adapterTypes));
        checks.add(checkKeywordSearch(components, adapterTypes));
        checks.add(checkCache(components, adapterTypes));
        checks.add(checkMessageQueue(components, adapterTypes));
        checks.add(checkStorage(components, adapterTypes));

        return checks;
    }

    private ReadinessCheck checkAppBoot() {
        return ReadinessCheck.passed("app.boot", "应用启动", Severity.ERROR,
                "应用已正常启动，运行模式: " + productMode);
    }

    private ReadinessCheck checkDatabase(Map<String, ComponentStatus> components) {
        ComponentStatus db = components.get("database");
        if (db != null && db.available()) {
            return ReadinessCheck.passed("db.connection", "数据库连接", Severity.ERROR,
                    "数据库连接正常");
        }
        return ReadinessCheck.failed("db.connection", "数据库连接", Severity.ERROR,
                "数据库连接不可用",
                "所有持久化功能将不可用",
                "检查 PostgreSQL 连接配置和网络可达性",
                "");
    }

    private ReadinessCheck checkChatModel(Map<String, ComponentStatus> components) {
        ComponentStatus chat = components.get("chat-model");
        if (chat != null && chat.available()) {
            return ReadinessCheck.passed("model.chat", "聊天模型", Severity.ERROR,
                    "聊天模型可用: " + chat.adapterType());
        }
        return ReadinessCheck.failed("model.chat", "聊天模型", Severity.ERROR,
                "聊天模型不可用",
                "无法进行对话和 Agent 任务",
                "检查 AI 模型配置（API Key、端点地址、模型名称）",
                "");
    }

    private ReadinessCheck checkEmbeddingModel(Map<String, ComponentStatus> components) {
        Severity severity = isDemo() ? Severity.WARN : Severity.ERROR;
        ComponentStatus embedding = components.get("embedding-model");
        if (embedding != null && embedding.available()) {
            return ReadinessCheck.passed("model.embedding", "Embedding 模型", severity,
                    "Embedding 模型可用: " + embedding.adapterType());
        }
        if (isDemo()) {
            return ReadinessCheck.failed("model.embedding", "Embedding 模型", Severity.WARN,
                    "Embedding 模型不可用",
                    "知识库检索和文档问答功能降级",
                    "配置 Embedding 模型以启用 RAG 能力",
                    "");
        }
        return ReadinessCheck.failed("model.embedding", "Embedding 模型", Severity.ERROR,
                "Embedding 模型不可用",
                "知识库检索和文档问答不可用",
                "检查 Embedding 模型配置（API Key、端点地址、模型名称）",
                "");
    }

    private ReadinessCheck checkVectorStore(Map<String, ComponentStatus> components,
                                            Map<String, String> adapterTypes) {
        Severity severity = isDemo() ? Severity.WARN : Severity.ERROR;
        ComponentStatus vector = components.get("vector-store");
        String type = adapterTypes.getOrDefault("vector-store", "unknown");
        if (vector != null && vector.available()) {
            return ReadinessCheck.passed("vector.store", "向量存储", severity,
                    "向量存储可用: " + type);
        }
        if ("noop".equals(type)) {
            return ReadinessCheck.failed("vector.store", "向量存储", Severity.WARN,
                    "向量存储使用 NoOp 适配器",
                    "语义检索不可用，将降级为关键词检索",
                    "配置 Milvus 或 PgVector 以启用语义检索",
                    "");
        }
        return ReadinessCheck.failed("vector.store", "向量存储", severity,
                "向量存储不可用",
                isDemo() ? "语义检索功能降级" : "知识库语义检索不可用",
                "检查向量存储配置和连接",
                "");
    }

    private ReadinessCheck checkKeywordSearch(Map<String, ComponentStatus> components,
                                              Map<String, String> adapterTypes) {
        Severity severity = isDemo() ? Severity.INFO : Severity.WARN;
        ComponentStatus keyword = components.get("keyword-search");
        String type = adapterTypes.getOrDefault("keyword-search", "unknown");
        if (keyword != null && keyword.available()) {
            return ReadinessCheck.passed("search.keyword", "关键词搜索", severity,
                    "关键词搜索可用: " + type);
        }
        return ReadinessCheck.failed("search.keyword", "关键词搜索", severity,
                "关键词搜索不可用",
                "关键词检索降级，可能影响检索精度",
                "配置 Lucene 或 Elasticsearch 以启用关键词搜索",
                "");
    }

    private ReadinessCheck checkCache(Map<String, ComponentStatus> components,
                                      Map<String, String> adapterTypes) {
        String type = adapterTypes.getOrDefault("cache", "unknown");
        ComponentStatus cache = components.get("cache");
        if (cache != null && cache.available()) {
            return ReadinessCheck.passed("cache", "缓存", Severity.INFO,
                    "缓存可用: " + type);
        }
        if (isEnterprise()) {
            return ReadinessCheck.failed("cache", "缓存", Severity.ERROR,
                    "缓存不可用",
                    "性能和会话管理受影响",
                    "配置 Redis 缓存",
                    "");
        }
        return ReadinessCheck.failed("cache", "缓存", Severity.INFO,
                "使用本地缓存（" + type + "）",
                "企业级缓存未启用",
                isDemo() ? "Demo 模式下本地缓存可用" : "配置 Redis 以启用分布式缓存",
                "");
    }

    private ReadinessCheck checkMessageQueue(Map<String, ComponentStatus> components,
                                             Map<String, String> adapterTypes) {
        String type = adapterTypes.getOrDefault("mq", "unknown");
        ComponentStatus mq = components.get("mq");
        if (mq != null && mq.available()) {
            return ReadinessCheck.passed("mq", "消息队列", Severity.INFO,
                    "消息队列可用: " + type);
        }
        if (isEnterprise()) {
            return ReadinessCheck.failed("mq", "消息队列", Severity.ERROR,
                    "消息队列不可用",
                    "异步任务处理不可用",
                    "配置 Pulsar 消息队列",
                    "");
        }
        return ReadinessCheck.passed("mq", "消息队列", Severity.INFO,
                "使用直接消息队列（" + type + "），企业级消息队列未启用");
    }

    private ReadinessCheck checkStorage(Map<String, ComponentStatus> components,
                                        Map<String, String> adapterTypes) {
        String type = adapterTypes.getOrDefault("storage", "unknown");
        ComponentStatus storage = components.get("storage");
        if (storage != null && storage.available()) {
            return ReadinessCheck.passed("storage", "对象存储", Severity.INFO,
                    "对象存储可用: " + type);
        }
        if (isEnterprise()) {
            return ReadinessCheck.failed("storage", "对象存储", Severity.WARN,
                    "对象存储不可用",
                    "文件上传和附件功能受限",
                    "配置 MinIO 或 S3 对象存储",
                    "");
        }
        return ReadinessCheck.passed("storage", "对象存储", Severity.INFO,
                "使用本地存储（" + type + "）");
    }

    private boolean isDemo() {
        return "DEMO".equalsIgnoreCase(productMode);
    }

    private boolean isEnterprise() {
        return "ENTERPRISE".equalsIgnoreCase(productMode);
    }
}
