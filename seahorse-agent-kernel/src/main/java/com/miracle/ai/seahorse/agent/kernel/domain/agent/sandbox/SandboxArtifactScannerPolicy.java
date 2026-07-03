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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import java.util.List;

public record SandboxArtifactScannerPolicy(String scannerId,
                                           String scannerMode,
                                           boolean failClosed,
                                           boolean rawFindingValuesPersisted,
                                           int maxContentScanBytes,
                                           int maxBinarySignatureScanBytes,
                                           int maxArchiveScanEntries,
                                           int maxArchiveEntryScanBytes,
                                           List<String> promptSafeMediaTypes,
                                           List<String> downloadOnlyMediaTypes,
                                           List<String> contentScannedMediaTypes,
                                           List<String> binarySignatureScannedMediaTypes,
                                           List<String> archiveScannedMediaTypes,
                                           List<String> blockedCategories,
                                           List<String> redactedCategories,
                                           List<String> unsupportedCapabilities) {

    public SandboxArtifactScannerPolicy {
        scannerId = normalize(scannerId, "unknown");
        scannerMode = normalize(scannerMode, "UNKNOWN");
        maxContentScanBytes = Math.max(maxContentScanBytes, 0);
        maxBinarySignatureScanBytes = Math.max(maxBinarySignatureScanBytes, 0);
        maxArchiveScanEntries = Math.max(maxArchiveScanEntries, 0);
        maxArchiveEntryScanBytes = Math.max(maxArchiveEntryScanBytes, 0);
        promptSafeMediaTypes = copy(promptSafeMediaTypes);
        downloadOnlyMediaTypes = copy(downloadOnlyMediaTypes);
        contentScannedMediaTypes = copy(contentScannedMediaTypes);
        binarySignatureScannedMediaTypes = copy(binarySignatureScannedMediaTypes);
        archiveScannedMediaTypes = copy(archiveScannedMediaTypes);
        blockedCategories = copy(blockedCategories);
        redactedCategories = copy(redactedCategories);
        unsupportedCapabilities = copy(unsupportedCapabilities);
    }

    public static SandboxArtifactScannerPolicy unavailable() {
        return new SandboxArtifactScannerPolicy(
                "unknown",
                "UNAVAILABLE",
                true,
                false,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("SCAN_ERROR"),
                List.of(),
                List.of("scanner policy details are not exposed by this scanner"));
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
