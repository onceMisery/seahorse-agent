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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class MemoryTrackWriteServiceTests {

    private static final String SLOT_KIND = "PROFILE_SLOT";
    private static final String OCCUPATION_SLOT = "identity.occupation";

    @Test
    void writeOccupationCorrection_allStepsSucceed() {
        AtomicBoolean correctionCalled = new AtomicBoolean();
        AtomicBoolean profileCalled = new AtomicBoolean();
        AtomicInteger lifecycleCalled = new AtomicInteger();

        MemoryTrackWriteService service = newService(
                correction -> correctionCalled.set(true),
                update -> profileCalled.set(true),
                (u, t, s, g, r) -> { lifecycleCalled.incrementAndGet(); return 1; });

        MemoryTrackWriteResult result = service.writeOccupationCorrection(
                "user-1", "tenant-1", "msg-1", "engineer", "doctor");

        Assertions.assertTrue(result.correctionWritten());
        Assertions.assertTrue(result.profileWritten());
        Assertions.assertTrue(result.obsoleteMarked());
        Assertions.assertTrue(result.coreSuccess());
        Assertions.assertEquals(2, result.operations().size());
        Assertions.assertTrue(correctionCalled.get());
        Assertions.assertTrue(profileCalled.get());
        Assertions.assertEquals(1, lifecycleCalled.get());
    }

    @Test
    void writeOccupationCorrection_correctionFails_throwsException() {
        MemoryTrackWriteService service = newService(
                correction -> { throw new RuntimeException("DB connection failed"); },
                update -> {},
                (u, t, s, g, r) -> 1);

        MemoryTrackWriteException ex = Assertions.assertThrows(MemoryTrackWriteException.class,
                () -> service.writeOccupationCorrection("user-1", "tenant-1", "msg-1", "engineer", "doctor"));

        Assertions.assertEquals("correction_upsert", ex.failedStep());
    }

    @Test
    void writeOccupationCorrection_profileFails_throwsException() {
        MemoryTrackWriteService service = newService(
                correction -> {},
                update -> { throw new RuntimeException("Profile store unavailable"); },
                (u, t, s, g, r) -> 1);

        MemoryTrackWriteException ex = Assertions.assertThrows(MemoryTrackWriteException.class,
                () -> service.writeOccupationCorrection("user-1", "tenant-1", "msg-1", "engineer", "doctor"));

        Assertions.assertEquals("profile_upsert", ex.failedStep());
    }

    @Test
    void writeOccupationCorrection_obsoleteFails_silentlySucceeds() {
        MemoryTrackWriteService service = newService(
                correction -> {},
                update -> {},
                (u, t, s, g, r) -> { throw new RuntimeException("Lifecycle failure"); });

        MemoryTrackWriteResult result = service.writeOccupationCorrection(
                "user-1", "tenant-1", "msg-1", "engineer", "doctor");

        Assertions.assertTrue(result.correctionWritten());
        Assertions.assertTrue(result.profileWritten());
        Assertions.assertFalse(result.obsoleteMarked());
        Assertions.assertTrue(result.coreSuccess());
    }

    @Test
    void writeProfileFact_succeeds() {
        AtomicBoolean profileCalled = new AtomicBoolean();
        MemoryTrackWriteService service = newService(
                correction -> {},
                update -> profileCalled.set(true),
                (u, t, s, g, r) -> 1);

        boolean result = service.writeProfileFact(
                "user-1", "tenant-1", "identity.name", "John", 0.9, "msg-1", "gen-1");

        Assertions.assertTrue(result);
        Assertions.assertTrue(profileCalled.get());
    }

    @Test
    void writeProfileFact_blankSlotKey_returnsFalse() {
        MemoryTrackWriteService service = newService(
                correction -> {}, update -> {}, (u, t, s, g, r) -> 1);

        boolean result = service.writeProfileFact(
                "user-1", "tenant-1", "", "John", 0.9, "msg-1", "gen-1");

        Assertions.assertFalse(result);
    }

    @Test
    void writeProfileFact_profileFails_throwsException() {
        MemoryTrackWriteService service = newService(
                correction -> {},
                update -> { throw new RuntimeException("Profile write failed"); },
                (u, t, s, g, r) -> 1);

        Assertions.assertThrows(MemoryTrackWriteException.class,
                () -> service.writeProfileFact(
                        "user-1", "tenant-1", "identity.name", "John", 0.9, "msg-1", "gen-1"));
    }

    @Test
    void writeProfileFact_obsoleteFails_stillReturnsTrue() {
        MemoryTrackWriteService service = newService(
                correction -> {},
                update -> {},
                (u, t, s, g, r) -> { throw new RuntimeException("lifecycle failed"); });

        boolean result = service.writeProfileFact(
                "user-1", "tenant-1", "identity.name", "John", 0.9, "msg-1", "gen-1");

        Assertions.assertTrue(result);
    }

    @Test
    void markProfileSlotFragmentsObsolete_silentOnFailure() {
        MemoryTrackWriteService service = newService(
                correction -> {},
                update -> {},
                (u, t, s, g, r) -> { throw new RuntimeException("lifecycle error"); });

        // Should not throw
        service.markProfileSlotFragmentsObsolete("user-1", "tenant-1", "identity.occupation", "gen-1");
    }

    private MemoryTrackWriteService newService(
            java.util.function.Consumer<CorrectionCommand> correctionAction,
            java.util.function.Consumer<ProfileFactUpdate> profileAction,
            LifecycleAction lifecycleAction) {
        return new MemoryTrackWriteService(
                newProfilePort(profileAction),
                newCorrectionPort(correctionAction),
                newLifecyclePort(lifecycleAction),
                SLOT_KIND,
                OCCUPATION_SLOT);
    }

    @FunctionalInterface
    private interface LifecycleAction {
        int execute(String userId, String tenantId, String slot, String generationId, String reason);
    }

    private static ProfileMemoryPort newProfilePort(java.util.function.Consumer<ProfileFactUpdate> action) {
        return new ProfileMemoryPort() {
            @Override
            public Optional<ProfileFact> findActive(String userId, String tenantId, String slotKey) {
                return Optional.empty();
            }

            @Override
            public List<ProfileFact> listActive(String userId, String tenantId, int limit) {
                return List.of();
            }

            @Override
            public void upsert(ProfileFactUpdate update) {
                action.accept(update);
            }
        };
    }

    private static CorrectionLedgerPort newCorrectionPort(
            java.util.function.Consumer<CorrectionCommand> action) {
        return new CorrectionLedgerPort() {
            @Override
            public List<CorrectionRule> listActive(String userId, String tenantId, int limit) {
                return List.of();
            }

            @Override
            public void upsert(CorrectionCommand command) {
                action.accept(command);
            }
        };
    }

    private static MemoryLifecyclePort newLifecyclePort(LifecycleAction action) {
        return new MemoryLifecyclePort() {
            @Override
            public int markObsoleteByProfileSlot(String userId, String tenantId,
                                                 String profileSlot, String activeGenerationId,
                                                 String reason) {
                return action.execute(userId, tenantId, profileSlot, activeGenerationId, reason);
            }

            @Override
            public void recordRead(String layer, String memoryId, Instant referencedAt) {
            }
        };
    }
}
