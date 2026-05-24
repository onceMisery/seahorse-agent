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

import java.util.Objects;

/**
 * Slice 3 续 cut 5：profile slot 解析与 slot value 规整。
 *
 * <p>原 facade 私有方法 {@code profileSlot} / {@code profileValue} / {@code stripPrefix} /
 * {@code stripProfilePrefix} / {@code normalizeOccupationValue} 全部迁入本 service：
 *
 * <ul>
 *     <li>{@link #resolveSlot(MemoryCaptureDecision, MemoryClassificationResult)} 复用 refined
 *         delta 提供的 {@code targetKey}（kind == {@code PROFILE_SLOT}），否则回退 {@link ProfileSlotResolver}。</li>
 *     <li>{@link #normalize(String, String)} 按 slotKey 剥离中英文前缀，并对 occupation 委托给
 *         {@link OccupationCorrection#normalizeOccupationValue(String)} 复用既有规则。</li>
 * </ul>
 *
 * <p>无任何 outbound port 依赖；仅组合 {@link ProfileSlotResolver} 一个 helper。
 */
public final class MemoryProfileValueNormalizer {

    private static final String TARGET_KIND_PROFILE_SLOT = "PROFILE_SLOT";

    private final ProfileSlotResolver profileSlotResolver;

    public MemoryProfileValueNormalizer(ProfileSlotResolver profileSlotResolver) {
        this.profileSlotResolver = Objects.requireNonNull(profileSlotResolver,
                "profileSlotResolver must not be null");
    }

    /**
     * 解析 capture decision 对应的 profile slot。
     *
     * <p>优先采用 refined delta 中的 {@code targetKey}（当 {@code targetKind} 为
     * {@code PROFILE_SLOT} 时）；否则回退到 {@link ProfileSlotResolver#resolve(String, String, String)}
     * 按 type + content 启发式判定。
     */
    public String resolveSlot(MemoryCaptureDecision decision, MemoryClassificationResult classification) {
        RefinedMemoryDelta delta = classification == null ? null : classification.refinedDelta();
        if (delta != null
                && TARGET_KIND_PROFILE_SLOT.equalsIgnoreCase(delta.targetKind())
                && !isBlank(delta.targetKey())) {
            return delta.targetKey();
        }
        return decision == null ? "" : profileSlotResolver.resolve(decision.type(), decision.content(), "");
    }

    /**
     * 按 slot 规整 value。
     *
     * <ul>
     *     <li>{@code identity.name}：剥离 "my name is" / "我叫" / "我的名字是" / "我的昵称是"。</li>
     *     <li>{@code skills.tech_stack}：剥离 "my tech stack is" / "我的技术栈是" / "我主要使用"。</li>
     *     <li>{@code preferences.response_style}：剥离 "i prefer" / "i like" / "我喜欢" / "我偏好"。</li>
     *     <li>{@code identity.occupation}：先剥 occupation 前缀，再走 occupation 规则化。</li>
     * </ul>
     *
     * @return 规整后的 value；未识别 slotKey 返回空串
     */
    public String normalize(String slotKey, String content) {
        String value = Objects.requireNonNullElse(content, "").trim();
        if ("identity.name".equals(slotKey)) {
            value = stripPrefix(value, "(?i)^my name is\\s+");
            value = stripPrefix(value, "^我叫");
            value = stripPrefix(value, "^我的名字是");
            return stripPrefix(value, "^我的昵称是");
        }
        if ("skills.tech_stack".equals(slotKey)) {
            value = stripPrefix(value, "(?i)^my tech stack is\\s+");
            value = stripPrefix(value, "^我的技术栈是\\s*");
            return stripPrefix(value, "^我主要使用\\s*");
        }
        if ("preferences.response_style".equals(slotKey)) {
            value = stripPrefix(value, "(?i)^i prefer\\s+");
            value = stripPrefix(value, "(?i)^i like\\s+");
            value = stripPrefix(value, "^我喜欢");
            value = stripPrefix(value, "^我偏好");
            return value;
        }
        if ("identity.occupation".equals(slotKey)) {
            return OccupationCorrection.normalizeOccupationValue(stripProfilePrefix(value));
        }
        return "";
    }

    private static String stripPrefix(String content, String regex) {
        return Objects.requireNonNullElse(content, "").replaceFirst(regex, "").trim();
    }

    private static String stripProfilePrefix(String content) {
        return Objects.requireNonNullElse(content, "")
                .trim()
                .replaceFirst("^我的(职业|身份|工作|岗位|角色)是", "")
                .replaceFirst("^我是", "")
                .replaceFirst("^我是一名", "")
                .replaceFirst("^我是一位", "")
                .replaceFirst("^一名", "")
                .replaceFirst("^一位", "")
                .trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
