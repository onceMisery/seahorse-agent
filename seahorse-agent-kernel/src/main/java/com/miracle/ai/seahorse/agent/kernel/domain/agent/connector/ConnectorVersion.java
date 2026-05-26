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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.connector;

import java.time.Instant;
import java.util.Objects;

public record ConnectorVersion(String connectorVersionId,
                               String connectorId,
                               String specHash,
                               String specJson,
                               String importedBy,
                               Instant importedAt) {

    public ConnectorVersion {
        connectorVersionId = Connector.requireText(connectorVersionId, "connectorVersionId must not be blank");
        connectorId = Connector.requireText(connectorId, "connectorId must not be blank");
        specHash = Connector.requireText(specHash, "specHash must not be blank");
        specJson = Connector.requireText(specJson, "specJson must not be blank");
        importedBy = Connector.requireText(importedBy, "importedBy must not be blank");
        importedAt = Objects.requireNonNull(importedAt, "importedAt must not be null");
    }
}
