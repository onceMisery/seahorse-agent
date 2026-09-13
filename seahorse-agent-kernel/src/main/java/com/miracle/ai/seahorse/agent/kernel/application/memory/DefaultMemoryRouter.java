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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTrack;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;

public class DefaultMemoryRouter implements MemoryRouterPort {

    public DefaultMemoryRouter() {
    }

    @Override
    public MemoryRoutePlan route(MemoryRouteRequest request) {
        String question = Objects.requireNonNullElse(request, new MemoryRouteRequest("", "default", ""))
                .question();
        if (question.isBlank()) {
            return new MemoryRoutePlan(EnumSet.of(
                    MemoryTrack.CORRECTION,
                    MemoryTrack.SHORT_WINDOW,
                    MemoryTrack.EPISODIC,
                    MemoryTrack.PROFILE));
        }
        String lower = question.toLowerCase(Locale.ROOT);
        // Profile KV is a strong-fact source: it loads on every turn, not only on
        // profile-flavored questions. Episodic/business tracks stay keyword-gated.
        EnumSet<MemoryTrack> tracks = EnumSet.of(MemoryTrack.CORRECTION, MemoryTrack.SHORT_WINDOW,
                MemoryTrack.PROFILE);
        boolean correctionQuestion = containsAny(question, "不是", "错了", "改成", "以后别", "忘记")
                || containsAny(lower, "not anymore", "wrong", "correct", "change to", "forget");
        boolean businessQuestion = containsAny(question, "知识库", "文档", "规则", "制度", "流程", "阈值", "接口", "API")
                || containsAny(lower, "knowledge", "document", "policy", "rule", "process",
                "threshold", "api");
        boolean episodicQuestion = containsAny(question, "上次", "之前", "历史", "项目", "决策", "讨论过", "做过",
                "长期记忆", "记忆", "记得")
                || containsAny(lower, "last time", "previous", "history", "project", "decision",
                "discussed", "remember when", "long-term memory");

        if (businessQuestion) {
            tracks.add(MemoryTrack.BUSINESS_DOCUMENT);
            tracks.add(MemoryTrack.EPISODIC);
        } else if (episodicQuestion) {
            tracks.add(MemoryTrack.EPISODIC);
        }
        return new MemoryRoutePlan(tracks);
    }

    private boolean containsAny(String content, String... needles) {
        if (content == null || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && content.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
