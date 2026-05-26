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

package com.miracle.ai.seahorse.agent.ports.inbound.conversation;

import java.io.InputStream;
import java.util.Objects;

public record UploadConversationAttachmentCommand(String conversationId,
                                                  String messageId,
                                                  String userId,
                                                  InputStream content,
                                                  long sizeBytes,
                                                  String fileName,
                                                  String mimeType) {

    private static final long MIN_SIZE_BYTES = 0L;

    public UploadConversationAttachmentCommand {
        conversationId = requireText(conversationId, "conversationId must not be blank");
        messageId = normalizeOptional(messageId);
        userId = requireText(userId, "userId must not be blank");
        content = Objects.requireNonNull(content, "content must not be null");
        if (sizeBytes < MIN_SIZE_BYTES) {
            throw new IllegalArgumentException("sizeBytes must not be negative");
        }
        fileName = requireText(fileName, "fileName must not be blank");
        mimeType = normalizeOptional(mimeType);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
