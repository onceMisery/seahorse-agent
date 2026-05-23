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

package com.miracle.ai.seahorse.agent.adapters.observation.micrometer;

import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerObservationAdapterTests {

    private static final String METRIC_EVENT = "seahorse.agent.observation.events";
    private static final String TAG_EVENT = "event";
    private static final String TAG_STAGE = "stage";

    @Test
    void shouldAccumulateEventCounterByExplicitAmount() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerObservationAdapter adapter = new MicrometerObservationAdapter(registry);

        adapter.recordEvent(new ObservationEvent(
                "memory-maintenance-stage",
                Instant.EPOCH,
                7L,
                Map.of(TAG_STAGE, "compaction.scanned")));

        Counter counter = registry.find(METRIC_EVENT)
                .tag(TAG_EVENT, "memory-maintenance-stage")
                .tag(TAG_STAGE, "compaction.scanned")
                .counter();
        assertThat(counter).as("counter should be registered").isNotNull();
        assertThat(counter.count()).isEqualTo(7.0D);
    }

    @Test
    void shouldDefaultEventAmountToOneWhenLegacyConstructorIsUsed() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerObservationAdapter adapter = new MicrometerObservationAdapter(registry);

        adapter.recordEvent(new ObservationEvent(
                "memory-refiner",
                Instant.EPOCH,
                Map.of()));

        Counter counter = registry.find(METRIC_EVENT)
                .tag(TAG_EVENT, "memory-refiner")
                .counter();
        assertThat(counter.count()).isEqualTo(ObservationEvent.DEFAULT_AMOUNT);
    }

    @Test
    void shouldSkipNonPositiveAmountToAvoidRegisteringEmptyCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerObservationAdapter adapter = new MicrometerObservationAdapter(registry);

        adapter.recordEvent(new ObservationEvent(
                "memory-maintenance-stage",
                Instant.EPOCH,
                0L,
                Map.of(TAG_STAGE, "gc.scanned")));

        Counter counter = registry.find(METRIC_EVENT)
                .tag(TAG_EVENT, "memory-maintenance-stage")
                .tag(TAG_STAGE, "gc.scanned")
                .counter();
        assertThat(counter).as("zero-amount events should not create counters").isNull();
    }

    @Test
    void shouldAccumulateScopeEventCounterByAmount() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerObservationAdapter adapter = new MicrometerObservationAdapter(registry);
        try (ObservationScope scope = adapter.start(new ObservationCommand(
                "memory-recall",
                "tenant-a",
                Map.of()))) {
            scope.recordEvent(new ObservationEvent(
                    "recall-channel-hit",
                    Instant.EPOCH,
                    3L,
                    Map.of()));
        }

        Counter counter = registry.find(METRIC_EVENT)
                .tag("observation", "memory-recall")
                .tag(TAG_EVENT, "recall-channel-hit")
                .counter();
        assertThat(counter).as("scope counter should be registered").isNotNull();
        assertThat(counter.count()).isEqualTo(3.0D);
    }
}
