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

package com.miracle.ai.seahorse.agent.kernel.plugin.wrapper;

/**
 * 端口包装器。
 *
 * @param <T> 被包装的端口类型
 */
public interface PortWrapper<T> {

    /**
     * 包装端口实例。
     *
     * @param delegate 原始端口
     * @return 包装后的端口
     */
    T wrap(T delegate);

    /**
     * 包装器名称。
     *
     * @return 名称
     */
    String name();

    /**
     * 包装器顺序，数值越小越靠近调用入口。
     *
     * @return 顺序
     */
    int order();

    /**
     * 包装器类型。
     *
     * @return 类型
     */
    default String type() {
        return name();
    }

    /**
     * 是否只是透传占位实现。
     *
     * @return true 表示未提供真实横切能力
     */
    default boolean passThrough() {
        return false;
    }
}
