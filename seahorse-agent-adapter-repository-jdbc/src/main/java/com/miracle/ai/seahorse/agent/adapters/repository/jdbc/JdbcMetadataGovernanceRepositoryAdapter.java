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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillOperationsOverview;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRetry;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewAuditRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldCapabilityRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncStatusRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcMetadataGovernanceRepositoryAdapter implements MetadataSchemaRegistryPort,
        MetadataDictionaryPort, MetadataExtractionResultRepositoryPort, MetadataReviewQueuePort,
        MetadataQuarantinePort, MetadataCanonicalWritePort, MetadataBackfillJobRepositoryPort,
        MetadataQualityReportRepositoryPort, MetadataReviewManagementRepositoryPort,
        MetadataQuarantineManagementRepositoryPort, MetadataSchemaManagementRepositoryPort,
        MetadataDictionaryManagementRepositoryPort, MetadataExtractionResultManagementRepositoryPort,
        MetadataSchemaIndexStatusPort, MetadataSchemaUsageReportRepositoryPort {

    private final JdbcMetadataSchemaRepositoryAdapter schemaRepositoryAdapter;
    private final JdbcMetadataDictionaryRepositoryAdapter dictionaryRepositoryAdapter;
    private final JdbcMetadataExtractionResultRepositoryAdapter extractionResultRepositoryAdapter;
    private final JdbcMetadataReviewRepositoryAdapter reviewRepositoryAdapter;
    private final JdbcMetadataQuarantineRepositoryAdapter quarantineRepositoryAdapter;
    private final JdbcMetadataCanonicalWriteRepositoryAdapter canonicalWriteRepositoryAdapter;
    private final JdbcMetadataBackfillJobRepositoryAdapter backfillJobRepositoryAdapter;
    private final JdbcMetadataQualityReportRepositoryAdapter qualityReportRepositoryAdapter;
    private final JdbcMetadataSchemaUsageReportRepositoryAdapter schemaUsageReportRepositoryAdapter;

    public JdbcMetadataGovernanceRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.schemaRepositoryAdapter = new JdbcMetadataSchemaRepositoryAdapter(dataSource, objectMapper);
        this.dictionaryRepositoryAdapter = new JdbcMetadataDictionaryRepositoryAdapter(dataSource);
        this.extractionResultRepositoryAdapter = new JdbcMetadataExtractionResultRepositoryAdapter(
                dataSource, objectMapper);
        this.reviewRepositoryAdapter = new JdbcMetadataReviewRepositoryAdapter(dataSource, objectMapper);
        this.quarantineRepositoryAdapter = new JdbcMetadataQuarantineRepositoryAdapter(dataSource, objectMapper);
        this.canonicalWriteRepositoryAdapter = new JdbcMetadataCanonicalWriteRepositoryAdapter(
                dataSource, objectMapper);
        this.backfillJobRepositoryAdapter = new JdbcMetadataBackfillJobRepositoryAdapter(dataSource, objectMapper);
        this.qualityReportRepositoryAdapter = new JdbcMetadataQualityReportRepositoryAdapter(
                dataSource, objectMapper, schemaRepositoryAdapter);
        this.schemaUsageReportRepositoryAdapter = new JdbcMetadataSchemaUsageReportRepositoryAdapter(
                dataSource, schemaRepositoryAdapter);
    }

    @Override
    public MetadataSchema loadSchema(String tenantId, String knowledgeBaseId) {
        return schemaRepositoryAdapter.loadSchema(tenantId, knowledgeBaseId);
    }

    @Override
    public List<MetadataSchemaFieldRecord> listSchemaFields(String tenantId, String knowledgeBaseId) {
        return schemaRepositoryAdapter.listSchemaFields(tenantId, knowledgeBaseId);
    }

    @Override
    public List<MetadataSchemaFieldCapabilityRecord> listSchemaFieldCapabilities(String tenantId,
                                                                                 String knowledgeBaseId) {
        return schemaRepositoryAdapter.listSchemaFieldCapabilities(tenantId, knowledgeBaseId);
    }

    @Override
    public Optional<MetadataSchemaFieldRecord> findSchemaField(String fieldId) {
        return schemaRepositoryAdapter.findSchemaField(fieldId);
    }

    @Override
    public String createSchemaField(MetadataSchemaFieldPayload payload) {
        return schemaRepositoryAdapter.createSchemaField(payload);
    }

    @Override
    public MetadataSchemaFieldRecord updateSchemaField(String fieldId, MetadataSchemaFieldPayload payload) {
        return schemaRepositoryAdapter.updateSchemaField(fieldId, payload);
    }

    @Override
    public boolean deleteSchemaField(String fieldId) {
        return schemaRepositoryAdapter.deleteSchemaField(fieldId);
    }

    @Override
    public void recordSyncResult(MetadataSchemaIndexSyncStatusRecord status) {
        schemaRepositoryAdapter.recordSyncResult(status);
    }

    @Override
    public Optional<String> canonicalValue(String tenantId, String dictionaryCode, String rawValue) {
        return dictionaryRepositoryAdapter.canonicalValue(tenantId, dictionaryCode, rawValue);
    }

    @Override
    public List<MetadataDictionaryItemRecord> listDictionaryItems(String tenantId,
                                                                  String dictionaryCode,
                                                                  boolean includeDisabled) {
        return dictionaryRepositoryAdapter.listDictionaryItems(tenantId, dictionaryCode, includeDisabled);
    }

    @Override
    public Optional<MetadataDictionaryItemRecord> findDictionaryItem(String itemId) {
        return dictionaryRepositoryAdapter.findDictionaryItem(itemId);
    }

    @Override
    public String createDictionaryItem(MetadataDictionaryItemPayload payload) {
        return dictionaryRepositoryAdapter.createDictionaryItem(payload);
    }

    @Override
    public MetadataDictionaryItemRecord updateDictionaryItem(String itemId, MetadataDictionaryItemPayload payload) {
        return dictionaryRepositoryAdapter.updateDictionaryItem(itemId, payload);
    }

    @Override
    public boolean disableDictionaryItem(String itemId) {
        return dictionaryRepositoryAdapter.disableDictionaryItem(itemId);
    }

    @Override
    public void save(MetadataExtractionRecord record) {
        extractionResultRepositoryAdapter.save(record);
    }

    @Override
    public String saveAndReturnId(MetadataExtractionRecord record) {
        return extractionResultRepositoryAdapter.saveAndReturnId(record);
    }

    @Override
    public boolean hasAcceptedResult(String tenantId,
                                     Long knowledgeBaseId,
                                     Long documentId,
                                     int schemaVersion,
                                     String extractorVersion) {
        return extractionResultRepositoryAdapter.hasAcceptedResult(tenantId, knowledgeBaseId, documentId,
                schemaVersion, extractorVersion);
    }

    @Override
    public MetadataExtractionResultPage pageExtractionResults(MetadataExtractionResultQuery query) {
        return extractionResultRepositoryAdapter.pageExtractionResults(query);
    }

    @Override
    public Optional<MetadataExtractionResultRecord> findExtractionResult(String resultId) {
        return extractionResultRepositoryAdapter.findExtractionResult(resultId);
    }

    @Override
    public void enqueue(MetadataReviewItem item) {
        reviewRepositoryAdapter.enqueue(item);
    }

    @Override
    public MetadataReviewPage pageReviewItems(MetadataReviewQuery query) {
        return reviewRepositoryAdapter.pageReviewItems(query);
    }

    @Override
    public Optional<MetadataReviewRecord> findReviewItem(String itemId) {
        return reviewRepositoryAdapter.findReviewItem(itemId);
    }

    @Override
    public List<MetadataReviewAuditRecord> listReviewAudits(String itemId) {
        return reviewRepositoryAdapter.listReviewAudits(itemId);
    }

    @Override
    public MetadataReviewRecord applyReviewDecision(MetadataReviewDecision decision) {
        return reviewRepositoryAdapter.applyReviewDecision(decision);
    }

    @Override
    public void quarantine(MetadataQuarantineItem item) {
        quarantineRepositoryAdapter.quarantine(item);
    }

    @Override
    public MetadataQuarantinePage pageQuarantineItems(MetadataQuarantineQuery query) {
        return quarantineRepositoryAdapter.pageQuarantineItems(query);
    }

    @Override
    public Optional<MetadataQuarantineRecord> findQuarantineItem(String itemId) {
        return quarantineRepositoryAdapter.findQuarantineItem(itemId);
    }

    @Override
    public MetadataQuarantineRecord resolveQuarantineItem(MetadataQuarantineResolution resolution) {
        return quarantineRepositoryAdapter.resolveQuarantineItem(resolution);
    }

    @Override
    public MetadataQuarantineRecord scheduleQuarantineRetry(MetadataQuarantineRetry retry) {
        return quarantineRepositoryAdapter.scheduleQuarantineRetry(retry);
    }

    @Override
    public void writeDocumentMetadata(String documentId, Map<String, Object> acceptedMetadata) {
        canonicalWriteRepositoryAdapter.writeDocumentMetadata(documentId, acceptedMetadata);
    }

    @Override
    public String create(MetadataBackfillJobRecord job) {
        return backfillJobRepositoryAdapter.create(job);
    }

    @Override
    public Optional<MetadataBackfillJobRecord> findById(String jobId) {
        return backfillJobRepositoryAdapter.findById(jobId);
    }

    @Override
    public MetadataBackfillJobPage page(MetadataBackfillJobQuery query) {
        return backfillJobRepositoryAdapter.page(query);
    }

    @Override
    public MetadataBackfillOperationsOverview overview(String tenantId, String knowledgeBaseId) {
        return backfillJobRepositoryAdapter.overview(tenantId, knowledgeBaseId);
    }

    @Override
    public void save(MetadataBackfillJobRecord job) {
        backfillJobRepositoryAdapter.save(job);
    }

    @Override
    public MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN) {
        return qualityReportRepositoryAdapter.report(tenantId, knowledgeBaseId, quarantineTopN);
    }

    @Override
    public MetadataQualityReport report(String tenantId,
                                        String knowledgeBaseId,
                                        int quarantineTopN,
                                        Integer schemaVersion,
                                        String extractorVersion) {
        return qualityReportRepositoryAdapter.report(tenantId, knowledgeBaseId, quarantineTopN, schemaVersion,
                extractorVersion);
    }

    @Override
    public MetadataQualityReport report(String tenantId,
                                        String knowledgeBaseId,
                                        int quarantineTopN,
                                        Integer schemaVersion,
                                        String extractorVersion,
                                        String llmPromptVersion) {
        return qualityReportRepositoryAdapter.report(tenantId, knowledgeBaseId, quarantineTopN, schemaVersion,
                extractorVersion, llmPromptVersion);
    }

    @Override
    public void recordCompiled(String tenantId,
                               String knowledgeBaseId,
                               Integer schemaVersion,
                               List<String> fieldKeys,
                               List<String> guardOnlyFieldKeys) {
        schemaUsageReportRepositoryAdapter.recordCompiled(tenantId, knowledgeBaseId, schemaVersion, fieldKeys,
                guardOnlyFieldKeys);
    }

    @Override
    public void recordRejected(String tenantId,
                               String knowledgeBaseId,
                               Integer schemaVersion,
                               List<String> fieldKeys,
                               String rejectReason) {
        schemaUsageReportRepositoryAdapter.recordRejected(tenantId, knowledgeBaseId, schemaVersion, fieldKeys,
                rejectReason);
    }

    @Override
    public MetadataSchemaUsageReport report(String tenantId, String knowledgeBaseId) {
        return schemaUsageReportRepositoryAdapter.report(tenantId, knowledgeBaseId);
    }

    @Override
    public MetadataSchemaUsageReport report(String tenantId, String knowledgeBaseId, Integer schemaVersion) {
        return schemaUsageReportRepositoryAdapter.report(tenantId, knowledgeBaseId, schemaVersion);
    }
}
