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

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointDiff;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 5：spec §10.2 决策表 6 行全覆盖单测。
 */
class SnapshotDiffAnalyzerTests {

    private final SnapshotDiffAnalyzer analyzer = new SnapshotDiffAnalyzer();

    @Test
    void missingPreviousCheckpointTriggersHardCascade() {
        AgentCheckpointSnapshot current = new AgentCheckpointSnapshot(
                Map.of("agentDefinition", "agent-v1"),
                "artifact-1");

        AgentCheckpointDiff diff = analyzer.analyze(null, current);

        assertThat(diff.decision()).isEqualTo(AgentCheckpointDiff.Decision.HARD_CASCADE);
        assertThat(diff.reason()).contains("previous checkpoint missing");
    }

    @Test
    void identicalInputAndArtifactReturnsUnchanged() {
        Map<String, String> hashes = Map.of("agentDefinition", "agent-v1", "outputContract", "json");
        AgentCheckpointSnapshot previous = new AgentCheckpointSnapshot(hashes, "artifact-1");
        AgentCheckpointSnapshot current = new AgentCheckpointSnapshot(hashes, "artifact-1");

        AgentCheckpointDiff diff = analyzer.analyze(previous, current);

        assertThat(diff.decision()).isEqualTo(AgentCheckpointDiff.Decision.UNCHANGED);
        assertThat(diff.changedFields()).isEmpty();
    }

    @Test
    void inputUnchangedButArtifactChangedReturnsSoftPatch() {
        Map<String, String> hashes = Map.of("agentDefinition", "agent-v1");
        AgentCheckpointSnapshot previous = new AgentCheckpointSnapshot(hashes, "artifact-1");
        AgentCheckpointSnapshot current = new AgentCheckpointSnapshot(hashes, "artifact-2");

        AgentCheckpointDiff diff = analyzer.analyze(previous, current);

        assertThat(diff.decision()).isEqualTo(AgentCheckpointDiff.Decision.SOFT_PATCH);
        assertThat(diff.changedFields()).isEmpty();
        assertThat(diff.reason()).contains("artifact hash changed");
    }

    @Test
    void nonDependencyInputChangeReturnsSoftPatch() {
        AgentCheckpointSnapshot previous = new AgentCheckpointSnapshot(
                Map.of("userTraceMetadata", "trace-1", "agentDefinition", "agent-v1"),
                "artifact-1");
        AgentCheckpointSnapshot current = new AgentCheckpointSnapshot(
                Map.of("userTraceMetadata", "trace-2", "agentDefinition", "agent-v1"),
                "artifact-1");

        AgentCheckpointDiff diff = analyzer.analyze(previous, current);

        assertThat(diff.decision()).isEqualTo(AgentCheckpointDiff.Decision.SOFT_PATCH);
        assertThat(diff.changedFields()).containsExactly("userTraceMetadata");
        assertThat(diff.reason()).contains("non-dependency");
    }

    @Test
    void dependencyInputChangeReturnsHardCascade() {
        AgentCheckpointSnapshot previous = new AgentCheckpointSnapshot(
                Map.of("agentDefinition", "agent-v1", "allowedTools", "tools-1"),
                "artifact-1");
        AgentCheckpointSnapshot current = new AgentCheckpointSnapshot(
                Map.of("agentDefinition", "agent-v2", "allowedTools", "tools-1"),
                "artifact-1");

        AgentCheckpointDiff diff = analyzer.analyze(previous, current);

        assertThat(diff.decision()).isEqualTo(AgentCheckpointDiff.Decision.HARD_CASCADE);
        assertThat(diff.changedFields()).containsExactly("agentDefinition");
        assertThat(diff.reason()).contains("dependency input fields changed");
    }

    @Test
    void multipleDependencyChangesPreserveAllChangedKeys() {
        AgentCheckpointSnapshot previous = new AgentCheckpointSnapshot(
                Map.of("upstreamPhase", "phase-a", "outputContract", "contract-1"),
                "artifact-1");
        AgentCheckpointSnapshot current = new AgentCheckpointSnapshot(
                Map.of("upstreamPhase", "phase-b", "outputContract", "contract-2"),
                "artifact-2");

        AgentCheckpointDiff diff = analyzer.analyze(previous, current);

        assertThat(diff.decision()).isEqualTo(AgentCheckpointDiff.Decision.HARD_CASCADE);
        assertThat(diff.changedFields()).containsExactlyInAnyOrder("upstreamPhase", "outputContract");
    }

    @Test
    void customDependencyFieldsOverrideDefaultClassification() {
        SnapshotDiffAnalyzer custom = new SnapshotDiffAnalyzer(Set.of("locale"));
        AgentCheckpointSnapshot previous = new AgentCheckpointSnapshot(
                Map.of("locale", "en", "agentDefinition", "agent-v1"),
                "artifact-1");
        AgentCheckpointSnapshot current = new AgentCheckpointSnapshot(
                Map.of("locale", "zh", "agentDefinition", "agent-v1"),
                "artifact-1");

        AgentCheckpointDiff diff = custom.analyze(previous, current);

        assertThat(diff.decision()).isEqualTo(AgentCheckpointDiff.Decision.HARD_CASCADE);
        assertThat(diff.changedFields()).containsExactly("locale");
    }

    @Test
    void unknownFieldOnlyOnOneSideCountsAsChanged() {
        AgentCheckpointSnapshot previous = new AgentCheckpointSnapshot(Map.of(), "artifact-1");
        AgentCheckpointSnapshot current = new AgentCheckpointSnapshot(
                Map.of("agentDefinition", "agent-v1"),
                "artifact-1");

        AgentCheckpointDiff diff = analyzer.analyze(previous, current);

        assertThat(diff.decision()).isEqualTo(AgentCheckpointDiff.Decision.HARD_CASCADE);
        assertThat(diff.changedFields()).containsExactly("agentDefinition");
    }
}
