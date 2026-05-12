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

package com.miracle.ai.seahorse.agent.kernel.plugin;

import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AdapterHealthIndicatorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AdapterHealthStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class FeatureHealthAggregatorTests {

    @Test
    void shouldAggregateFeatureAndAdapterHealth() {
        AgentFeature feature = new HealthyFeature();
        AdapterHealthIndicatorPort adapter = () -> AdapterHealthStatus.up("jdbc");
        FeatureHealthAggregator aggregator = new FeatureHealthAggregator(List.of(feature), List.of(adapter));

        FeatureHealthReport report = aggregator.health();

        Assertions.assertTrue(report.up());
        Assertions.assertEquals(1, report.features().size());
        Assertions.assertEquals(1, report.adapters().size());
    }

    @Test
    void shouldConvertFeatureHealthExceptionToDownStatus() {
        FeatureHealthAggregator aggregator = new FeatureHealthAggregator(
                List.of(new FailingFeature()), List.of());

        FeatureHealthReport report = aggregator.health();

        Assertions.assertFalse(report.up());
        Assertions.assertEquals("broken", report.features().get(0).message());
    }

    private static final class HealthyFeature implements AgentFeature {

        @Override
        public String name() {
            return "healthy";
        }

        @Override
        public FeatureType type() {
            return FeatureType.SEARCH_CHANNEL;
        }
    }

    private static final class FailingFeature implements AgentFeature {

        @Override
        public String name() {
            return "failing";
        }

        @Override
        public FeatureType type() {
            return FeatureType.SEARCH_CHANNEL;
        }

        @Override
        public FeatureHealth health() {
            throw new IllegalStateException("broken");
        }
    }
}

