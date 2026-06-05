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

package com.miracle.ai.seahorse.agent.ports.outbound.retrieval;

import java.util.List;

/**
 * Outbound port for query rewriting in the Advanced RAG pipeline.
 *
 * <p>Implementations may use LLM-based or rule-based strategies to expand
 * or reformulate the original query into one or more alternative queries
 * that improve retrieval recall.
 */
public interface QueryRewritePort {

    /**
     * Rewrite the original query into zero or more alternative queries.
     *
     * @param originalQuery the user-supplied query text
     * @return a list of rewritten queries (must contain at least the original)
     */
    List<String> rewrite(String originalQuery);
}
