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

package com.miracle.ai.seahorse.agent.adapters.web;

import java.util.List;
import java.util.Map;

/**
 * 元数据复核提交请求。
 */
public class MetadataReviewDecisionRequest {

    private String comment;
    private Map<String, Object> correctedMetadata;
    private List<String> ignoredFields;
    private String extractorVersion;
    private String pipelineId;

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Map<String, Object> getCorrectedMetadata() {
        return correctedMetadata;
    }

    public void setCorrectedMetadata(Map<String, Object> correctedMetadata) {
        this.correctedMetadata = correctedMetadata;
    }

    public List<String> getIgnoredFields() {
        return ignoredFields;
    }

    public void setIgnoredFields(List<String> ignoredFields) {
        this.ignoredFields = ignoredFields;
    }

    public String getExtractorVersion() {
        return extractorVersion;
    }

    public void setExtractorVersion(String extractorVersion) {
        this.extractorVersion = extractorVersion;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }
}
