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

package com.miracle.ai.seahorse.agent.adapters.search.lucene;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lucene 嵌入式关键词索引写入适配器。
 *
 * <p>该实现只接收入库链路已经归一化后的 chunk 快照；动态 metadata 原样存储为 JSON，
 * 并额外生成 Lucene 字段用于已编译过滤表达式下推，不在这里解释用户原始过滤 Map。
 */
public class LuceneKeywordIndexAdapter implements KeywordIndexPort, Closeable {

    private final ObjectMapper objectMapper;
    private final Analyzer analyzer;
    private final Directory directory;
    private final IndexWriter writer;

    public LuceneKeywordIndexAdapter(ObjectMapper objectMapper, LuceneKeywordProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        LuceneKeywordProperties safeProperties = Objects.requireNonNull(properties, "properties must not be null");
        try {
            Files.createDirectories(safeProperties.indexDirectory());
            this.directory = FSDirectory.open(safeProperties.indexDirectory());
            this.analyzer = new StandardAnalyzer();
            this.writer = new IndexWriter(directory, new IndexWriterConfig(analyzer)
                    .setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND));
        } catch (IOException ex) {
            throw new IllegalStateException("failed to open Lucene keyword index", ex);
        }
    }

    @Override
    public void indexDocumentChunks(String kbId, String docId, List<VectorChunk> chunks) {
        if (!hasText(kbId) || !hasText(docId) || chunks == null || chunks.isEmpty()) {
            return;
        }
        try {
            // 文档级重建先清理旧分片，再写入当前快照，避免同一 chunk 多次入库后重复召回。
            writer.deleteDocuments(documentQuery(kbId, docId));
            for (VectorChunk chunk : chunks) {
                if (chunk != null && hasText(chunk.getChunkId())) {
                    writer.addDocument(document(kbId, docId, chunk));
                }
            }
            writer.commit();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to write Lucene keyword index", ex);
        }
    }

    @Override
    public void deleteDocumentChunks(String kbId, String docId) {
        if (!hasText(kbId) || !hasText(docId)) {
            return;
        }
        try {
            writer.deleteDocuments(documentQuery(kbId, docId));
            writer.commit();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to delete Lucene keyword index", ex);
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
        analyzer.close();
        directory.close();
    }

    private BooleanQuery documentQuery(String kbId, String docId) {
        return new BooleanQuery.Builder()
                .add(new TermQuery(new Term(LuceneKeywordFields.KB_ID, kbId)), BooleanClause.Occur.FILTER)
                .add(new TermQuery(new Term(LuceneKeywordFields.DOC_ID, docId)), BooleanClause.Occur.FILTER)
                .build();
    }

    private Document document(String kbId, String docId, VectorChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>(Objects.requireNonNullElse(chunk.getMetadata(), Map.of()));
        Document document = new Document();
        addStoredExact(document, LuceneKeywordFields.CHUNK_ID, chunk.getChunkId());
        addStoredExact(document, LuceneKeywordFields.KB_ID, kbId);
        addStoredExact(document, LuceneKeywordFields.DOC_ID, docId);
        addStoredExact(document, LuceneKeywordFields.CHUNK_INDEX, Objects.toString(chunk.getIndex(), ""));
        document.add(new TextField(LuceneKeywordFields.CONTENT, Objects.requireNonNullElse(chunk.getContent(), ""),
                Field.Store.YES));
        document.add(new StoredField(LuceneKeywordFields.METADATA_JSON, toJson(metadata)));

        addSystemFields(document, metadata);
        addMetadataFields(document, metadata);
        return document;
    }

    private void addSystemFields(Document document, Map<String, Object> metadata) {
        addStoredExact(document, LuceneKeywordFields.TENANT_ID, metadata.get(LuceneKeywordFields.TENANT_ID));
        addStoredExact(document, LuceneKeywordFields.COLLECTION_NAME, metadata.get(LuceneKeywordFields.COLLECTION_NAME));
        addRepeatedExact(document, LuceneKeywordFields.ACL_SUBJECT_IDS,
                firstValue(metadata.get(LuceneKeywordFields.ACL_SUBJECT_IDS),
                        metadata.get(LuceneKeywordFields.ACL_SUBJECTS)));
        addStoredExact(document, LuceneKeywordFields.FILE_TYPE, metadata.get(LuceneKeywordFields.FILE_TYPE));
        addStoredExact(document, LuceneKeywordFields.SOURCE_TYPE, metadata.get(LuceneKeywordFields.SOURCE_TYPE));
        addStoredExact(document, LuceneKeywordFields.CREATED_AT, metadata.get(LuceneKeywordFields.CREATED_AT));
        addStoredExact(document, LuceneKeywordFields.UPDATED_AT, metadata.get(LuceneKeywordFields.UPDATED_AT));
        addStoredExact(document, LuceneKeywordFields.ENABLED,
                Objects.toString(firstValue(metadata.get(LuceneKeywordFields.ENABLED), true)));
    }

    private void addMetadataFields(Document document, Map<String, Object> metadata) {
        metadata.forEach((key, value) -> {
            if (!hasText(key) || value == null) {
                return;
            }
            String exactField = LuceneKeywordFields.METADATA_PREFIX + key;
            addRepeatedExact(document, exactField, value);
            // 兼容既有 ES 风格 searchFieldName=metadata.xxx.keyword 的 Schema 配置。
            addRepeatedExact(document, exactField + ".keyword", value);
            addRepeatedText(document, LuceneKeywordFields.METADATA_TEXT_PREFIX + key, value);
        });
    }

    private void addStoredExact(Document document, String field, Object value) {
        String normalized = normalized(value);
        if (!hasText(normalized)) {
            return;
        }
        document.add(new StringField(field, normalized, Field.Store.YES));
    }

    private void addRepeatedExact(Document document, String field, Object value) {
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addExact(document, field, item));
            return;
        }
        addExact(document, field, value);
    }

    private void addExact(Document document, String field, Object value) {
        String normalized = normalized(value);
        if (hasText(normalized)) {
            document.add(new StringField(field, normalized, Field.Store.NO));
        }
    }

    private void addRepeatedText(Document document, String field, Object value) {
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addText(document, field, item));
            return;
        }
        addText(document, field, value);
    }

    private void addText(Document document, String field, Object value) {
        String normalized = normalized(value);
        if (hasText(normalized)) {
            document.add(new TextField(field, normalized, Field.Store.NO));
        }
    }

    private String normalized(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return Objects.toString(value, "").trim();
    }

    private Object firstValue(Object first, Object second) {
        return first == null ? second : first;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize Lucene keyword document", ex);
        }
    }
}
