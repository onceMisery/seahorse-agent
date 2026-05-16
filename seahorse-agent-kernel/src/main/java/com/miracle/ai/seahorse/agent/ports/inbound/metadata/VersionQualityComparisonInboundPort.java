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

package com.miracle.ai.seahorse.agent.ports.inbound.metadata;

import com.miracle.ai.seahorse.agent.ports.outbound.metadata.VersionQualityComparisonReport;

/**
 * 跨版本质量对比入站端口。
 *
 * <p>用于在同一次管理查询中并列输出元数据治理质量报表与检索评测对比结果，
 * 方便上线前比较 baseline 与候选版本。
 */
public interface VersionQualityComparisonInboundPort {

    VersionQualityComparisonReport compare(VersionQualityComparisonCommand command);
}
