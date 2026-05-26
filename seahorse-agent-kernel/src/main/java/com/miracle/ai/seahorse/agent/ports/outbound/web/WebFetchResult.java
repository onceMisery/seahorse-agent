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

package com.miracle.ai.seahorse.agent.ports.outbound.web;

import java.util.Objects;

public record WebFetchResult(String url,
                             WebFetchStatus status,
                             String title,
                             String contentText,
                             String mimeType,
                             String reasonCode,
                             boolean truncated) {

    public WebFetchResult {
        url = Objects.requireNonNullElse(url, "");
        status = Objects.requireNonNullElse(status, WebFetchStatus.FAILED);
        title = Objects.requireNonNullElse(title, "");
        contentText = Objects.requireNonNullElse(contentText, "");
        mimeType = Objects.requireNonNullElse(mimeType, "");
        reasonCode = trimToNull(reasonCode);
    }

    public static WebFetchResult fetched(String url,
                                         String title,
                                         String contentText,
                                         String mimeType,
                                         boolean truncated) {
        return new WebFetchResult(url, WebFetchStatus.FETCHED, title, contentText, mimeType, null, truncated);
    }

    public static WebFetchResult rejected(String url, String reasonCode) {
        return new WebFetchResult(url, WebFetchStatus.REJECTED, "", "", "", reasonCode, false);
    }

    public static WebFetchResult failed(String url, String reasonCode) {
        return new WebFetchResult(url, WebFetchStatus.FAILED, "", "", "", reasonCode, false);
    }

    private static String trimToNull(String value) {
        String normalized = Objects.requireNonNullElse(value, "").trim();
        return normalized.isBlank() ? null : normalized;
    }
}
