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

package com.miracle.ai.seahorse.agent.kernel.application.sample;

import com.miracle.ai.seahorse.agent.ports.inbound.sample.CreateSampleQuestionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.sample.SampleQuestionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.sample.SampleQuestionPageCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.sample.UpdateSampleQuestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionUpdateValues;

import java.util.List;
import java.util.Objects;

/**
 * Kernel 层示例问题管理服务。
 */
public class KernelSampleQuestionService implements SampleQuestionInboundPort {

    private static final int DEFAULT_RANDOM_LIMIT = 3;
    private static final long DEFAULT_CURRENT = 1L;
    private static final long DEFAULT_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;

    private final SampleQuestionRepositoryPort repositoryPort;

    public KernelSampleQuestionService(SampleQuestionRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

    @Override
    public List<SampleQuestionRecord> listRandomQuestions() {
        return repositoryPort.listRandomQuestions(DEFAULT_RANDOM_LIMIT);
    }

    @Override
    public SampleQuestionPage page(SampleQuestionPageCommand command) {
        SampleQuestionPageCommand safeCommand = command == null
                ? new SampleQuestionPageCommand(DEFAULT_CURRENT, DEFAULT_SIZE, null)
                : command;
        return repositoryPort.page(normalizeCurrent(safeCommand.current()),
                normalizeSize(safeCommand.size()), trimToNull(safeCommand.keyword()));
    }

    @Override
    public SampleQuestionRecord queryById(String id) {
        return repositoryPort.findById(requireText(id, "id must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("sample question not found"));
    }

    @Override
    public String create(CreateSampleQuestionCommand command) {
        CreateSampleQuestionCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        return repositoryPort.create(trimToNull(safeCommand.title()), trimToNull(safeCommand.description()),
                requireText(safeCommand.question(), "question must not be blank"));
    }

    @Override
    public void update(String id, UpdateSampleQuestionCommand command) {
        UpdateSampleQuestionCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String question = !safeCommand.questionPresent()
                ? null
                : requireText(safeCommand.question(), "question must not be blank");
        SampleQuestionUpdateValues values = new SampleQuestionUpdateValues(
                trimToNull(safeCommand.title()),
                trimToNull(safeCommand.description()),
                question,
                safeCommand.titlePresent(),
                safeCommand.descriptionPresent(),
                safeCommand.questionPresent());
        boolean updated = repositoryPort.update(requireText(id, "id must not be blank"), values);
        if (!updated) {
            throw new IllegalArgumentException("sample question not found");
        }
    }

    @Override
    public void delete(String id) {
        if (!repositoryPort.delete(requireText(id, "id must not be blank"))) {
            throw new IllegalArgumentException("sample question not found");
        }
    }

    private long normalizeCurrent(long current) {
        return current < 1L ? DEFAULT_CURRENT : current;
    }

    private long normalizeSize(long size) {
        if (size < 1L) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String requireText(String value, String message) {
        String safeValue = trimToNull(value);
        if (safeValue == null) {
            throw new IllegalArgumentException(message);
        }
        return safeValue;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
