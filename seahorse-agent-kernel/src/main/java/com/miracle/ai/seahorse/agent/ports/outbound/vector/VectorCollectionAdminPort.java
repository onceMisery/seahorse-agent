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

package com.miracle.ai.seahorse.agent.ports.outbound.vector;

/**
 * 向量集合管理端口。
 * <p>
 * 入库索引节点通过该端口创建或检查集合，不直接依赖 Milvus 或 pgvector 管理 API。
 */
public interface VectorCollectionAdminPort {

    /**
     * 查询集合是否已经存在。
     *
     * @param collectionName 集合名称
     * @return true 表示集合存在，false 表示不存在
     */
    boolean collectionExists(String collectionName);

    /**
     * 确保集合存在。
     *
     * @param collectionName 集合名称
     */
    void ensureCollection(String collectionName);
}
