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

/**
 * Port for listening to knowledge document lifecycle events.
 *
 * <p>Implementations can trigger side effects such as:
 * <ul>
 *   <li>Automatic chunking and vectorization after document upload</li>
 *   <li>Cache invalidation after document update</li>
 *   <li>Vector deletion after document removal</li>
 * </ul>
 */
public interface DocumentChangeListenerPort {

    /**
     * Called after a new document is uploaded.
     *
     * @param documentId the ID of the uploaded document
     */
    void onDocumentUploaded(Long documentId);

    /**
     * Called after an existing document is updated.
     *
     * @param documentId the ID of the updated document
     */
    void onDocumentUpdated(Long documentId);

    /**
     * Called after a document is deleted.
     *
     * @param documentId the ID of the deleted document
     */
    void onDocumentDeleted(Long documentId);

    /**
     * No-op implementation for optional use.
     */
    static DocumentChangeListenerPort noop() {
        return new DocumentChangeListenerPort() {
            @Override
            public void onDocumentUploaded(Long documentId) {
                // no-op
            }

            @Override
            public void onDocumentUpdated(Long documentId) {
                // no-op
            }

            @Override
            public void onDocumentDeleted(Long documentId) {
                // no-op
            }
        };
    }
}
