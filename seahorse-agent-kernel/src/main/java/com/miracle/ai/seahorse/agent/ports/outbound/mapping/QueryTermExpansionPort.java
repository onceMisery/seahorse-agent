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

package com.miracle.ai.seahorse.agent.ports.outbound.mapping;

import java.util.List;
import java.util.Map;

/**
 * 术语扩展端口。
 *
 * <p>从用户查询中匹配已注册的术语映射，返回扩展结果。
 * 与 {@link QueryTermMappingRepositoryPort}（管理分页）不同，该端口面向在线查询。
 */
public interface QueryTermExpansionPort {

    /**
     * 对查询文本进行术语匹配和扩展。
     *
     * @param queryText 查询文本
     * @return 匹配到的术语映射（key=源词, value=目标词列表）
     */
    Map<String, List<String>> expand(String queryText);

    static QueryTermExpansionPort noop() {
        return queryText -> Map.of();
    }
}
