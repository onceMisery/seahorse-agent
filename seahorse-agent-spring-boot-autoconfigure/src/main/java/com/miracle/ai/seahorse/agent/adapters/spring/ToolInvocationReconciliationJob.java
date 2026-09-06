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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ToolInvocationReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * UNKNOWN 工具调用对账定时任务。
 *
 * <p>每 5 分钟扫描一次超过宽限期的 UNKNOWN 审计记录，把不确定的副作用结局
 * 对账到真实终态。集群环境下通过分布式锁保证只有一个实例执行。
 */
public class ToolInvocationReconciliationJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolInvocationReconciliationJob.class);

    private final ToolInvocationReconciliationService reconciliationService;

    public ToolInvocationReconciliationJob(ToolInvocationReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void reconcile() {
        try {
            ToolInvocationReconciliationService.ToolInvocationReconciliationResult result =
                    reconciliationService.reconcile();
            if (result.total() > 0) {
                LOGGER.info("Tool invocation reconciliation converged {} records ({} from sibling, {} as failed)",
                        result.total(), result.reconciledFromSibling(), result.resolvedAsFailed());
            }
        } catch (Exception e) {
            LOGGER.error("Tool invocation reconciliation job failed unexpectedly", e);
        }
    }
}
