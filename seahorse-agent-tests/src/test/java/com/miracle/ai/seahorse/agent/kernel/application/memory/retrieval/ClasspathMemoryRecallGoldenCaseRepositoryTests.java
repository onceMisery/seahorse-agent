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

package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallGoldenCaseProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasspathMemoryRecallGoldenCaseRepositoryTests {

    @Test
    void shouldLoadProfileFromClasspathResource() {
        ClasspathMemoryRecallGoldenCaseRepository repository = new ClasspathMemoryRecallGoldenCaseRepository();

        Optional<MemoryRecallGoldenCaseProfile> profile = repository.findByName("smoke");

        assertThat(profile).isPresent();
        MemoryRecallGoldenCaseProfile resolved = profile.get();
        assertThat(resolved.name()).isEqualTo("smoke");
        assertThat(resolved.topK()).isEqualTo(3);
        assertThat(resolved.cases()).hasSize(2);
        assertThat(resolved.cases().get(0).caseId()).isEqualTo("smoke-1");
        assertThat(resolved.cases().get(0).expectedMemoryIds()).containsExactly("mem-pip", "mem-java");
    }

    @Test
    void shouldReturnEmptyForUnknownProfile() {
        ClasspathMemoryRecallGoldenCaseRepository repository = new ClasspathMemoryRecallGoldenCaseRepository();

        assertThat(repository.findByName("does-not-exist")).isEmpty();
    }

    @Test
    void shouldReturnEmptyForBlankProfileName() {
        ClasspathMemoryRecallGoldenCaseRepository repository = new ClasspathMemoryRecallGoldenCaseRepository();

        assertThat(repository.findByName("   ")).isEmpty();
        assertThat(repository.findByName(null)).isEmpty();
    }

    @Test
    void shouldListProfileNamesFromIndex() {
        ClasspathMemoryRecallGoldenCaseRepository repository = new ClasspathMemoryRecallGoldenCaseRepository();

        List<String> names = repository.listProfileNames();

        assertThat(names).containsExactly("smoke", "regression");
    }

    @Test
    void shouldReturnEmptyListWhenIndexFileIsAbsent() {
        ClasspathMemoryRecallGoldenCaseRepository repository = new ClasspathMemoryRecallGoldenCaseRepository(
                null, getClass().getClassLoader(), "seahorse-agent/memory-recall-golden-missing");

        assertThat(repository.listProfileNames()).isEmpty();
    }

    @Test
    void shouldFailLoudlyWhenJsonIsMalformed() {
        ClasspathMemoryRecallGoldenCaseRepository repository = new ClasspathMemoryRecallGoldenCaseRepository(
                null, getClass().getClassLoader(), "seahorse-agent/memory-recall-golden-broken");

        assertThatThrownBy(() -> repository.findByName("broken"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to read golden case profile resource");
    }

    @Test
    void shouldDefaultRootWhenBlank() {
        ClasspathMemoryRecallGoldenCaseRepository repository = new ClasspathMemoryRecallGoldenCaseRepository(
                null, getClass().getClassLoader(), "   ");

        assertThat(repository.findByName("smoke")).isPresent();
    }
}
