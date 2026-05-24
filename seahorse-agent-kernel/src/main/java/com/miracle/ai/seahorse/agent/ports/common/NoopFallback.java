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

package com.miracle.ai.seahorse.agent.ports.common;

/**
 * 显式 noop fallback 标记接口。
 *
 * <p>Spec §7（生产 noop 风险治理）要求 starter 能在启动期识别哪些关键端口当前由 noop 实现兜底，
 * 并按生产策略分类降级。把现有 {@code static noop()} 工厂返回的实例打上本标记后，starter 即可通过
 * {@code instanceof NoopFallback} 稳定识别它们，从而：
 *
 * <ul>
 *     <li>A 类（写入/审计/索引等关键链路）— 在生产强制 fail-fast，避免静默丢数据。</li>
 *     <li>B 类（向量/关键字索引/观测增强）— 记录 WARN + metric，但不阻塞启动。</li>
 *     <li>C 类（refiner/summarizer/graph 等纯增强）— 允许保留 noop。</li>
 * </ul>
 *
 * <p>注意事项：
 * <ul>
 *     <li>{@code NoopFallback} 自身不承诺端口行为；它仅是元数据标记。</li>
 *     <li>由 lambda 表达式产生的 noop 不能直接实现接口，相关端口需要改写为命名内部类。</li>
 *     <li>本接口属于 kernel 层；adapter 实现侧不直接依赖。</li>
 * </ul>
 */
public interface NoopFallback {
}
