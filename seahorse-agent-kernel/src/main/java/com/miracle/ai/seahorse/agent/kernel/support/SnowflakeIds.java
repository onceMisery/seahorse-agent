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

package com.miracle.ai.seahorse.agent.kernel.support;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.zip.CRC32;

/**
 * Compact, time-ordered IDs for database primary keys.
 */
public final class SnowflakeIds {

    private static final long CUSTOM_EPOCH_MILLIS = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
    private static final int NODE_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1L;
    private static final long MAX_NODE_ID = (1L << NODE_BITS) - 1L;
    private static final long NODE_ID = resolveNodeId();

    private static long lastTimestamp = -1L;
    private static long sequence = 0L;

    private SnowflakeIds() {
    }

    public static synchronized long nextId() {
        long timestamp = currentTimestamp();
        if (timestamp < lastTimestamp) {
            timestamp = lastTimestamp;
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1L) & MAX_SEQUENCE;
            if (sequence == 0L) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return (timestamp << (NODE_BITS + SEQUENCE_BITS)) | (NODE_ID << SEQUENCE_BITS) | sequence;
    }

    public static String nextIdString() {
        return Long.toString(nextId());
    }

    private static long currentTimestamp() {
        return System.currentTimeMillis() - CUSTOM_EPOCH_MILLIS;
    }

    private static long waitNextMillis(long previousTimestamp) {
        long timestamp = currentTimestamp();
        while (timestamp <= previousTimestamp) {
            timestamp = currentTimestamp();
        }
        return timestamp;
    }

    private static long resolveNodeId() {
        String configured = System.getenv("SEAHORSE_SNOWFLAKE_NODE_ID");
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty("seahorse.snowflake.node-id");
        }
        if (configured != null && !configured.isBlank()) {
            try {
                long nodeId = Long.parseLong(configured.trim());
                if (nodeId >= 0L && nodeId <= MAX_NODE_ID) {
                    return nodeId;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to stable local hash.
            }
        }
        CRC32 crc32 = new CRC32();
        crc32.update(stableNodeSource().getBytes(StandardCharsets.UTF_8));
        return crc32.getValue() & MAX_NODE_ID;
    }

    private static String stableNodeSource() {
        try {
            return InetAddress.getLocalHost().getHostName() + ":" + ManagementFactory.getRuntimeMXBean().getName();
        } catch (Exception ignored) {
            return ManagementFactory.getRuntimeMXBean().getName();
        }
    }
}
