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

package com.miracle.ai.seahorse.agent.kernel.plugin;

/**
 * 微内核 Feature 基础接口。
 * <p>
 * L2 Feature 只表达业务扩展能力，不直接暴露 Milvus、Redis、S3、OkHttp 等具体 SDK。
 * 内核通过该接口完成统一识别、启停判断、排序和健康检查，避免主干逻辑被拆成不可治理的散装插件。
 */
public interface AgentFeature {

    /**
     * Feature 唯一名称。
     * <p>
     * 名称用于配置开关、日志、监控和扩展冲突检测，同一端口下不允许重复。
     *
     * @return Feature 名称
     */
    String name();

    /**
     * Feature 类型。
     * <p>
     * 类型用于区分检索通道、后处理器、MCP 工具、记忆治理、模型路由等不同扩展点。
     *
     * @return Feature 类型
     */
    FeatureType type();

    /**
     * 判断当前上下文下是否启用。
     * <p>
     * 默认启用，具体 Feature 可以基于租户、用户、灰度属性或配置做细粒度控制。
     *
     * @param context Feature 激活上下文
     * @return true 表示启用，false 表示跳过
     */
    default boolean enabled(FeatureActivationContext context) {
        return true;
    }

    /**
     * 默认排序。
     * <p>
     * 注册表优先使用 {@link ExtensionDescriptor#order()}，该方法作为没有描述符时的补充语义。
     *
     * @return 数字越小优先级越高
     */
    default int order() {
        return 0;
    }

    /**
     * Feature 健康状态。
     * <p>
     * 健康检查只报告自身状态，不在请求链路中主动访问外部服务，避免增加主链路耗时。
     *
     * @return 健康状态
     */
    default FeatureHealth health() {
        return FeatureHealth.up(name());
    }
}
