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

import java.util.List;

/**
 * 扩展注册表。
 * <p>
 * 注册表是微内核可插拔架构的启动期索引，L1 内核只通过端口类型获取扩展链，
 * 不依赖 Spring Bean 名称、具体实现类或外部技术 SDK。
 */
public interface ExtensionRegistry {

    /**
     * 获取默认扩展。
     *
     * @param portType 端口类型
     * @param <T>      端口泛型
     * @return 默认扩展实例
     */
    <T> T getDefaultExtension(Class<T> portType);

    /**
     * 获取当前上下文中启用的扩展链。
     *
     * @param portType 端口类型
     * @param context  激活上下文
     * @param <T>      端口泛型
     * @return 已按描述符顺序排序的扩展链
     */
    <T> List<T> getActivatedExtensions(Class<T> portType, FeatureActivationContext context);

    /**
     * 查询启动期已注册扩展快照。
     *
     * @return 已注册扩展快照
     */
    default List<ExtensionRegistration> registeredExtensions() {
        return List.of();
    }

    /**
     * 注册扩展实例。
     *
     * @param descriptor 扩展描述符
     * @param instance   扩展实例
     */
    void register(ExtensionDescriptor descriptor, Object instance);

    static ExtensionRegistry empty() {
        return new ExtensionRegistry() {
            @Override
            public <T> T getDefaultExtension(Class<T> portType) {
                throw new IllegalStateException("no extension registry available");
            }

            @Override
            public <T> List<T> getActivatedExtensions(Class<T> portType, FeatureActivationContext context) {
                return List.of();
            }

            @Override
            public void register(ExtensionDescriptor descriptor, Object instance) {
            }
        };
    }
}
