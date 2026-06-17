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

package com.miracle.ai.seahorse.agent.adapters.vector.milvus;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillVectorIndex;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SkillVectorIndexRepositoryPort;
import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Milvus 实现的 Skill 向量索引仓储。
 *
 * <p>Collection 结构：
 * <ul>
 *   <li>id (VARCHAR): 主键，格式为 "{tenantId}:{skillName}"</li>
 *   <li>tenant_id (VARCHAR): 租户 ID</li>
 *   <li>skill_name (VARCHAR): Skill 名称</li>
 *   <li>revision_id (VARCHAR): Revision ID</li>
 *   <li>content (VARCHAR): Skill 文本内容</li>
 *   <li>embedding (FloatVector): 语义向量</li>
 *   <li>timestamp (INT64): 时间戳</li>
 * </ul>
 */
public class MilvusSkillVectorIndexAdapter implements SkillVectorIndexRepositoryPort {

    private static final Logger LOG = LoggerFactory.getLogger(MilvusSkillVectorIndexAdapter.class);

    private static final String COLLECTION_NAME = "seahorse_skill_vectors";
    private static final String FIELD_ID = "id";
    private static final String FIELD_TENANT_ID = "tenant_id";
    private static final String FIELD_SKILL_NAME = "skill_name";
    private static final String FIELD_REVISION_ID = "revision_id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_EMBEDDING = "embedding";
    private static final String FIELD_TIMESTAMP = "timestamp";

    private final MilvusClientV2 milvusClient;

    public MilvusSkillVectorIndexAdapter(MilvusClientV2 milvusClient) {
        this.milvusClient = Objects.requireNonNull(milvusClient, "milvusClient must not be null");
    }

    @Override
    public void save(SkillVectorIndex index) {
        saveBatch(List.of(index));
    }

    @Override
    public void saveBatch(List<SkillVectorIndex> indices) {
        if (indices == null || indices.isEmpty()) {
            return;
        }

        List<JsonObject> rows = new ArrayList<>();

        for (SkillVectorIndex index : indices) {
            JsonObject row = new JsonObject();
            row.addProperty(FIELD_ID, buildId(index.tenantId(), index.skillName()));
            row.addProperty(FIELD_TENANT_ID, index.tenantId());
            row.addProperty(FIELD_SKILL_NAME, index.skillName());
            row.addProperty(FIELD_REVISION_ID, index.revisionId());
            row.addProperty(FIELD_CONTENT, truncateContent(index.content()));
            row.addProperty(FIELD_TIMESTAMP, index.timestamp());

            // 添加向量字段
            com.google.gson.JsonArray vectorArray = new com.google.gson.JsonArray(index.embedding().length);
            for (float value : index.embedding()) {
                vectorArray.add(value);
            }
            row.add(FIELD_EMBEDDING, vectorArray);

            rows.add(row);
        }

        UpsertReq upsertReq = UpsertReq.builder()
                .collectionName(COLLECTION_NAME)
                .data(rows)
                .build();

        milvusClient.upsert(upsertReq);

        LOG.debug("Upserted {} skill vector indices to Milvus", indices.size());
    }

    @Override
    public Optional<SkillVectorIndex> findBySkillName(String tenantId, String skillName) {
        // Milvus 不直接支持按 ID 查询，使用向量搜索的替代方案
        // 这里返回 empty，因为主要用途是向量搜索而非精确查找
        return Optional.empty();
    }

    @Override
    public List<SkillSearchResult> searchSimilar(String tenantId, float[] queryVector, int topK) {
        if (queryVector == null || queryVector.length == 0) {
            LOG.warn("Empty query vector, returning empty results");
            return List.of();
        }

        SearchReq searchReq = SearchReq.builder()
                .collectionName(COLLECTION_NAME)
                .annsField(FIELD_EMBEDDING)
                .data(List.of(new FloatVec(queryVector)))
                .topK(topK)
                .filter(FIELD_TENANT_ID + " == \"" + tenantId + "\"")
                .outputFields(List.of(FIELD_SKILL_NAME, FIELD_REVISION_ID, FIELD_CONTENT))
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .build();

        SearchResp searchResp = milvusClient.search(searchReq);

        if (searchResp == null || searchResp.getSearchResults() == null || searchResp.getSearchResults().isEmpty()) {
            return List.of();
        }

        List<SkillSearchResult> results = new ArrayList<>();

        for (List<SearchResp.SearchResult> resultList : searchResp.getSearchResults()) {
            for (SearchResp.SearchResult result : resultList) {
                Map<String, Object> entity = result.getEntity();

                String skillName = (String) entity.get(FIELD_SKILL_NAME);
                String revisionId = (String) entity.get(FIELD_REVISION_ID);
                String content = (String) entity.get(FIELD_CONTENT);
                float score = result.getScore();

                results.add(new SkillSearchResult(skillName, revisionId, score, content));
            }
        }

        LOG.debug("Vector search returned {} results for tenant: {}", results.size(), tenantId);

        return results;
    }

    @Override
    public void delete(String tenantId, String skillName) {
        String id = buildId(tenantId, skillName);

        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(COLLECTION_NAME)
                .filter(FIELD_ID + " == \"" + id + "\"")
                .build();

        milvusClient.delete(deleteReq);

        LOG.debug("Deleted skill vector index: {} (tenant: {})", skillName, tenantId);
    }

    @Override
    public void deleteByTenant(String tenantId) {
        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(COLLECTION_NAME)
                .filter(FIELD_TENANT_ID + " == \"" + tenantId + "\"")
                .build();

        milvusClient.delete(deleteReq);

        LOG.info("Deleted all skill vector indices for tenant: {}", tenantId);
    }

    @Override
    public boolean collectionExists() {
        try {
            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(COLLECTION_NAME)
                    .build();

            return Boolean.TRUE.equals(milvusClient.hasCollection(hasReq));
        } catch (Exception ex) {
            LOG.error("Failed to check collection existence", ex);
            return false;
        }
    }

    @Override
    public void createCollection(int dimension) {
        ensureCollection(dimension);
    }

    @Override
    public boolean ensureCollection(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }

        if (!collectionExists()) {
            createMilvusCollection(dimension);
            return true;
        }

        Integer existingDimension = existingEmbeddingDimension();
        if (existingDimension != null && existingDimension == dimension) {
            LOG.info("Collection {} already exists with dimension: {}", COLLECTION_NAME, dimension);
            return false;
        }

        LOG.warn("Recreating Milvus collection {} because embedding dimension changed from {} to {}",
                COLLECTION_NAME, existingDimension, dimension);
        dropCollection();
        createMilvusCollection(dimension);
        return true;
    }

    private Integer existingEmbeddingDimension() {
        DescribeCollectionReq describeReq = DescribeCollectionReq.builder()
                .collectionName(COLLECTION_NAME)
                .build();
        DescribeCollectionResp response = milvusClient.describeCollection(describeReq);
        if (response == null || response.getCollectionSchema() == null) {
            return null;
        }
        CreateCollectionReq.FieldSchema field = response.getCollectionSchema().getField(FIELD_EMBEDDING);
        return field == null ? null : field.getDimension();
    }

    private void dropCollection() {
        DropCollectionReq dropReq = DropCollectionReq.builder()
                .collectionName(COLLECTION_NAME)
                .async(false)
                .build();
        milvusClient.dropCollection(dropReq);
        LOG.info("Dropped Milvus collection: {}", COLLECTION_NAME);
    }

    private void createMilvusCollection(int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }

        if (collectionExists()) {
            LOG.info("Collection {} already exists, skipping creation", COLLECTION_NAME);
            return;
        }

        List<CreateCollectionReq.FieldSchema> fields = new ArrayList<>();
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_ID)
                .dataType(DataType.VarChar)
                .maxLength(512)
                .isPrimaryKey(true)
                .autoID(false)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_TENANT_ID)
                .dataType(DataType.VarChar)
                .maxLength(128)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_SKILL_NAME)
                .dataType(DataType.VarChar)
                .maxLength(256)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_REVISION_ID)
                .dataType(DataType.VarChar)
                .maxLength(256)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_CONTENT)
                .dataType(DataType.VarChar)
                .maxLength(4096)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_EMBEDDING)
                .dataType(DataType.FloatVector)
                .dimension(dimension)
                .build());
        fields.add(CreateCollectionReq.FieldSchema.builder()
                .name(FIELD_TIMESTAMP)
                .dataType(DataType.Int64)
                .build());

        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                .fieldSchemaList(fields)
                .build();

        IndexParam indexParam = IndexParam.builder()
                .fieldName(FIELD_EMBEDDING)
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build();

        CreateCollectionReq createReq = CreateCollectionReq.builder()
                .collectionName(COLLECTION_NAME)
                .collectionSchema(schema)
                .primaryFieldName(FIELD_ID)
                .vectorFieldName(FIELD_EMBEDDING)
                .indexParams(List.of(indexParam))
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .build();

        milvusClient.createCollection(createReq);

        LOG.info("Created Milvus collection: {} (dimension: {})", COLLECTION_NAME, dimension);
    }

    private String buildId(String tenantId, String skillName) {
        return tenantId + ":" + skillName;
    }

    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        // Milvus VARCHAR 字段限制为 4096
        return content.length() > 4000 ? content.substring(0, 4000) : content;
    }
}
