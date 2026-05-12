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

package com.miracle.ai.seahorse.agent.adapters.mq.pulsar;

import java.util.Locale;

/**
 * Pulsar MQ adapter 配置。
 */
public class PulsarMessageQueueProperties {

    private int sendTimeoutMs = 30000;

    private boolean blockIfQueueFull = true;

    private boolean batchingEnabled = true;

    private int batchingMaxMessages = 200;

    private int batchingMaxPublishDelayMs = 5;

    private String compressionType = "LZ4";

    public int getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public void setSendTimeoutMs(int sendTimeoutMs) {
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public boolean isBlockIfQueueFull() {
        return blockIfQueueFull;
    }

    public void setBlockIfQueueFull(boolean blockIfQueueFull) {
        this.blockIfQueueFull = blockIfQueueFull;
    }

    public boolean isBatchingEnabled() {
        return batchingEnabled;
    }

    public void setBatchingEnabled(boolean batchingEnabled) {
        this.batchingEnabled = batchingEnabled;
    }

    public int getBatchingMaxMessages() {
        return batchingMaxMessages;
    }

    public void setBatchingMaxMessages(int batchingMaxMessages) {
        this.batchingMaxMessages = batchingMaxMessages;
    }

    public int getBatchingMaxPublishDelayMs() {
        return batchingMaxPublishDelayMs;
    }

    public void setBatchingMaxPublishDelayMs(int batchingMaxPublishDelayMs) {
        this.batchingMaxPublishDelayMs = batchingMaxPublishDelayMs;
    }

    public String getCompressionType() {
        return compressionType;
    }

    public void setCompressionType(String compressionType) {
        if (compressionType == null || compressionType.isBlank()) {
            this.compressionType = "LZ4";
            return;
        }
        this.compressionType = compressionType.trim().toUpperCase(Locale.ROOT);
    }
}
