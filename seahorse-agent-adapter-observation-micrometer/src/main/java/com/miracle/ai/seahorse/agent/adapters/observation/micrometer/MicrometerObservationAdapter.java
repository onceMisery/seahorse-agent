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
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Micrometer 观测 adapter。
 *
 * <p>该实现将 Seahorse 内核观测端口转换为 Micrometer timer/counter 指标，不依赖
 * Spring Boot 自动配置，可在任意运行时通过构造注入 {@link MeterRegistry}。
 */
public class MicrometerObservationAdapter implements ObservationPort {

    private static final String METRIC_DURATION = "seahorse.agent.observation.duration";
    private static final String METRIC_EVENT = "seahorse.agent.observation.events";
    private static final String TAG_OBSERVATION = "observation";
    private static final String TAG_EVENT = "event";
    private static final String TAG_TENANT = "tenant";

    private final MeterRegistry meterRegistry;

    public MicrometerObservationAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    @Override
    public ObservationScope start(ObservationCommand command) {
        ObservationCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        Timer.Sample sample = Timer.start(meterRegistry);
        Tags tags = commandTags(safeCommand);
        return new MicrometerObservationScope(meterRegistry, sample, tags);
    }

    @Override
    public void recordEvent(ObservationEvent event) {
        ObservationEvent safeEvent = Objects.requireNonNull(event, "event must not be null");
        if (safeEvent.amount() <= 0L) {
            return;
        }
        Counter.builder(METRIC_EVENT)
                .tags(eventTags(safeEvent))
                .register(meterRegistry)
                .increment(safeEvent.amount());
    }

    private Tags commandTags(ObservationCommand command) {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of(TAG_OBSERVATION, command.name()));
        if (!command.tenantId().isBlank()) {
            tags.add(Tag.of(TAG_TENANT, command.tenantId()));
        }
        addAttributeTags(tags, command.attributes());
        return Tags.of(tags);
    }

    private Tags eventTags(ObservationEvent event) {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of(TAG_EVENT, event.name()));
        addAttributeTags(tags, event.attributes());
        return Tags.of(tags);
    }

    private void addAttributeTags(List<Tag> tags, Map<String, String> attributes) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (validTag(entry)) {
                tags.add(Tag.of(entry.getKey(), entry.getValue()));
            }
        }
    }

    private boolean validTag(Map.Entry<String, String> entry) {
        String key = entry.getKey();
        String value = entry.getValue();
        return key != null && !key.isBlank() && value != null;
    }

    private static final class MicrometerObservationScope implements ObservationScope {

        private final MeterRegistry meterRegistry;
        private final Timer.Sample sample;
        private final Tags tags;
        private final AtomicBoolean closed = new AtomicBoolean();

        private MicrometerObservationScope(MeterRegistry meterRegistry, Timer.Sample sample, Tags tags) {
            this.meterRegistry = meterRegistry;
            this.sample = sample;
            this.tags = tags;
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            ObservationEvent safeEvent = Objects.requireNonNull(event, "event must not be null");
            if (safeEvent.amount() <= 0L) {
                return;
            }
            Counter.builder(METRIC_EVENT)
                    .tags(tags.and(TAG_EVENT, safeEvent.name()))
                    .register(meterRegistry)
                    .increment(safeEvent.amount());
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                Timer timer = Timer.builder(METRIC_DURATION)
                        .tags(tags)
                        .register(meterRegistry);
                sample.stop(timer);
            }
        }
    }
}
