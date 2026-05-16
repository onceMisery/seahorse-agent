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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SystemRetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldContains;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldEq;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldExists;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldIn;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldNe;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FieldRange;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.FilterAnd;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.MetadataFilterExpr;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchRequest;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于嵌入式 Lucene 的关键词/BM25 检索适配器。
 *
 * <p>Lucene 只作为低优先级可插拔后端；查询侧动态 metadata 仍然只消费
 * {@link KeywordSearchRequest#compiledFilter()} 生成的 AST，不允许绕过 Schema/Filter Compiler。
 */
public class LuceneKeywordSearchAdapter implements KeywordSearchPort, Closeable {

    private final ObjectMapper objectMapper;
    private final LuceneKeywordProperties properties;
    private final Analyzer analyzer;
    private final Directory directory;

    public LuceneKeywordSearchAdapter(ObjectMapper objectMapper, LuceneKeywordProperties properties) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        try {
            Files.createDirectories(properties.indexDirectory());
            this.directory = FSDirectory.open(properties.indexDirectory());
            this.analyzer = new StandardAnalyzer();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to open Lucene keyword index", ex);
        }
    }

    @Override
    public List<RetrievedChunk> search(KeywordSearchRequest request) {
        KeywordSearchRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        if (safeRequest.query().isBlank()) {
            return List.of();
        }
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            Query query = luceneQuery(safeRequest);
            TopDocs topDocs = searcher.search(query, safeRequest.topK());
            StoredFields storedFields = searcher.storedFields();
            List<RetrievedChunk> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                results.add(toRetrievedChunk(storedFields.document(scoreDoc.doc), scoreDoc.score));
            }
            return results;
        } catch (IndexNotFoundException ex) {
            return List.of();
        } catch (IOException | ParseException ex) {
            throw new IllegalStateException("failed to search Lucene keyword index", ex);
        }
    }

    @Override
    public void close() throws IOException {
        analyzer.close();
        directory.close();
    }

    Query luceneQuery(KeywordSearchRequest request) throws ParseException {
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        query.add(contentQuery(request.query()), BooleanClause.Occur.MUST);
        appendSystemFilters(query, request.compiledFilter().sourceFilter().system());
        appendMetadataFilter(query, request.compiledFilter().expression());
        return query.build();
    }

    private Query contentQuery(String queryText) throws ParseException {
        String[] queryFields = properties.queryFields();
        if (queryFields.length == 0) {
            queryFields = new String[] {LuceneKeywordFields.CONTENT};
        }
        MultiFieldQueryParser parser = new MultiFieldQueryParser(queryFields, analyzer, properties.fieldBoosts());
        return parser.parse(QueryParser.escape(queryText.trim()));
    }

    private void appendSystemFilters(BooleanQuery.Builder query, SystemRetrievalFilter filter) {
        if (filter == null || filter.enabledOnly()) {
            query.add(term(LuceneKeywordFields.ENABLED, true), BooleanClause.Occur.FILTER);
        }
        if (filter == null) {
            return;
        }
        appendTerm(query, LuceneKeywordFields.TENANT_ID, filter.tenantId());
        appendTerms(query, LuceneKeywordFields.KB_ID, filter.knowledgeBaseIds());
        appendTerms(query, LuceneKeywordFields.DOC_ID, filter.documentIds());
        appendTerms(query, LuceneKeywordFields.COLLECTION_NAME, filter.collectionNames());
        appendTerms(query, LuceneKeywordFields.ACL_SUBJECT_IDS, filter.aclSubjectIds());
        appendTerms(query, LuceneKeywordFields.FILE_TYPE, filter.fileTypes());
        appendTerms(query, LuceneKeywordFields.SOURCE_TYPE, filter.sourceTypes());
        appendRange(query, LuceneKeywordFields.CREATED_AT, filter.createdFrom(), filter.createdTo());
        appendRange(query, LuceneKeywordFields.UPDATED_AT, filter.updatedFrom(), filter.updatedTo());
    }

    private void appendMetadataFilter(BooleanQuery.Builder query, MetadataFilterExpr expression) throws ParseException {
        if (expression == null) {
            return;
        }
        if (expression instanceof FilterAnd and) {
            for (MetadataFilterExpr child : and.children()) {
                appendMetadataFilter(query, child);
            }
            return;
        }
        // 这里只处理 Filter Compiler 输出的 AST，避免 adapter 重新解释不可信动态 Map。
        if (expression instanceof FieldEq eq) {
            query.add(term(searchField(eq.field()), eq.value()), BooleanClause.Occur.FILTER);
        } else if (expression instanceof FieldNe ne) {
            query.add(notTerm(searchField(ne.field()), ne.value()), BooleanClause.Occur.FILTER);
        } else if (expression instanceof FieldIn in) {
            appendTerms(query, searchField(in.field()), in.values());
        } else if (expression instanceof FieldRange range) {
            appendRange(query, searchField(range.field()), range.from(), range.to());
        } else if (expression instanceof FieldContains contains) {
            Query containsQuery = containsQuery(textSearchField(contains.field()), contains.value());
            if (containsQuery != null) {
                query.add(containsQuery, BooleanClause.Occur.FILTER);
            }
        } else if (expression instanceof FieldExists exists) {
            query.add(existsQuery(searchField(exists.field())), BooleanClause.Occur.FILTER);
        }
    }

    private void appendTerm(BooleanQuery.Builder query, String field, Object value) {
        String normalized = normalized(value);
        if (hasText(normalized)) {
            query.add(term(field, normalized), BooleanClause.Occur.FILTER);
        }
    }

    private void appendTerms(BooleanQuery.Builder query, String field, Collection<?> values) {
        Query termsQuery = termsQuery(field, values);
        if (termsQuery != null) {
            query.add(termsQuery, BooleanClause.Occur.FILTER);
        }
    }

    private Query termsQuery(String field, Collection<?> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<String> normalizedValues = values.stream()
                .map(this::normalized)
                .filter(this::hasText)
                .distinct()
                .toList();
        if (normalizedValues.isEmpty()) {
            return null;
        }
        if (normalizedValues.size() == 1) {
            return term(field, normalizedValues.get(0));
        }
        BooleanQuery.Builder query = new BooleanQuery.Builder();
        normalizedValues.forEach(value -> query.add(term(field, value), BooleanClause.Occur.SHOULD));
        query.setMinimumNumberShouldMatch(1);
        return query.build();
    }

    private void appendRange(BooleanQuery.Builder query, String field, Object from, Object to) {
        Query range = rangeQuery(field, from, to);
        if (range != null) {
            query.add(range, BooleanClause.Occur.FILTER);
        }
    }

    private Query rangeQuery(String field, Object from, Object to) {
        String lower = normalized(from);
        String upper = normalized(to);
        if (!hasText(lower) && !hasText(upper)) {
            return null;
        }
        return TermRangeQuery.newStringRange(field, hasText(lower) ? lower : null,
                hasText(upper) ? upper : null, true, true);
    }

    private Query containsQuery(String field, Object value) throws ParseException {
        String normalized = normalized(value);
        if (!hasText(normalized)) {
            return null;
        }
        return new QueryParser(field, analyzer).parse(QueryParser.escape(normalized));
    }

    private Query existsQuery(String field) {
        return TermRangeQuery.newStringRange(field, null, null, true, true);
    }

    private Query term(String field, Object value) {
        return new TermQuery(new Term(field, normalized(value)));
    }

    private Query notTerm(String field, Object value) {
        return new BooleanQuery.Builder()
                .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST)
                .add(term(field, value), BooleanClause.Occur.MUST_NOT)
                .build();
    }

    private String searchField(MetadataFieldDescriptor field) {
        String mapped = field.backendMapping().searchFieldName();
        if (hasText(mapped) && !mapped.equals(field.fieldKey())) {
            if (mapped.startsWith(LuceneKeywordFields.METADATA_PREFIX)) {
                return mapped;
            }
            return mapped.contains(".") ? mapped : LuceneKeywordFields.METADATA_PREFIX + mapped;
        }
        return LuceneKeywordFields.METADATA_PREFIX + field.fieldKey();
    }

    private String textSearchField(MetadataFieldDescriptor field) {
        String exactField = searchField(field);
        if (!exactField.startsWith(LuceneKeywordFields.METADATA_PREFIX)) {
            return exactField;
        }
        String key = exactField.substring(LuceneKeywordFields.METADATA_PREFIX.length());
        if (key.endsWith(".keyword")) {
            key = key.substring(0, key.length() - ".keyword".length());
        }
        return LuceneKeywordFields.METADATA_TEXT_PREFIX + key;
    }

    private RetrievedChunk toRetrievedChunk(Document document, float score) {
        Map<String, Object> metadata = metadata(document.get(LuceneKeywordFields.METADATA_JSON));
        String kbId = document.get(LuceneKeywordFields.KB_ID);
        String docId = document.get(LuceneKeywordFields.DOC_ID);
        Integer chunkIndex = integer(document.get(LuceneKeywordFields.CHUNK_INDEX));
        metadata.putIfAbsent(LuceneKeywordFields.KB_ID, kbId);
        metadata.putIfAbsent(LuceneKeywordFields.DOC_ID, docId);
        metadata.putIfAbsent(LuceneKeywordFields.CHUNK_INDEX, chunkIndex);
        putIfPresent(metadata, LuceneKeywordFields.TENANT_ID, document.get(LuceneKeywordFields.TENANT_ID));
        putIfPresent(metadata, LuceneKeywordFields.COLLECTION_NAME, document.get(LuceneKeywordFields.COLLECTION_NAME));
        putIfPresent(metadata, LuceneKeywordFields.FILE_TYPE, document.get(LuceneKeywordFields.FILE_TYPE));
        putIfPresent(metadata, LuceneKeywordFields.SOURCE_TYPE, document.get(LuceneKeywordFields.SOURCE_TYPE));
        putIfPresent(metadata, LuceneKeywordFields.CREATED_AT, document.get(LuceneKeywordFields.CREATED_AT));
        putIfPresent(metadata, LuceneKeywordFields.UPDATED_AT, document.get(LuceneKeywordFields.UPDATED_AT));
        metadata.putIfAbsent(LuceneKeywordFields.ENABLED, Boolean.parseBoolean(document.get(LuceneKeywordFields.ENABLED)));
        return RetrievedChunk.builder()
                .id(document.get(LuceneKeywordFields.CHUNK_ID))
                .text(document.get(LuceneKeywordFields.CONTENT))
                .score(score)
                .tenantId(document.get(LuceneKeywordFields.TENANT_ID))
                .kbId(kbId)
                .docId(docId)
                .collectionName(document.get(LuceneKeywordFields.COLLECTION_NAME))
                .chunkIndex(chunkIndex)
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> metadata(String metadataJson) {
        if (!hasText(metadataJson)) {
            return new LinkedHashMap<>();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {
            }));
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null && !metadata.containsKey(key)) {
            metadata.put(key, value);
        }
    }

    private Integer integer(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
