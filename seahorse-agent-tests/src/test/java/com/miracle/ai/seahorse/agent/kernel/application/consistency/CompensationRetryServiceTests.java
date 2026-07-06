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

package com.miracle.ai.seahorse.agent.kernel.application.consistency;

import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class CompensationRetryServiceTests {

    @Test
    void shouldRedactCredentialTextBeforePersistingRetryFailure() {
        RecordingCompensationLogPort logPort = new RecordingCompensationLogPort();
        CompensationLog log = new CompensationLog();
        log.setId(7L);
        log.setOperationType("vector_repair");
        log.setOperationId("op-a");
        log.setPayload("{}");
        log.setRetryCount(0);
        log.setMaxRetries(3);
        logPort.logs.add(log);
        CompensationRetryService service = new CompensationRetryService(
                logPort,
                DistributedLockPort.noop(),
                Map.of("vector_repair", ignored -> {
                    throw new IllegalStateException(
                            "repair failed Authorization: Bearer abcdefghijklmnop api_key=plain-comp-secret");
                }));

        service.executeRetry();

        Assertions.assertEquals(CompensationLog.CompensationStatus.PENDING, logPort.status);
        Assertions.assertNotNull(logPort.lastError);
        Assertions.assertTrue(logPort.lastError.contains("[REDACTED]"), logPort.lastError);
        Assertions.assertFalse(logPort.lastError.contains("abcdefghijklmnop"), logPort.lastError);
        Assertions.assertFalse(logPort.lastError.contains("plain-comp-secret"), logPort.lastError);
    }

    private static class RecordingCompensationLogPort implements CompensationLogPort {

        private final List<CompensationLog> logs = new ArrayList<>();
        private CompensationLog.CompensationStatus status;
        private String lastError;

        @Override
        public void save(CompensationLog log) {
            logs.add(log);
        }

        @Override
        public List<CompensationLog> findPendingRetries(int limit) {
            return List.copyOf(logs);
        }

        @Override
        public void updateStatus(Long id, CompensationLog.CompensationStatus status, String lastError) {
            this.status = status;
            this.lastError = lastError;
        }

        @Override
        public void incrementRetryCount(Long id) {
        }
    }
}
