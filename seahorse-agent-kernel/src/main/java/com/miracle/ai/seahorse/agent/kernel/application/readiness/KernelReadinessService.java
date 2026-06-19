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
        checks.add(checkMigration(components));
        checks.add(checkDefaultAdmin(components));
        checks.add(checkChatModel(components));
        checks.add(checkEmbeddingModel(components));
        checks.add(checkEmbeddingDimension(components));
        checks.add(checkVectorStore(components, adapterTypes));
        checks.add(checkKeywordSearch(components, adapterTypes));
        checks.add(checkCache(components, adapterTypes));
        checks.add(checkMessageQueue(components, adapterTypes));
        checks.add(checkStorage(components, adapterTypes));
        checks.add(checkFeatureFlags());

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
                "/docs/zh/content/部署配置/部署配置.md");
    }

    private ReadinessCheck checkMigration(Map<String, ComponentStatus> components) {
        ComponentStatus mig = components.get("db.migration");
        if (mig != null && mig.available()) {
            return ReadinessCheck.passed("db.migration", "数据库迁移版本", Severity.ERROR,
                    "数据库迁移已应用（" + safeDetail(mig) + "）");
        }
        return ReadinessCheck.failed("db.migration", "数据库迁移版本", Severity.ERROR,
                mig != null ? mig.detail() : "迁移状态未知",
                "缺少核心表，应用核心功能将报错或数据不一致",
                "执行 resources/database/seahorse_init.sql 或确认幂等迁移升级器已在启动时运行",
                "/docs/zh/content/部署配置/生产环境部署.md");
    }

    private ReadinessCheck checkDefaultAdmin(Map<String, ComponentStatus> components) {
        ComponentStatus admin = components.get("auth.default-admin");
        if (admin != null && admin.available()) {
            return ReadinessCheck.passed("auth.default-admin", "默认管理员/登录状态", Severity.ERROR,
                    "用户账号已就绪（" + safeDetail(admin) + "）");
        }
        return ReadinessCheck.failed("auth.default-admin", "默认管理员/登录状态", Severity.ERROR,
                admin != null ? admin.detail() : "未发现用户账号",
                "无法登录系统，所有需要认证的功能不可用",
                "首次启动应自动创建默认管理员；若缺失，检查注册服务配置或手动创建管理员账号",
                "/docs/zh/content/快速开始.md");
    }

    private ReadinessCheck checkEmbeddingDimension(Map<String, ComponentStatus> components) {
        Severity severity = isDemo() ? Severity.WARN : Severity.ERROR;
        ComponentStatus dim = components.get("embedding.dimension");
        // 未配置显式维度：available=true 且 adapterType=unknown → skipped
        if (dim != null && dim.available() && "unknown".equals(dim.adapterType())) {
            return ReadinessCheck.skipped("embedding.dimension", "Embedding 维度一致性",
                    "未配置显式 embedding/向量库维度，跳过一致性校验");
        }
        if (dim != null && dim.available()) {
            return ReadinessCheck.passed("embedding.dimension", "Embedding 维度一致性", severity,
                    "Embedding 与向量库维度一致（" + safeDetail(dim) + "）");
        }
        return ReadinessCheck.failed("embedding.dimension", "Embedding 维度一致性", severity,
                dim != null ? dim.detail() : "维度状态未知",
                "知识库检索不可用，文档问答会降级或失败",
                "确认 embedding 模型输出维度与向量库集合维度一致，必要时重建向量集合或切换匹配模型",
                "/docs/zh/content/部署配置/开发环境搭建.md");
    }

    private ReadinessCheck checkFeatureFlags() {
        boolean known = isDemo() || isRag() || isEnterprise();
        if (known) {
            return ReadinessCheck.passed("feature.flags", "前后端能力开关一致性", Severity.WARN,
                    "产品模式有效: " + productMode + "，能力开关由后端统一生成");
        }
        return ReadinessCheck.failed("feature.flags", "前后端能力开关一致性", Severity.WARN,
                "未识别的产品模式: " + productMode,
                "前端可能显示与后端实际能力不一致的入口",
                "将 seahorse-agent.product-mode 设置为 demo / rag / enterprise 之一",
                "/docs/zh/content/项目概述.md");
    }

    private static String safeDetail(ComponentStatus s) {
        return s.detail() == null || s.detail().isBlank() ? s.adapterType() : s.detail();
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
        String unavailableMessage = mq != null && mq.detail() != null && !mq.detail().isBlank()
                ? mq.detail()
                : "消息队列不可用";
        if (isEnterprise()) {
            return ReadinessCheck.failed("mq", "消息队列", Severity.ERROR,
                    unavailableMessage,
                    "异步任务处理不可用",
                    "配置 Pulsar 消息队列",
                    "");
        }
        if (!"direct".equalsIgnoreCase(type) && !"unknown".equalsIgnoreCase(type)) {
            return ReadinessCheck.failed("mq", "消息队列", Severity.WARN,
                    unavailableMessage,
                    "已配置的消息队列不可用",
                    "检查 " + type + " 消息队列配置",
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

    private boolean isRag() {
        return "RAG".equalsIgnoreCase(productMode);
    }

    private boolean isEnterprise() {
        return "ENTERPRISE".equalsIgnoreCase(productMode);
    }
}
