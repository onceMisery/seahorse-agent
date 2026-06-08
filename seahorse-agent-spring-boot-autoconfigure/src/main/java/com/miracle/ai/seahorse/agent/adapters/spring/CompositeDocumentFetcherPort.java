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

import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetchResult;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;

import java.util.List;
import java.util.Objects;

/**
 * 多来源文档拉取委派端口。
 */
public class CompositeDocumentFetcherPort implements DocumentFetcherPort {

    private final List<DocumentFetcherPort> delegates;

    public CompositeDocumentFetcherPort(List<DocumentFetcherPort> delegates) {
        this.delegates = List.copyOf(Objects.requireNonNullElse(delegates, List.of()));
    }

    @Override
    public boolean supports(String sourceType) {
        return delegates.stream().anyMatch(delegate -> delegate.supports(sourceType));
    }

    @Override
    public DocumentFetchResult fetch(DocumentFetchRequest request) {
        DocumentFetchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        return delegates.stream()
                .filter(delegate -> delegate.supports(safeRequest.sourceType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unsupported document source type: " + safeRequest.sourceType()))
                .fetch(safeRequest);
    }
}
