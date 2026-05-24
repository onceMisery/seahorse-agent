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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.context;

public final class ResourceAccessReasonCodes {

    public static final String OWNER_MATCH = "OWNER_MATCH";
    public static final String OWNER_REQUIRED = "OWNER_REQUIRED";
    public static final String PUBLIC_RESOURCE = "PUBLIC_RESOURCE";
    public static final String PUBLIC_OR_OWNER_REQUIRED = "PUBLIC_OR_OWNER_REQUIRED";
    public static final String TENANT_MISMATCH = "TENANT_MISMATCH";
    public static final String READ_ONLY_POLICY = "READ_ONLY_POLICY";
    public static final String SUBJECT_NOT_SUPPORTED = "SUBJECT_NOT_SUPPORTED";
    public static final String RESOURCE_TYPE_NOT_SUPPORTED = "RESOURCE_TYPE_NOT_SUPPORTED";
    public static final String DEFAULT_DENY = "DEFAULT_DENY";

    private ResourceAccessReasonCodes() {
    }
}
