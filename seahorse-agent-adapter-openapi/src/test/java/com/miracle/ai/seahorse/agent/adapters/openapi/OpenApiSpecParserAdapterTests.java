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

package com.miracle.ai.seahorse.agent.adapters.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiHttpMethod;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiSpecDocument;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OpenApiSpecParseRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiSpecParserAdapterTests {

    @Test
    void shouldParseMinimalOpenApi3SpecOperations() {
        OpenApiSpecParserAdapter parser = new OpenApiSpecParserAdapter(new ObjectMapper());

        OpenApiSpecDocument document = parser.parse(new OpenApiSpecParseRequest("""
                {
                  "openapi": "3.0.3",
                  "info": {
                    "title": "CRM API",
                    "description": "Customer API"
                  },
                  "servers": [
                    {"url": "https://crm.example.test/api"}
                  ],
                  "paths": {
                    "/customers": {
                      "get": {
                        "operationId": "listCustomers",
                        "summary": "List customers",
                        "tags": ["crm_customer"],
                        "parameters": [
                          {
                            "name": "status",
                            "in": "query",
                            "schema": {"type": "string"}
                          }
                        ],
                        "responses": {
                          "200": {
                            "description": "ok",
                            "content": {
                              "application/json": {
                                "schema": {"type": "array"}
                              }
                            }
                          }
                        }
                      }
                    },
                    "/customers/{customerId}": {
                      "delete": {
                        "operationId": "deleteCustomer",
                        "summary": "Delete customer",
                        "x-resource-type": "CRM_CUSTOMER",
                        "requestBody": {
                          "content": {
                            "application/json": {
                              "schema": {"type": "object", "required": ["reason"]}
                            }
                          }
                        },
                        "responses": {
                          "204": {"description": "deleted"}
                        }
                      }
                    }
                  }
                }
                """));

        assertThat(document.title()).isEqualTo("CRM API");
        assertThat(document.description()).isEqualTo("Customer API");
        assertThat(document.baseUrl()).isEqualTo("https://crm.example.test/api");
        assertThat(document.operations()).hasSize(2);
        assertThat(document.operations().get(0).operationId()).isEqualTo("listCustomers");
        assertThat(document.operations().get(0).method()).isEqualTo(OpenApiHttpMethod.GET);
        assertThat(document.operations().get(0).resourceType()).isEqualTo("CRM_CUSTOMER");
        assertThat(document.operations().get(0).schemaJson()).contains("status");
        assertThat(document.operations().get(0).outputSchemaJson()).contains("array");
        assertThat(document.operations().get(1).method()).isEqualTo(OpenApiHttpMethod.DELETE);
        assertThat(document.operations().get(1).schemaJson()).contains("reason");
    }
}
