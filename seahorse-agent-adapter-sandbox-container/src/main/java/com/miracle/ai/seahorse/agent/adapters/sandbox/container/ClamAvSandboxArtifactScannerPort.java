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

package com.miracle.ai.seahorse.agent.adapters.sandbox.container;

import com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.DefaultSandboxArtifactScannerPort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScannerPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScannerPort;

import java.io.DataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Streams local artifacts to clamd, then applies local policy without persisting engine findings. */
final class ClamAvSandboxArtifactScannerPort implements SandboxArtifactScannerPort {

    private static final int BUFFER_SIZE = 8192;
    private static final byte[] INSTREAM_COMMAND = "zINSTREAM\0".getBytes(StandardCharsets.US_ASCII);

    private final ContainerSandboxAdapterProperties properties;
    private final SandboxArtifactScannerPort localScanner;

    ClamAvSandboxArtifactScannerPort(ContainerSandboxAdapterProperties properties) {
        this(properties, new DefaultSandboxArtifactScannerPort());
    }

    ClamAvSandboxArtifactScannerPort(ContainerSandboxAdapterProperties properties,
                                     SandboxArtifactScannerPort localScanner) {
        this.properties = properties;
        this.localScanner = localScanner;
    }

    @Override
    public SandboxArtifactScanResult scan(SandboxArtifactScanRequest request) {
        Path file = localFile(request.artifact());
        if (file == null) {
            return blocked(request.artifact(), "EXTERNAL_SCAN_ERROR");
        }
        try {
            if (!scanWithClamAv(file)) {
                return blocked(request.artifact(), "MALWARE");
            }
            return localScanner.scan(request);
        } catch (IOException exception) {
            return blocked(request.artifact(), "EXTERNAL_SCAN_ERROR");
        }
    }

    @Override
    public SandboxArtifactScannerPolicy describePolicy() {
        SandboxArtifactScannerPolicy localPolicy = localScanner.describePolicy();
        List<String> unsupported = localPolicy.unsupportedCapabilities().stream()
                .filter(capability -> !capability.toLowerCase().contains("external virus"))
                .toList();
        List<String> blocked = java.util.stream.Stream.concat(
                        localPolicy.blockedCategories().stream(),
                        java.util.stream.Stream.of("MALWARE", "EXTERNAL_SCAN_ERROR"))
                .distinct()
                .sorted()
                .toList();
        return new SandboxArtifactScannerPolicy(
                "clamav-plus-local-bounded",
                "LOCAL_BOUNDED_AND_EXTERNAL_CLAMAV",
                true,
                false,
                localPolicy.maxContentScanBytes(),
                localPolicy.maxBinarySignatureScanBytes(),
                localPolicy.maxArchiveScanEntries(),
                localPolicy.maxArchiveEntryScanBytes(),
                localPolicy.maxCompressedArchiveDecompressedBytes(),
                localPolicy.promptSafeMediaTypes(),
                localPolicy.downloadOnlyMediaTypes(),
                localPolicy.contentScannedMediaTypes(),
                localPolicy.binarySignatureScannedMediaTypes(),
                localPolicy.archiveScannedMediaTypes(),
                blocked,
                localPolicy.redactedCategories(),
                unsupported);
    }

    private boolean scanWithClamAv(Path file) throws IOException {
        if (Files.size(file) > properties.getMaxSessionFileBytes()) {
            throw new IOException("artifact exceeds external scanner request limit");
        }
        Duration timeout = properties.getExternalVirusScannerTimeout();
        int timeoutMillis = Math.toIntExact(Math.min(timeout.toMillis(), Integer.MAX_VALUE));
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.getExternalVirusScannerHost(),
                    properties.getExternalVirusScannerPort()), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            try (DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                 InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                ByteArrayOutputStream request = new ByteArrayOutputStream();
                request.write(INSTREAM_COMMAND);
                int read;
                while ((read = input.read(buffer)) != -1) {
                    request.write((read >>> 24) & 0xff);
                    request.write((read >>> 16) & 0xff);
                    request.write((read >>> 8) & 0xff);
                    request.write(read & 0xff);
                    request.write(buffer, 0, read);
                }
                request.write(new byte[Integer.BYTES]);
                output.write(request.toByteArray());
                output.flush();
                String response = readResponse(socket.getInputStream());
                if (response.endsWith("OK")) {
                    return true;
                }
                if (response.endsWith("FOUND")) {
                    return false;
                }
                throw new IOException("unexpected clamd response");
            }
        }
    }

    private static Path localFile(SandboxArtifact artifact) {
        try {
            URI uri = URI.create(artifact.objectUri());
            if (!"file".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            Path file = Path.of(uri);
            return Files.isRegularFile(file) ? file : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String readResponse(InputStream input) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                response.write(buffer, 0, read);
            }
        } catch (IOException exception) {
            if (response.size() == 0) {
                throw exception;
            }
        }
        return response.toString(StandardCharsets.US_ASCII).trim();
    }

    private static SandboxArtifactScanResult blocked(SandboxArtifact artifact, String category) {
        return SandboxArtifactScanResult.blocked(
                artifact.sensitivity() == ContextSensitivity.SECRET ? ContextSensitivity.SECRET : ContextSensitivity.CONFIDENTIAL,
                category.equals("MALWARE") ? "external virus scan blocked artifact" : "external virus scan unavailable",
                false,
                List.of(category));
    }

}
