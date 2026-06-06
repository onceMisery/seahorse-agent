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

package com.miracle.ai.seahorse.agent.kernel.domain.common.exception;

/**
 * 请求的资源不存在时抛出。映射到 HTTP 404。
 */
public class ResourceNotFoundException extends SeahorseBusinessException {

    private static final String DEFAULT_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String resourceType, Object identifier) {
        super(DEFAULT_CODE, resourceType + " not found: " + identifier, 404);
    }

    public ResourceNotFoundException(String message) {
        super(DEFAULT_CODE, message, 404);
    }
}
