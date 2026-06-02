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

import java.util.Objects;

/**
 * 记忆代数标签：追踪记忆的细化层级，防止无限递归细化闭环。
 */
public final class GenerationTag {

    public static final int MAX_GENERATION = 2;
    public static final double HIGH_SIMILARITY_THRESHOLD = 0.90;
    public static final String ORIGINAL_GENERATION_ID = "0:::";

    private final int generation;
    private final String sourceMemoryId;
    private final double sourceSimilarity;

    private GenerationTag(int generation, String sourceMemoryId, double sourceSimilarity) {
        this.generation = Math.max(0, generation);
        this.sourceMemoryId = Objects.requireNonNullElse(sourceMemoryId, "");
        this.sourceSimilarity = Math.max(0.0, Math.min(1.0, sourceSimilarity));
    }

    public static GenerationTag parse(String generationId) {
        if (generationId == null || generationId.isBlank()) {
            return original();
        }

        String[] parts = generationId.split(":", -1);
        if (parts.length != 3) {
            return original();
        }

        try {
            int generation = Integer.parseInt(parts[0]);
            String sourceMemoryId = parts[1];
            double sourceSimilarity = parts[2].isBlank() ? 0.0 : Double.parseDouble(parts[2]);
            return new GenerationTag(generation, sourceMemoryId, sourceSimilarity);
        } catch (NumberFormatException e) {
            return original();
        }
    }

    public static GenerationTag original() {
        return new GenerationTag(0, "", 0.0);
    }

    public GenerationTag nextGeneration(String currentMemoryId, double similarity) {
        return new GenerationTag(
            this.generation + 1,
            Objects.requireNonNullElse(currentMemoryId, ""),
            similarity
        );
    }

    public boolean exceedsMaxGeneration() {
        return generation >= MAX_GENERATION;
    }

    public boolean hasHighSimilarity() {
        return sourceSimilarity >= HIGH_SIMILARITY_THRESHOLD;
    }

    public String serialize() {
        return String.format("%d:%s:%.2f", generation, sourceMemoryId, sourceSimilarity);
    }

    public int getGeneration() {
        return generation;
    }

    public String getSourceMemoryId() {
        return sourceMemoryId;
    }

    public double getSourceSimilarity() {
        return sourceSimilarity;
    }

    public boolean isOriginal() {
        return generation == 0;
    }

    @Override
    public String toString() {
        return serialize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GenerationTag that = (GenerationTag) o;
        return generation == that.generation
            && Double.compare(that.sourceSimilarity, sourceSimilarity) == 0
            && Objects.equals(sourceMemoryId, that.sourceMemoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(generation, sourceMemoryId, sourceSimilarity);
    }
}
