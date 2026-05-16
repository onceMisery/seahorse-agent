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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataDictionaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemPayload;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 元数据标准化字典管理 Web adapter。
 */
@RestController
public class SeahorseMetadataDictionaryController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";

    private final MetadataDictionaryInboundPort dictionaryPort;

    public SeahorseMetadataDictionaryController(ObjectProvider<MetadataDictionaryInboundPort> dictionaryPortProvider) {
        this.dictionaryPort = dictionaryPortProvider.getIfAvailable();
    }

    @GetMapping("/metadata-dictionaries/items")
    public Map<String, Object> listItems(@RequestParam String tenantId,
                                         @RequestParam(required = false, defaultValue = "") String dictCode,
                                         @RequestParam(required = false, defaultValue = "false")
                                         boolean includeDisabled) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                dictionaryPort.listItems(tenantId, dictCode, includeDisabled));
    }

    @PostMapping("/metadata-dictionaries/items")
    public Map<String, Object> createItem(@RequestBody MetadataDictionaryItemRequest request) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                dictionaryPort.createItem(toPayload(request)));
    }

    @PutMapping("/metadata-dictionaries/items/{item-id}")
    public Map<String, Object> updateItem(@PathVariable("item-id") String itemId,
                                          @RequestBody MetadataDictionaryItemRequest request) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                dictionaryPort.updateItem(itemId, toPayload(request)));
    }

    @DeleteMapping("/metadata-dictionaries/items/{item-id}")
    public Map<String, Object> deleteItem(@PathVariable("item-id") String itemId) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA,
                Map.of("deleted", dictionaryPort.deleteItem(itemId)));
    }

    private MetadataDictionaryItemPayload toPayload(MetadataDictionaryItemRequest request) {
        MetadataDictionaryItemRequest safeRequest =
                request == null ? new MetadataDictionaryItemRequest() : request;
        return new MetadataDictionaryItemPayload(
                safeRequest.getTenantId(),
                safeRequest.getDictionaryCode(),
                safeRequest.getRawValue(),
                safeRequest.getCanonicalValue(),
                safeRequest.getDisplayName(),
                safeRequest.getEnabled() == null || safeRequest.getEnabled());
    }
}
