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
        EnumSet<MemoryTrack> tracks = EnumSet.of(MemoryTrack.CORRECTION, MemoryTrack.SHORT_WINDOW);
        boolean profileQuestion = containsAny(question, "我是谁", "我的职业", "我的身份", "我的技术栈", "我的偏好", "我喜欢",
                "我的回答风格", "我的回复风格", "用户画像", "个人画像")
                || isChineseProfileRecallQuestion(question)
                || containsAny(lower, "who am i", "my occupation", "my profession", "my job",
                "my identity", "my tech stack", "my preference", "what do i like", "profile",
                "user profile", "memory about me", "remember about me");
        boolean correctionQuestion = containsAny(question, "不是", "错了", "改成", "以后别", "忘记")
                || containsAny(lower, "not anymore", "wrong", "correct", "change to", "forget");
        boolean businessQuestion = containsAny(question, "知识库", "文档", "规则", "制度", "流程", "阈值", "接口", "API")
                || containsAny(lower, "knowledge", "document", "policy", "rule", "process",
                "threshold", "api");
        boolean episodicQuestion = containsAny(question, "上次", "之前", "历史", "项目", "决策", "讨论过", "做过",
                "长期记忆", "记忆", "记得")
                || containsAny(lower, "last time", "previous", "history", "project", "decision",
                "discussed", "remember when", "long-term memory");

        if (profileQuestion || correctionQuestion || businessQuestion || episodicQuestion) {
            tracks.add(MemoryTrack.PROFILE);
        }
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

    private boolean isChineseProfileRecallQuestion(String question) {
        return containsAny(question, "职业", "身份", "偏好", "回答风格", "回复风格", "画像")
                && containsAny(question, "我", "用户", "个人", "长期记忆", "记忆", "记得");
    }
}
