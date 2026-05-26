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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public interface ToolProviderExposurePolicyPort {

    String PROVIDER_DISABLED_MESSAGE = "Tool provider is disabled in the current product mode";
    Set<ToolProvider> CONSUMER_WEB_ALLOWED_PROVIDERS = Set.copyOf(EnumSet.of(
            ToolProvider.BUILTIN,
            ToolProvider.INTERNAL));

    boolean isProviderAllowed(ToolProvider provider);

    default boolean isToolAllowed(ToolCatalogEntry entry) {
        return entry != null && isProviderAllowed(entry.provider());
    }

    default void requireToolAllowed(ToolCatalogEntry entry) {
        if (!isToolAllowed(entry)) {
            throw new IllegalStateException(PROVIDER_DISABLED_MESSAGE);
        }
    }

    static ToolProviderExposurePolicyPort consumerWebDefaults() {
        return provider -> provider != null && CONSUMER_WEB_ALLOWED_PROVIDERS.contains(provider);
    }

    static ToolProviderExposurePolicyPort allEnabled() {
        return provider -> Objects.nonNull(provider);
    }
}
