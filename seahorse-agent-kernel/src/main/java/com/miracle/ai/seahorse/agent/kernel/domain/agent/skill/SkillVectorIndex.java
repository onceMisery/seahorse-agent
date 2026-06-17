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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.skill;

import java.util.List;

/**
 * Skill 向量索引记录。
 *
 * <p>存储 Skill 的语义向量表示，用于基于向量的相似度搜索。
 */
public record SkillVectorIndex(
        String skillName,
        String tenantId,
        String revisionId,
        float[] embedding,
        String content,
        long timestamp) {

    public SkillVectorIndex {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (embedding == null || embedding.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
    }

    /**
     * 构建用于向量化的文本内容。
     *
     * @param skill Skill 实体
     * @param revisionContent Skill 正文内容
     * @return 用于 embedding 的文本
     */
    public static String buildContentForEmbedding(AgentSkill skill, String revisionContent) {
        StringBuilder builder = new StringBuilder();

        // 名称
        builder.append("Skill: ").append(skill.name()).append("\n");

        // 描述
        if (skill.description() != null && !skill.description().isBlank()) {
            builder.append("Description: ").append(skill.description()).append("\n");
        }

        // 标签
        if (skill.tags() != null && !skill.tags().isEmpty()) {
            builder.append("Tags: ").append(String.join(", ", skill.tags())).append("\n");
        }

        // 正文（截取前 500 字符）
        if (revisionContent != null && !revisionContent.isBlank()) {
            String truncated = revisionContent.length() > 500
                    ? revisionContent.substring(0, 500) + "..."
                    : revisionContent;
            builder.append("Content: ").append(truncated).append("\n");
        }

        return builder.toString();
    }
}
