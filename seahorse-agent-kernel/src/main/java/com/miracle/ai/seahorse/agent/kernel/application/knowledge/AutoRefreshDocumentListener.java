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

package com.miracle.ai.seahorse.agent.kernel.application.knowledge;

import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentChangeListenerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.DurableTaskQueuePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Automatic document refresh listener.
 */
public class AutoRefreshDocumentListener implements DocumentChangeListenerPort {

    private static final Logger LOG = LoggerFactory.getLogger(AutoRefreshDocumentListener.class);
    private static final String REFRESH_TOPIC = "document-refresh";

    private final KernelDocumentRefreshService refreshService;
    private final DurableTaskQueuePort taskQueue;
    private final long delayMillis;

    public AutoRefreshDocumentListener(KernelDocumentRefreshService refreshService,
                                       DurableTaskQueuePort taskQueue,
                                       long delayMillis) {
        this.refreshService = Objects.requireNonNull(refreshService, "refreshService must not be null");
        this.taskQueue = Objects.requireNonNull(taskQueue, "taskQueue must not be null");
        this.delayMillis = Math.max(0, delayMillis);
    }

    @Override
    public void onDocumentUploaded(Long documentId) {
        if (documentId == null) {
            return;
        }
        LOG.info("Document uploaded (id={}), scheduling refresh in {}ms", documentId, delayMillis);
        scheduleRefresh(documentId);
    }

    @Override
    public void onDocumentUpdated(Long documentId) {
        if (documentId == null) {
            return;
        }
        LOG.info("Document updated (id={}), scheduling refresh in {}ms", documentId, delayMillis);
        scheduleRefresh(documentId);
    }

    @Override
    public void onDocumentDeleted(Long documentId) {
        if (documentId == null) {
            return;
        }
        LOG.info("Document deleted (id={}), removing vectors", documentId);
        try {
            refreshService.deleteDocumentVectors(documentId);
        } catch (Exception e) {
            LOG.error("Failed to delete vectors for document id={}", documentId, e);
        }
    }

    private void scheduleRefresh(Long documentId) {
        try {
            DocumentRefreshTask task = new DocumentRefreshTask(documentId);
            if (delayMillis > 0) {
                taskQueue.send(REFRESH_TOPIC, task, delayMillis);
            } else {
                refreshService.refreshDocument(documentId);
            }
        } catch (Exception e) {
            LOG.error("Failed to schedule document refresh for id={}", documentId, e);
        }
    }

    public static class DocumentRefreshTask {
        private final Long documentId;

        public DocumentRefreshTask(Long documentId) {
            this.documentId = documentId;
        }

        public Long getDocumentId() {
            return documentId;
        }
    }
}
