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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;

import java.util.List;
import java.util.Optional;

/**
 * Context pack 查询、差异与保留清理入站用例端口。
 *
 * <p>合并原本分散在三个单方法端口（查询、差异、保留清理）中的 Context pack 用例，
 * 由同一个 Kernel 实现提供，供 Web 适配器通过契约依赖。</p>
 */
public interface ContextPackInboundPort {

    Optional<ContextPack> findById(String contextPackId);

    List<ContextItem> listItems(String contextPackId);

    ContextPackDiffResult diff(String leftContextPackId, String rightContextPackId);

    ContextPackRetentionCleanupResult cleanupExpiredItems(String contextPackId);
}
