package com.miracle.ai.seahorse.agent.ports.outbound.metadata;

import java.util.List;
import java.util.Optional;

/**
 * Metadata Schema 字段管理仓储端口。
 */
public interface MetadataSchemaManagementRepositoryPort {

    List<MetadataSchemaFieldRecord> listSchemaFields(String tenantId, String knowledgeBaseId);

    Optional<MetadataSchemaFieldRecord> findSchemaField(String fieldId);

    String createSchemaField(MetadataSchemaFieldPayload payload);

    MetadataSchemaFieldRecord updateSchemaField(String fieldId, MetadataSchemaFieldPayload payload);

    boolean deleteSchemaField(String fieldId);

    static MetadataSchemaManagementRepositoryPort empty() {
        return new MetadataSchemaManagementRepositoryPort() {
            @Override
            public List<MetadataSchemaFieldRecord> listSchemaFields(String tenantId, String knowledgeBaseId) {
                return List.of();
            }

            @Override
            public Optional<MetadataSchemaFieldRecord> findSchemaField(String fieldId) {
                return Optional.empty();
            }

            @Override
            public String createSchemaField(MetadataSchemaFieldPayload payload) {
                throw new IllegalStateException("Metadata Schema 管理仓储未配置");
            }

            @Override
            public MetadataSchemaFieldRecord updateSchemaField(String fieldId, MetadataSchemaFieldPayload payload) {
                throw new IllegalArgumentException("Metadata Schema 字段不存在: " + fieldId);
            }

            @Override
            public boolean deleteSchemaField(String fieldId) {
                return false;
            }
        };
    }
}
