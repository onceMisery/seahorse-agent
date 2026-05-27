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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.research;

/**
 * 研究任务固定步骤类型。
 *
 * <p>步骤按顺序执行：PLAN → SEARCH → FETCH → EXTRACT_EVIDENCE → SYNTHESIZE → WRITE_REPORT → VERIFY_CITATIONS。
 */
public enum ResearchStepType {

    PLAN,
    SEARCH,
    FETCH,
    EXTRACT_EVIDENCE,
    SYNTHESIZE,
    WRITE_REPORT,
    VERIFY_CITATIONS;

    /**
     * 返回当前步骤的下一步，如果已是最后一步则返回 null。
     */
    public ResearchStepType next() {
        return switch (this) {
            case PLAN -> SEARCH;
            case SEARCH -> FETCH;
            case FETCH -> EXTRACT_EVIDENCE;
            case EXTRACT_EVIDENCE -> SYNTHESIZE;
            case SYNTHESIZE -> WRITE_REPORT;
            case WRITE_REPORT -> VERIFY_CITATIONS;
            case VERIFY_CITATIONS -> null;
        };
    }
}
