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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Granular metadata port adapters backed by the compatibility JDBC facade.
 */
public final class JdbcMetadataPortAdapters {

    private JdbcMetadataPortAdapters() {
    }

    public static MetadataSchemaRegistryPort schemaRegistry(JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new SchemaRegistryAdapter(delegate);
    }

    public static MetadataSchemaManagementRepositoryPort schemaManagement(
            JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new SchemaManagementAdapter(delegate);
    }

    public static MetadataSchemaIndexStatusPort schemaIndexStatus(JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new SchemaIndexStatusAdapter(delegate);
    }

    public static MetadataDictionaryPort dictionary(JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new DictionaryAdapter(delegate);
    }

    public static MetadataDictionaryManagementRepositoryPort dictionaryManagement(
            JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new DictionaryManagementAdapter(delegate);
    }

    public static MetadataExtractionResultRepositoryPort extractionResult(
            JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new ExtractionResultAdapter(delegate);
    }

    public static MetadataExtractionResultManagementRepositoryPort extractionResultManagement(
            JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new ExtractionResultManagementAdapter(delegate);
    }

    public static MetadataReviewQueuePort reviewQueue(JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new ReviewQueueAdapter(delegate);
    }

    public static MetadataReviewManagementRepositoryPort reviewManagement(
            JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new ReviewManagementAdapter(delegate);
    }

    public static MetadataQuarantinePort quarantine(JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new QuarantineAdapter(delegate);
    }

    public static MetadataQuarantineManagementRepositoryPort quarantineManagement(
            JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new QuarantineManagementAdapter(delegate);
    }

    public static MetadataCanonicalWritePort canonicalWrite(JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new CanonicalWriteAdapter(delegate);
    }

    public static MetadataBackfillJobRepositoryPort backfillJob(JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new BackfillJobAdapter(delegate);
    }

    public static MetadataQualityReportRepositoryPort qualityReport(JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new QualityReportAdapter(delegate);
    }

    public static MetadataSchemaUsageReportRepositoryPort schemaUsageReport(
            JdbcMetadataGovernanceRepositoryAdapter delegate) {
        return new SchemaUsageReportAdapter(delegate);
    }

    private abstract static class DelegateAdapter {

        protected final JdbcMetadataGovernanceRepositoryAdapter delegate;

        DelegateAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        }
    }

    private static final class SchemaRegistryAdapter extends DelegateAdapter implements MetadataSchemaRegistryPort {

        SchemaRegistryAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public MetadataSchema loadSchema(String tenantId, String knowledgeBaseId) {
            return delegate.loadSchema(tenantId, knowledgeBaseId);
        }
    }

    private static final class SchemaManagementAdapter extends DelegateAdapter
            implements MetadataSchemaManagementRepositoryPort {

        SchemaManagementAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public List<MetadataSchemaFieldRecord> listSchemaFields(String tenantId, String knowledgeBaseId) {
            return delegate.listSchemaFields(tenantId, knowledgeBaseId);
        }

        @Override
        public List<MetadataSchemaFieldCapabilityRecord> listSchemaFieldCapabilities(String tenantId,
                                                                                     String knowledgeBaseId) {
            return delegate.listSchemaFieldCapabilities(tenantId, knowledgeBaseId);
        }

        @Override
        public Optional<MetadataSchemaFieldRecord> findSchemaField(String fieldId) {
            return delegate.findSchemaField(fieldId);
        }

        @Override
        public String createSchemaField(MetadataSchemaFieldPayload payload) {
            return delegate.createSchemaField(payload);
        }

        @Override
        public MetadataSchemaFieldRecord updateSchemaField(String fieldId, MetadataSchemaFieldPayload payload) {
            return delegate.updateSchemaField(fieldId, payload);
        }

        @Override
        public boolean deleteSchemaField(String fieldId) {
            return delegate.deleteSchemaField(fieldId);
        }
    }

    private static final class SchemaIndexStatusAdapter extends DelegateAdapter
            implements MetadataSchemaIndexStatusPort {

        SchemaIndexStatusAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public void recordSyncResult(MetadataSchemaIndexSyncStatusRecord status) {
            delegate.recordSyncResult(status);
        }
    }

    private static final class DictionaryAdapter extends DelegateAdapter implements MetadataDictionaryPort {

        DictionaryAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public Optional<String> canonicalValue(String tenantId, String dictionaryCode, String rawValue) {
            return delegate.canonicalValue(tenantId, dictionaryCode, rawValue);
        }
    }

    private static final class DictionaryManagementAdapter extends DelegateAdapter
            implements MetadataDictionaryManagementRepositoryPort {

        DictionaryManagementAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public List<MetadataDictionaryItemRecord> listDictionaryItems(String tenantId,
                                                                      String dictionaryCode,
                                                                      boolean includeDisabled) {
            return delegate.listDictionaryItems(tenantId, dictionaryCode, includeDisabled);
        }

        @Override
        public Optional<MetadataDictionaryItemRecord> findDictionaryItem(String itemId) {
            return delegate.findDictionaryItem(itemId);
        }

        @Override
        public String createDictionaryItem(MetadataDictionaryItemPayload payload) {
            return delegate.createDictionaryItem(payload);
        }

        @Override
        public MetadataDictionaryItemRecord updateDictionaryItem(String itemId, MetadataDictionaryItemPayload payload) {
            return delegate.updateDictionaryItem(itemId, payload);
        }

        @Override
        public boolean disableDictionaryItem(String itemId) {
            return delegate.disableDictionaryItem(itemId);
        }
    }

    private static final class ExtractionResultAdapter extends DelegateAdapter
            implements MetadataExtractionResultRepositoryPort {

        ExtractionResultAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public void save(MetadataExtractionRecord record) {
            delegate.save(record);
        }

        @Override
        public String saveAndReturnId(MetadataExtractionRecord record) {
            return delegate.saveAndReturnId(record);
        }

        @Override
        public boolean hasAcceptedResult(String tenantId,
                                         Long knowledgeBaseId,
                                         Long documentId,
                                         int schemaVersion,
                                         String extractorVersion) {
            return delegate.hasAcceptedResult(tenantId, knowledgeBaseId, documentId, schemaVersion, extractorVersion);
        }
    }

    private static final class ExtractionResultManagementAdapter extends DelegateAdapter
            implements MetadataExtractionResultManagementRepositoryPort {

        ExtractionResultManagementAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public MetadataExtractionResultPage pageExtractionResults(MetadataExtractionResultQuery query) {
            return delegate.pageExtractionResults(query);
        }

        @Override
        public Optional<MetadataExtractionResultRecord> findExtractionResult(String resultId) {
            return delegate.findExtractionResult(resultId);
        }
    }

    private static final class ReviewQueueAdapter extends DelegateAdapter implements MetadataReviewQueuePort {

        ReviewQueueAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public void enqueue(MetadataReviewItem item) {
            delegate.enqueue(item);
        }
    }

    private static final class ReviewManagementAdapter extends DelegateAdapter
            implements MetadataReviewManagementRepositoryPort {

        ReviewManagementAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public MetadataReviewPage pageReviewItems(MetadataReviewQuery query) {
            return delegate.pageReviewItems(query);
        }

        @Override
        public Optional<MetadataReviewRecord> findReviewItem(String itemId) {
            return delegate.findReviewItem(itemId);
        }

        @Override
        public List<MetadataReviewAuditRecord> listReviewAudits(String itemId) {
            return delegate.listReviewAudits(itemId);
        }

        @Override
        public MetadataReviewRecord applyReviewDecision(MetadataReviewDecision decision) {
            return delegate.applyReviewDecision(decision);
        }
    }

    private static final class QuarantineAdapter extends DelegateAdapter implements MetadataQuarantinePort {

        QuarantineAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public void quarantine(MetadataQuarantineItem item) {
            delegate.quarantine(item);
        }
    }

    private static final class QuarantineManagementAdapter extends DelegateAdapter
            implements MetadataQuarantineManagementRepositoryPort {

        QuarantineManagementAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public MetadataQuarantinePage pageQuarantineItems(MetadataQuarantineQuery query) {
            return delegate.pageQuarantineItems(query);
        }

        @Override
        public Optional<MetadataQuarantineRecord> findQuarantineItem(String itemId) {
            return delegate.findQuarantineItem(itemId);
        }

        @Override
        public MetadataQuarantineRecord resolveQuarantineItem(MetadataQuarantineResolution resolution) {
            return delegate.resolveQuarantineItem(resolution);
        }

        @Override
        public MetadataQuarantineRecord scheduleQuarantineRetry(MetadataQuarantineRetry retry) {
            return delegate.scheduleQuarantineRetry(retry);
        }
    }

    private static final class CanonicalWriteAdapter extends DelegateAdapter implements MetadataCanonicalWritePort {

        CanonicalWriteAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public void writeDocumentMetadata(String documentId, Map<String, Object> acceptedMetadata) {
            delegate.writeDocumentMetadata(documentId, acceptedMetadata);
        }
    }

    private static final class BackfillJobAdapter extends DelegateAdapter implements MetadataBackfillJobRepositoryPort {

        BackfillJobAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public String create(MetadataBackfillJobRecord job) {
            return delegate.create(job);
        }

        @Override
        public Optional<MetadataBackfillJobRecord> findById(String jobId) {
            return delegate.findById(jobId);
        }

        @Override
        public MetadataBackfillJobPage page(MetadataBackfillJobQuery query) {
            return delegate.page(query);
        }

        @Override
        public MetadataBackfillOperationsOverview overview(String tenantId, String knowledgeBaseId) {
            return delegate.overview(tenantId, knowledgeBaseId);
        }

        @Override
        public void save(MetadataBackfillJobRecord job) {
            delegate.save(job);
        }
    }

    private static final class QualityReportAdapter extends DelegateAdapter
            implements MetadataQualityReportRepositoryPort {

        QualityReportAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN) {
            return delegate.report(tenantId, knowledgeBaseId, quarantineTopN);
        }

        @Override
        public MetadataQualityReport report(String tenantId,
                                            String knowledgeBaseId,
                                            int quarantineTopN,
                                            Integer schemaVersion,
                                            String extractorVersion) {
            return delegate.report(tenantId, knowledgeBaseId, quarantineTopN, schemaVersion, extractorVersion);
        }

        @Override
        public MetadataQualityReport report(String tenantId,
                                            String knowledgeBaseId,
                                            int quarantineTopN,
                                            Integer schemaVersion,
                                            String extractorVersion,
                                            String llmPromptVersion) {
            return delegate.report(tenantId, knowledgeBaseId, quarantineTopN, schemaVersion, extractorVersion,
                    llmPromptVersion);
        }
    }

    private static final class SchemaUsageReportAdapter extends DelegateAdapter
            implements MetadataSchemaUsageReportRepositoryPort {

        SchemaUsageReportAdapter(JdbcMetadataGovernanceRepositoryAdapter delegate) {
            super(delegate);
        }

        @Override
        public void recordCompiled(String tenantId,
                                   String knowledgeBaseId,
                                   Integer schemaVersion,
                                   List<String> fieldKeys,
                                   List<String> guardOnlyFieldKeys) {
            delegate.recordCompiled(tenantId, knowledgeBaseId, schemaVersion, fieldKeys, guardOnlyFieldKeys);
        }

        @Override
        public void recordRejected(String tenantId,
                                   String knowledgeBaseId,
                                   Integer schemaVersion,
                                   List<String> fieldKeys,
                                   String rejectReason) {
            delegate.recordRejected(tenantId, knowledgeBaseId, schemaVersion, fieldKeys, rejectReason);
        }

        @Override
        public MetadataSchemaUsageReport report(String tenantId, String knowledgeBaseId) {
            return delegate.report(tenantId, knowledgeBaseId);
        }

        @Override
        public MetadataSchemaUsageReport report(String tenantId, String knowledgeBaseId, Integer schemaVersion) {
            return delegate.report(tenantId, knowledgeBaseId, schemaVersion);
        }
    }
}
