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

package com.miracle.ai.seahorse.agent.kernel.application.feedback;

import com.miracle.ai.seahorse.agent.ports.inbound.feedback.MessageFeedbackInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.SubmitMessageFeedbackCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackSubmission;

import java.time.Instant;
import java.util.Objects;

/**
 * L1 消息反馈应用服务。
 *
 * <p>默认直接写入反馈仓储，保证无 MQ 或 outbox 时反馈不丢失；需要异步化时可在 Web 层或端口 wrapper 中切换。
 */
public class KernelMessageFeedbackService implements MessageFeedbackInboundPort {

    private final MessageFeedbackRepositoryPort repositoryPort;

    public KernelMessageFeedbackService(MessageFeedbackRepositoryPort repositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
    }

    @Override
    public void submit(SubmitMessageFeedbackCommand command) {
        SubmitMessageFeedbackCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        repositoryPort.upsert(new MessageFeedbackSubmission(
                safeCommand.messageId(),
                safeCommand.userId(),
                safeCommand.vote(),
                safeCommand.reason(),
                safeCommand.comment(),
                Instant.now()));
    }
}
