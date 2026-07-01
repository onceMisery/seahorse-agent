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

import java.util.List;

public record OpenApiSpecDocument(String title,
                                  String description,
                                  String baseUrl,
                                  List<OpenApiSpecOperation> operations) {

    public OpenApiSpecDocument {
        title = Connector.trimToNull(title);
        description = Connector.trimToNull(description);
        baseUrl = Connector.trimToNull(baseUrl);
        operations = operations == null ? List.of() : List.copyOf(operations);
    }

    public OpenApiSpecDocument(String title,
                               String description,
                               List<OpenApiSpecOperation> operations) {
        this(title, description, null, operations);
    }
}
