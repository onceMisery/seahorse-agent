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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.learning;

import java.util.Locale;
import java.util.Objects;

/**
 * 一次回访所依据的评测测量结果。
 *
 * <p>闭环本身<b>不负责采集</b>这两个数：内核没有评测数据源的端口，硬塞一个进来只会让
 * 端口表面积继续膨胀。这里把它们做成调用方的入参——谁触发回访（人手点、定时任务、
 * 或接入了评测系统的适配层），谁负责给出可比的一对通过率。
 *
 * <p>两个值缺任意一个就 {@link #measurable()} 为 false，此时回访<b>不落库</b>。
 * 理由见 {@link com.miracle.ai.seahorse.agent.kernel.application.learning.KernelPolicyAdjustmentTicketService}：
 * 不可判定的回访每轮都会产生一条一模一样的记录，既没有信息量又会吃掉冷却期。
 *
 * @param baselinePassRate 该工单落地前的评测通过率
 * @param currentPassRate  当前评测通过率
 * @param source           数据来源说明，写进回访记录便于追溯
 */
public record ReviewMeasurement(Double baselinePassRate, Double currentPassRate, String source) {

    public ReviewMeasurement {
        baselinePassRate = requireRatio(baselinePassRate, "baselinePassRate");
        currentPassRate = requireRatio(currentPassRate, "currentPassRate");
        source = Objects.requireNonNullElse(source, "").trim();
    }

    public static ReviewMeasurement unavailable() {
        return new ReviewMeasurement(null, null, "unavailable");
    }

    public static ReviewMeasurement of(double baselinePassRate, double currentPassRate, String source) {
        return new ReviewMeasurement(baselinePassRate, currentPassRate, source);
    }

    public boolean measurable() {
        return baselinePassRate != null && currentPassRate != null;
    }

    /** 通过率变化量，不可测量时返回 {@code null}。 */
    public Double delta() {
        return measurable() ? currentPassRate - baselinePassRate : null;
    }

    private static Double requireRatio(Double value, String name) {
        if (value == null) {
            return null;
        }
        if (value.isNaN() || value.isInfinite() || value < 0D || value > 1D) {
            throw new IllegalArgumentException(
                    String.format(Locale.ROOT, "%s 必须落在 [0,1]，实际为 %s", name, value));
        }
        return value;
    }
}
