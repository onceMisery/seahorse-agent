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

package com.miracle.ai.seahorse.agent.ports.outbound.knowledge;

import java.time.Instant;

/**
 * 文档分块执行日志。
 */
public class KnowledgeDocumentChunkLogRecord {

    private Long id;
    private Long docId;
    private String status;
    private String processMode;
    private String chunkStrategy;
    private String pipelineId;
    private String pipelineName;
    private Long extractDuration;
    private Long chunkDuration;
    private Long embedDuration;
    private Long persistDuration;
    private Long otherDuration;
    private Long totalDuration;
    private Integer chunkCount;
    private String errorMessage;
    private Instant startTime;
    private Instant endTime;
    private Instant createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getProcessMode() {
        return processMode;
    }

    public void setProcessMode(String processMode) {
        this.processMode = processMode;
    }

    public String getChunkStrategy() {
        return chunkStrategy;
    }

    public void setChunkStrategy(String chunkStrategy) {
        this.chunkStrategy = chunkStrategy;
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public void setPipelineId(String pipelineId) {
        this.pipelineId = pipelineId;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public void setPipelineName(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public Long getExtractDuration() {
        return extractDuration;
    }

    public void setExtractDuration(Long extractDuration) {
        this.extractDuration = extractDuration;
    }

    public Long getChunkDuration() {
        return chunkDuration;
    }

    public void setChunkDuration(Long chunkDuration) {
        this.chunkDuration = chunkDuration;
    }

    public Long getEmbedDuration() {
        return embedDuration;
    }

    public void setEmbedDuration(Long embedDuration) {
        this.embedDuration = embedDuration;
    }

    public Long getPersistDuration() {
        return persistDuration;
    }

    public void setPersistDuration(Long persistDuration) {
        this.persistDuration = persistDuration;
    }

    public Long getOtherDuration() {
        return otherDuration;
    }

    public void setOtherDuration(Long otherDuration) {
        this.otherDuration = otherDuration;
    }

    public Long getTotalDuration() {
        return totalDuration;
    }

    public void setTotalDuration(Long totalDuration) {
        this.totalDuration = totalDuration;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public Instant getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Instant createTime) {
        this.createTime = createTime;
    }
}
