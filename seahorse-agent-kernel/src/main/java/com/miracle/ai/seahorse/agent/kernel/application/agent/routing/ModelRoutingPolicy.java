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

package com.miracle.ai.seahorse.agent.kernel.application.agent.routing;

/**
 * 模型路由策略。
 *
 * <p>根据任务模板、用户额度和上下文长度选择合适的模型档位。
 * 当高档模型不可用时自动降级并记录原因。
 */
public class ModelRoutingPolicy {

    private static final int LARGE_CONTEXT_THRESHOLD = 32_000;
    private static final int VERY_LARGE_CONTEXT_THRESHOLD = 100_000;
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String LARGE_CONTEXT_MODEL = "gpt-4o";
    private static final String HIGH_QUALITY_MODEL = "gpt-4o";
    private static final String VERY_LARGE_CONTEXT_MODEL = "gpt-4o";
    private static final double HIGH_COST_THRESHOLD = 0.5;

    /**
     * 选择模型。
     *
     * @param templateModelTier 模板绑定的默认模型档位（HIGH/MEDIUM/LOW）
     * @param remainingQuota    用户剩余额度（美元）
     * @param contextTokens     当前上下文 token 数
     * @return 模型选择结果
     */
    public ModelSelection selectModel(String templateModelTier, double remainingQuota, int contextTokens) {
        // 超大上下文强制使用大窗口模型
        if (contextTokens > VERY_LARGE_CONTEXT_THRESHOLD) {
            if (remainingQuota < HIGH_COST_THRESHOLD) {
                return new ModelSelection(DEFAULT_MODEL, "上下文过长但额度不足，已降级");
            }
            return new ModelSelection(VERY_LARGE_CONTEXT_MODEL, null);
        }

        // 大上下文优先使用大窗口模型
        if (contextTokens > LARGE_CONTEXT_THRESHOLD) {
            return new ModelSelection(LARGE_CONTEXT_MODEL, null);
        }

        // 高档模板需要额度支撑
        if ("HIGH".equalsIgnoreCase(templateModelTier)) {
            if (remainingQuota <= 0) {
                return new ModelSelection(DEFAULT_MODEL, "额度不足，已降级到基础模型");
            }
            return new ModelSelection(HIGH_QUALITY_MODEL, null);
        }

        // MEDIUM 档位使用默认模型
        if ("MEDIUM".equalsIgnoreCase(templateModelTier)) {
            return new ModelSelection(DEFAULT_MODEL, null);
        }

        // LOW 档位或未指定
        return new ModelSelection(DEFAULT_MODEL, null);
    }

    /**
     * 模型选择结果。
     */
    public record ModelSelection(String modelId, String downgradeReason) {

        public boolean isDowngraded() {
            return downgradeReason != null;
        }
    }
}
