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
 * Outbound port for rerank model operations in the Advanced RAG pipeline.
 *
 * <p>Implementations delegate to an external rerank provider (e.g. Jina AI)
 * to re-score candidate documents against a query.
 */
public interface RerankModelPort {

    /**
     * Rerank the given documents for the specified query.
     *
     * @param query     the user query
     * @param documents the candidate document texts
     * @param topK      the maximum number of results to return
     * @return rerank results ordered by descending relevance
     */
    List<RerankResult> rerank(String query, List<String> documents, int topK);

    /**
     * A single rerank scoring result.
     *
     * @param index the zero-based index into the original documents list
     * @param score the relevance score assigned by the model
     */
    record RerankResult(int index, double score) {}
}
