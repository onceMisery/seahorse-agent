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

package com.miracle.ai.seahorse.agent.kernel.exception;

/**
 * Thrown when an operation is denied due to insufficient access or policy violation.
 */
public class ForbiddenException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public ForbiddenException(String message, String resourceType, String resourceId) {
        super(message);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public ForbiddenException(String message) {
        this(message, null, null);
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }
}
