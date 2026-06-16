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

package com.miracle.ai.seahorse.agent.ports.outbound.ingestion;

import com.miracle.ai.seahorse.agent.kernel.domain.ingestion.NodeLog;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 入库任务记录。
 */
public class IngestionTaskRecord {

    private String id;
    private String pipelineId;
    private int pipelineVersion;
    private Map<String, Object> pipelineSnapshot = Map.of();
    private String sourceType;
    private String sourceLocation;
    private String sourceFileName;
    private String status;
    private int chunkCount;
    private String errorMessage;
    private List<NodeLog> logs = List.of();
    private Map<String, Object> metadata = Map.of();
    private int unresolvedQuarantineCount;
    private boolean hasQuarantineItems;
    private Instant startedAt;
    private Instant completedAt;
    private String createdBy;
    private Instant createTime;
    private Instant updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public int getPipelineVersion() {
        return pipelineVersion;
    }

    public void setPipelineVersion(int pipelineVersion) {
        this.pipelineVersion = Math.max(0, pipelineVersion);
    }

    public Map<String, Object> getPipelineSnapshot() {
        return pipelineSnapshot;
    }

    public void setPipelineSnapshot(Map<String, Object> pipelineSnapshot) {
        this.pipelineSnapshot = new LinkedHashMap<>(Objects.requireNonNullElse(pipelineSnapshot, Map.of()));
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceLocation() {
        return sourceLocation;
    }

    public void setSourceLocation(String sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName) {
        this.sourceFileName = sourceFileName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<NodeLog> getLogs() {
        return logs;
    }

    public void setLogs(List<NodeLog> logs) {
        this.logs = List.copyOf(Objects.requireNonNullElse(logs, List.of()));
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = new LinkedHashMap<>(Objects.requireNonNullElse(metadata, Map.of()));
    }

    public int getUnresolvedQuarantineCount() {
        return unresolvedQuarantineCount;
    }

    public void setUnresolvedQuarantineCount(int unresolvedQuarantineCount) {
        this.unresolvedQuarantineCount = Math.max(0, unresolvedQuarantineCount);
        this.hasQuarantineItems = this.unresolvedQuarantineCount > 0;
    }

    public boolean isHasQuarantineItems() {
        return hasQuarantineItems;
    }

    public void setHasQuarantineItems(boolean hasQuarantineItems) {
        this.hasQuarantineItems = hasQuarantineItems;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }

    public Instant getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Instant updateTime) {
        this.updateTime = updateTime;
    }
}
