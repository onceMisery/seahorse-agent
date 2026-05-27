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

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.EvidenceItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 引用验证器。
 *
 * <p>解析报告中的引用标号 [1], [2], ...，检查每个引用是否有对应 EvidenceItem。
 */
public class CitationVerifier {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[(\\d+)]");

    /**
     * 验证报告内容中的引用是否都有对应证据支撑。
     */
    public VerificationResult verify(String reportContent, List<EvidenceItem> evidence) {
        Set<Integer> citedIndices = extractCitationIndices(reportContent);
        Set<Integer> evidenceIndices = new HashSet<>();
        for (EvidenceItem item : evidence) {
            evidenceIndices.add(item.citationIndex());
        }

        List<Integer> verified = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();

        for (int index : citedIndices) {
            if (evidenceIndices.contains(index)) {
                verified.add(index);
            } else {
                missing.add(index);
            }
        }

        List<Integer> unreferenced = new ArrayList<>();
        for (int index : evidenceIndices) {
            if (!citedIndices.contains(index)) {
                unreferenced.add(index);
            }
        }

        return new VerificationResult(verified, missing, unreferenced);
    }

    private Set<Integer> extractCitationIndices(String content) {
        Set<Integer> indices = new HashSet<>();
        if (content == null || content.isBlank()) {
            return indices;
        }
        Matcher matcher = CITATION_PATTERN.matcher(content);
        while (matcher.find()) {
            try {
                indices.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return indices;
    }

    /**
     * 引用验证结果。
     */
    public record VerificationResult(
        List<Integer> verified,
        List<Integer> missing,
        List<Integer> unreferenced
    ) {
        public boolean isFullyVerified() {
            return missing.isEmpty();
        }
    }
}
