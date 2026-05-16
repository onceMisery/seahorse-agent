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

final class LuceneKeywordFields {

    static final String CHUNK_ID = "chunk_id";
    static final String KB_ID = "kb_id";
    static final String DOC_ID = "doc_id";
    static final String CHUNK_INDEX = "chunk_index";
    static final String CONTENT = "content";
    static final String METADATA_JSON = "metadata_json";
    static final String TENANT_ID = "tenant_id";
    static final String COLLECTION_NAME = "collection_name";
    static final String ACL_SUBJECTS = "acl_subjects";
    static final String ACL_SUBJECT_IDS = "acl_subject_ids";
    static final String FILE_TYPE = "file_type";
    static final String SOURCE_TYPE = "source_type";
    static final String CREATED_AT = "created_at";
    static final String UPDATED_AT = "updated_at";
    static final String ENABLED = "enabled";
    static final String METADATA_PREFIX = "metadata.";
    static final String METADATA_TEXT_PREFIX = "metadata_text.";

    private LuceneKeywordFields() {
    }
}
