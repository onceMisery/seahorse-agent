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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Slice 3 续：将 profile / correction 横向轨写入逻辑从 {@code DefaultMemoryEnginePort} 拆出。
 *
 * <p>职责范围（spec §8.2 MemoryTrackWriteService）：
 * <ul>
 *     <li>OccupationCorrection 落入 correction ledger + profile 更新（{@link #writeOccupationCorrection}）。</li>
 *     <li>显式 profile fact 入库（{@link #writeProfileFact}）。</li>
 *     <li>profile slot 旧片段失效标记（{@link #markProfileSlotFragmentsObsolete}）。</li>
 * </ul>
 *
 * <p>输入约定：所有 slotKey / value 在调用前已由 facade 完成规整（{@code profileValue} /
 * {@code normalizeOccupationValue}），service 不再二次清洗，保持职责单一。
 *
 * <p>operations 字符串与历史 ingestion 结果保持完全兼容，外部不应改名：
 * <ul>
 *     <li>{@link #OPERATION_CORRECTION_UPSERT} — correction ledger 写入。</li>
 *     <li>{@link #OPERATION_PROFILE_UPSERT} — profile fact 写入。</li>
 * </ul>
 */
public final class MemoryTrackWriteService {

    public static final String OPERATION_CORRECTION_UPSERT = "CORRECTION_UPSERT";
    public static final String OPERATION_PROFILE_UPSERT = "PROFILE_UPSERT";

    private static final Logger LOG = LoggerFactory.getLogger(MemoryTrackWriteService.class);

    private static final String CORRECTION_KIND_PROFILE = "PROFILE_CORRECTION";
    private static final String OCCUPATION_REASON_EXPLICIT_CORRECTION = "explicit_user_correction";
    private static final String OCCUPATION_REASON_EXPLICIT_MEMORY = "explicit_user_memory";
    private static final String OCCUPATION_LIFECYCLE_REASON = "profile slot updated";
    private static final double OCCUPATION_PROFILE_CONFIDENCE = 0.95D;

    private final ProfileMemoryPort profileMemoryPort;
    private final CorrectionLedgerPort correctionLedgerPort;
    private final MemoryLifecyclePort memoryLifecyclePort;
    private final String profileSlotKind;
    private final String identityOccupationSlotKey;

    public MemoryTrackWriteService(ProfileMemoryPort profileMemoryPort,
                                   CorrectionLedgerPort correctionLedgerPort,
                                   MemoryLifecyclePort memoryLifecyclePort,
                                   String profileSlotKind,
                                   String identityOccupationSlotKey) {
        this.profileMemoryPort = Objects.requireNonNull(profileMemoryPort, "profileMemoryPort must not be null");
        this.correctionLedgerPort = Objects.requireNonNull(correctionLedgerPort,
                "correctionLedgerPort must not be null");
        this.memoryLifecyclePort = Objects.requireNonNull(memoryLifecyclePort,
                "memoryLifecyclePort must not be null");
        this.profileSlotKind = Objects.requireNonNull(profileSlotKind, "profileSlotKind must not be null");
        this.identityOccupationSlotKey = Objects.requireNonNull(identityOccupationSlotKey,
                "identityOccupationSlotKey must not be null");
    }

    /**
     * 写入 occupation correction：correction ledger + profile fact + lifecycle obsolete。
     *
     * <p>本路径用于显式纠错场景（例："不对，我是医生，不是工程师"）。
     *
     * <p><b>失败策略</b>：
     * <ul>
     *     <li>第 1 步（correctionLedgerPort.upsert）失败 → 抛出 {@link MemoryTrackWriteException}。</li>
     *     <li>第 2 步（profileMemoryPort.upsert）失败 → 抛出 {@link MemoryTrackWriteException}。</li>
     *     <li>第 3 步（markObsolete）失败 → 静默记录，不影响整体成功判定。</li>
     * </ul>
     *
     * @return 包含每步成功/失败状态的 {@link MemoryTrackWriteResult}。
     * @throws MemoryTrackWriteException 当第 1 步或第 2 步失败时。
     */
    public MemoryTrackWriteResult writeOccupationCorrection(String userId,
                                                            String tenantId,
                                                            String messageId,
                                                            String incorrectValue,
                                                            String correctValue) {
        String generationId = "identity.occupation:" + SnowflakeIds.nextIdString();
        List<String> sourceIds = isBlank(messageId) ? List.of() : List.of(messageId);
        // Step 1: Correction Ledger upsert
        try {
            correctionLedgerPort.upsert(new CorrectionCommand(
                    userId,
                    tenantId,
                    CORRECTION_KIND_PROFILE,
                    profileSlotKind,
                    identityOccupationSlotKey,
                    incorrectValue,
                    correctValue,
                    "用户纠正职业画像：" + incorrectValue + " -> " + correctValue,
                    sourceIds,
                    generationId));
        } catch (RuntimeException ex) {
            throw new MemoryTrackWriteException("correction_upsert",
                    "Correction Ledger upsert failed for userId=" + userId, ex);
        }
        // Step 2: Profile fact upsert
        try {
            profileMemoryPort.upsert(new ProfileFactUpdate(
                    userId,
                    tenantId,
                    identityOccupationSlotKey,
                    correctValue,
                    OCCUPATION_PROFILE_CONFIDENCE,
                    OCCUPATION_REASON_EXPLICIT_CORRECTION,
                    sourceIds,
                    generationId));
        } catch (RuntimeException ex) {
            throw new MemoryTrackWriteException("profile_upsert",
                    "Profile upsert failed for userId=" + userId, ex);
        }
        // Step 3: Mark obsolete (best-effort)
        boolean obsoleteMarked = markProfileSlotFragmentsObsoleteWithStatus(
                userId, tenantId, identityOccupationSlotKey, generationId);
        return new MemoryTrackWriteResult(
                true,
                true,
                obsoleteMarked,
                List.of(OPERATION_CORRECTION_UPSERT, OPERATION_PROFILE_UPSERT));
    }

    /**
     * 写入显式 profile fact：profile port + lifecycle obsolete。
     *
     * <p>入参 {@code value} 已由 facade 完成 slot 特定规整；空 slotKey / 空 value 直接返回 false。
     *
     * <p><b>失败策略</b>：
     * <ul>
     *     <li>Profile upsert 失败 → 抛出 {@link MemoryTrackWriteException}。</li>
     *     <li>markObsolete 失败 → 静默记录（尽力而为）。</li>
     * </ul>
     *
     * @return true 表示 profile port 写入成功；false 表示 slotKey/value 缺失。
     * @throws MemoryTrackWriteException 当 profile upsert 失败时。
     */
    public boolean writeProfileFact(String userId,
                                    String tenantId,
                                    String slotKey,
                                    String value,
                                    double confidenceLevel,
                                    String messageId,
                                    String generationId) {
        if (isBlank(slotKey) || isBlank(value)) {
            return false;
        }
        try {
            profileMemoryPort.upsert(new ProfileFactUpdate(
                    userId,
                    tenantId,
                    slotKey,
                    value,
                    confidenceLevel,
                    OCCUPATION_REASON_EXPLICIT_MEMORY,
                    isBlank(messageId) ? List.of() : List.of(messageId),
                    generationId));
        } catch (RuntimeException ex) {
            throw new MemoryTrackWriteException("profile_upsert",
                    "Profile upsert failed for userId=" + userId + ", slot=" + slotKey, ex);
        }
        markProfileSlotFragmentsObsolete(userId, tenantId, slotKey, generationId);
        return true;
    }

    /**
     * 标记 profile slot 已存在的旧片段为 obsolete（保留历史日志原文，含 "记忆生命周期标记失败"
     * 等中文日志）。失败被静默 swallow，避免阻塞主写入路径。
     */
    public void markProfileSlotFragmentsObsolete(String userId,
                                                 String tenantId,
                                                 String profileSlot,
                                                 String activeGenerationId) {
        markProfileSlotFragmentsObsoleteWithStatus(userId, tenantId, profileSlot, activeGenerationId);
    }

    /**
     * 同 {@link #markProfileSlotFragmentsObsolete}，但返回是否成功。
     */
    private boolean markProfileSlotFragmentsObsoleteWithStatus(String userId,
                                                               String tenantId,
                                                               String profileSlot,
                                                               String activeGenerationId) {
        try {
            memoryLifecyclePort.markObsoleteByProfileSlot(
                    userId,
                    tenantId,
                    profileSlot,
                    activeGenerationId,
                    OCCUPATION_LIFECYCLE_REASON);
            return true;
        } catch (RuntimeException ex) {
            LOG.warn("Profile slot 旧片段失效失败: userId={}, slot={}", userId, profileSlot, ex);
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
