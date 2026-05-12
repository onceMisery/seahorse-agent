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

package com.miracle.ai.seahorse.agent.ports.inbound.ingestion;

import java.util.Objects;

/**
 * 上传文件并执行入库任务命令。
 */
public record IngestionTaskUploadCommand(
        String pipelineId,
        String originalFilename,
        String contentType,
        byte[] content,
        String operator
) {

    public IngestionTaskUploadCommand {
        originalFilename = Objects.requireNonNullElse(originalFilename, "");
        contentType = Objects.requireNonNullElse(contentType, "");
        content = content == null ? new byte[0] : content.clone();
        operator = Objects.requireNonNullElse(operator, "");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
