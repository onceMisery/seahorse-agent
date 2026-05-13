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

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;

import java.util.List;
import java.util.Map;

/**
 * Metadata Schema 字段管理请求。
 */
public class MetadataSchemaFieldRequest {

    private String tenantId;
    private String fieldKey;
    private String displayName;
    private String valueType;
    private List<String> allowedOperators;
    private Boolean required;
    private Boolean filterable;
    private Boolean sortable;
    private Boolean facetable;
    private Boolean indexed;
    private String indexPolicy;
    private Double minConfidence;
    private List<String> trustedSources;
    private Map<String, Object> extractionHints;
    private BackendFieldMapping backendMapping;
    private Integer schemaVersion;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public List<String> getAllowedOperators() {
        return allowedOperators;
    }

    public void setAllowedOperators(List<String> allowedOperators) {
        this.allowedOperators = allowedOperators;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getFilterable() {
        return filterable;
    }

    public void setFilterable(Boolean filterable) {
        this.filterable = filterable;
    }

    public Boolean getSortable() {
        return sortable;
    }

    public void setSortable(Boolean sortable) {
        this.sortable = sortable;
    }

    public Boolean getFacetable() {
        return facetable;
    }

    public void setFacetable(Boolean facetable) {
        this.facetable = facetable;
    }

    public Boolean getIndexed() {
        return indexed;
    }

    public void setIndexed(Boolean indexed) {
        this.indexed = indexed;
    }

    public String getIndexPolicy() {
        return indexPolicy;
    }

    public void setIndexPolicy(String indexPolicy) {
        this.indexPolicy = indexPolicy;
    }

    public Double getMinConfidence() {
        return minConfidence;
    }

    public void setMinConfidence(Double minConfidence) {
        this.minConfidence = minConfidence;
    }

    public List<String> getTrustedSources() {
        return trustedSources;
    }

    public void setTrustedSources(List<String> trustedSources) {
        this.trustedSources = trustedSources;
    }

    public Map<String, Object> getExtractionHints() {
        return extractionHints;
    }

    public void setExtractionHints(Map<String, Object> extractionHints) {
        this.extractionHints = extractionHints;
    }

    public BackendFieldMapping getBackendMapping() {
        return backendMapping;
    }

    public void setBackendMapping(BackendFieldMapping backendMapping) {
        this.backendMapping = backendMapping;
    }

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }
}
