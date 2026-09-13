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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRoutePlan;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTrack;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultMemoryRouterTests {

    private final DefaultMemoryRouter router = new DefaultMemoryRouter();

    @Test
    void shouldAlwaysRouteProfileTrackForPlainQuestions() {
        MemoryRoutePlan plan = router.route(new MemoryRouteRequest("user-1", "tenant-1",
                "帮我总结一下这份报表的要点"));

        // Profile KV is a strong-fact source and must load on every turn,
        // regardless of profile-flavored keywords in the question.
        Set<MemoryTrack> tracks = plan.activeTracks();
        assertTrue(tracks.contains(MemoryTrack.PROFILE));
        assertTrue(tracks.contains(MemoryTrack.CORRECTION));
        assertTrue(tracks.contains(MemoryTrack.SHORT_WINDOW));
        assertFalse(tracks.contains(MemoryTrack.BUSINESS_DOCUMENT));
        assertFalse(tracks.contains(MemoryTrack.EPISODIC));
    }

    @Test
    void shouldKeepGatedTracksForBlankQuestion() {
        MemoryRoutePlan plan = router.route(new MemoryRouteRequest("user-1", "tenant-1", "  "));

        Set<MemoryTrack> tracks = plan.activeTracks();
        assertTrue(tracks.contains(MemoryTrack.PROFILE));
        assertTrue(tracks.contains(MemoryTrack.CORRECTION));
        assertTrue(tracks.contains(MemoryTrack.SHORT_WINDOW));
        assertTrue(tracks.contains(MemoryTrack.EPISODIC));
        assertFalse(tracks.contains(MemoryTrack.BUSINESS_DOCUMENT));
    }

    @Test
    void shouldAppendBusinessAndEpisodicTracksOnBusinessQuestion() {
        MemoryRoutePlan plan = router.route(new MemoryRouteRequest("user-1", "tenant-1",
                "知识库里关于报销流程的规则是什么"));

        Set<MemoryTrack> tracks = plan.activeTracks();
        assertTrue(tracks.contains(MemoryTrack.PROFILE));
        assertTrue(tracks.contains(MemoryTrack.BUSINESS_DOCUMENT));
        assertTrue(tracks.contains(MemoryTrack.EPISODIC));
    }

    @Test
    void shouldAppendEpisodicTrackOnEpisodicQuestion() {
        MemoryRoutePlan plan = router.route(new MemoryRouteRequest("user-1", "tenant-1",
                "上次我们讨论过的方案结论是什么"));

        Set<MemoryTrack> tracks = plan.activeTracks();
        assertTrue(tracks.contains(MemoryTrack.PROFILE));
        assertTrue(tracks.contains(MemoryTrack.EPISODIC));
        assertFalse(tracks.contains(MemoryTrack.BUSINESS_DOCUMENT));
    }

    @Test
    void shouldHandleNullRequestAsBlankQuestion() {
        MemoryRoutePlan plan = router.route(null);

        Set<MemoryTrack> tracks = plan.activeTracks();
        assertTrue(tracks.contains(MemoryTrack.PROFILE));
        assertTrue(tracks.contains(MemoryTrack.EPISODIC));
    }
}
