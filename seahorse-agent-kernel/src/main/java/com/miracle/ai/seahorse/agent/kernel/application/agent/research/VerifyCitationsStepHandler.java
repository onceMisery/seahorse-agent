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

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchStepType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DurableTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VERIFY_CITATIONS 步骤：验证报告中的引用标号是否都有对应证据支撑。
 */
public class VerifyCitationsStepHandler implements ResearchStepHandler {

    private static final Logger log = LoggerFactory.getLogger(VerifyCitationsStepHandler.class);

    private final CitationVerifier verifier;

    public VerifyCitationsStepHandler(CitationVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public ResearchStepType stepType() {
        return ResearchStepType.VERIFY_CITATIONS;
    }

    @Override
    public void execute(DurableTask task, ResearchStepContext context) {
        String report = context.reportContent();
        if (report == null || report.isBlank()) {
            log.warn("No report content to verify for run={}", task.runId());
            return;
        }

        CitationVerifier.VerificationResult result = verifier.verify(report, context.evidence());

        if (!result.isFullyVerified()) {
            log.info("Citation verification for run={}: verified={}, missing={}, unreferenced={}",
                    task.runId(), result.verified().size(), result.missing().size(), result.unreferenced().size());
        }
    }
}
