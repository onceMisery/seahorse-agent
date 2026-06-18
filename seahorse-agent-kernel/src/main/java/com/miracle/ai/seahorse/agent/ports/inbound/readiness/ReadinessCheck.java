/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.
 */
package com.miracle.ai.seahorse.agent.ports.inbound.readiness;

/**
 * 系统就绪检查项。
 *
 * @param id         检查项唯一标识，例如 "model.chat"
 * @param name       检查项名称，例如 "聊天模型可用性"
 * @param severity   严重程度：error（阻断）、warn（降级）、info（提示）
 * @param status     检查状态：passed、failed、skipped
 * @param message    状态描述
 * @param impact     失败时的影响说明
 * @param suggestion 修复建议
 * @param docsUrl    相关文档链接
 */
public record ReadinessCheck(
        String id,
        String name,
        Severity severity,
        Status status,
        String message,
        String impact,
        String suggestion,
        String docsUrl
) {

    public enum Severity { ERROR, WARN, INFO }

    public enum Status { PASSED, FAILED, SKIPPED }

    public static ReadinessCheck passed(String id, String name, Severity severity, String message) {
        return new ReadinessCheck(id, name, severity, Status.PASSED, message, "", "", "");
    }

    public static ReadinessCheck failed(String id, String name, Severity severity,
                                        String message, String impact, String suggestion, String docsUrl) {
        return new ReadinessCheck(id, name, severity, Status.FAILED, message, impact, suggestion, docsUrl);
    }

    public static ReadinessCheck skipped(String id, String name, String message) {
        return new ReadinessCheck(id, name, Severity.INFO, Status.SKIPPED, message, "", "", "");
    }
}
