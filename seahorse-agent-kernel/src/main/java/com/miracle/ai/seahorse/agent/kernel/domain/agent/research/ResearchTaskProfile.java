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

import java.util.List;

/**
 * 研究任务配置档案，定义搜索深度、来源限制和输出类型。
 */
public record ResearchTaskProfile(
    String profileId,
    String name,
    int defaultDepth,
    int maxSearchQueries,
    int maxSources,
    List<String> allowedSourceTypes,
    String outputArtifactType,
    String costLimitPolicyId
) {

    public static ResearchTaskProfile defaultProfile() {
        return new ResearchTaskProfile(
                "default-research",
                "默认研究",
                2,
                5,
                10,
                List.of("WEB_SEARCH", "WEB_CRAWL"),
                "MARKDOWN_REPORT",
                null);
    }
}
