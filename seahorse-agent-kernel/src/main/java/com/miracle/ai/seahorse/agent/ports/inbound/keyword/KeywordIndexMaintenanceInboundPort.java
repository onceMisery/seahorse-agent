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

package com.miracle.ai.seahorse.agent.ports.inbound.keyword;

/**
 * 关键词索引运维入口。
 *
 * <p>用于历史回填、索引失败补偿和 mapping 变更后的重建。重建逻辑放在 kernel 编排层，
 * 具体 Elasticsearch/JDBC/OpenSearch 写入仍由 {@code KeywordIndexPort} 适配器完成。
 */
public interface KeywordIndexMaintenanceInboundPort {

    KeywordIndexRebuildResult rebuildDocument(String docId);

    KeywordIndexRebuildResult rebuildKnowledgeBase(String kbId, int batchSize);
}
