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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeContainerReapResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerSandboxRuntimeAdapterTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void shouldRunCodeInterpreterThroughDockerWithNetworkDeniedWorkspaceMount() throws Exception {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                "hello from sandbox\n",
                Duration.ofMillis(250)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);

        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));
        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('hello from sandbox')",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().reasonCode()).isEqualTo(SandboxPolicyReasonCode.VALID_REQUEST);
        assertThat(result.execution().resultSummary()).contains("hello from sandbox");
        assertThat(result.artifacts()).isEmpty();
        assertThat(runner.lastCommand.commandLine())
                .containsSubsequence("docker", "run", "--rm")
                .containsSubsequence("--network", "none")
                .containsSubsequence("--memory", "128m")
                .containsSubsequence("--cpus", "0.5")
                .containsSubsequence("--pids-limit", "64")
                .containsSubsequence("python:3.11-alpine", "python", "/workspace/main.py");
        assertThat(runner.lastCommand.commandLine())
                .anySatisfy(argument -> assertThat(argument).endsWith(":/workspace:rw"));
        assertThat(Files.readString(runner.lastCommand.workingDirectory().resolve("main.py")))
                .isEqualTo("print('hello from sandbox')");
    }

    @Test
    void shouldFailClosedWhenCodeInterpreterNetworkIsRequested() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('network should not start')",
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary())
                .contains("network egress is only supported for browser automation");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldCollectArtifactsCreatedBySuccessfulExecution() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("created artifacts\n", Duration.ofMillis(180)),
                command -> {
                    Files.writeString(command.workingDirectory().resolve("answer.txt"), "artifact text");
                    Path nested = command.workingDirectory().resolve("reports");
                    Files.createDirectories(nested);
                    Files.writeString(nested.resolve("summary.md"), "# Sandbox artifact");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('created artifacts')",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.artifacts()).hasSize(2);
        assertThat(result.artifacts())
                .allSatisfy(artifact -> {
                    assertThat(artifact.artifactId()).startsWith("sandbox_artifact_container_");
                    assertThat(artifact.sessionId()).isEqualTo(session.sessionId());
                    assertThat(artifact.executionId()).isEqualTo(result.execution().executionId());
                    assertThat(artifact.objectUri()).startsWith("file:");
                    assertThat(artifact.scanStatus()).isEqualTo(SandboxArtifactScanStatus.PENDING);
                    assertThat(artifact.sensitivity()).isEqualTo(ContextSensitivity.INTERNAL);
                    assertThat(artifact.createdAt()).isEqualTo(CLOCK.instant());
                });
        assertThat(result.artifacts())
                .anySatisfy(artifact -> {
                    assertThat(artifact.objectUri()).contains("answer.txt");
                    assertThat(artifact.mediaType()).isEqualTo("text/plain");
                })
                .anySatisfy(artifact -> {
                    assertThat(artifact.objectUri()).contains("summary.md");
                    assertThat(artifact.mediaType()).isEqualTo("text/markdown");
                })
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"));
    }

    @Test
    void shouldClassifyZipArtifactsWithStableMediaType() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("created archive\n", Duration.ofMillis(180)),
                command -> Files.write(command.workingDirectory().resolve("bundle.zip"),
                        new byte[]{'P', 'K', 0x03, 0x04, 0, 0}));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('created archive')",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("bundle.zip");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("application/zip");
    }

    @Test
    void shouldClassifyTarArtifactsWithStableMediaType() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("created tar archive\n", Duration.ofMillis(180)),
                command -> Files.write(command.workingDirectory().resolve("bundle.tar"),
                        new byte[]{'u', 's', 't', 'a', 'r'}));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('created tar archive')",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("bundle.tar");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("application/x-tar");
    }

    @Test
    void shouldClassifyGzipTarArtifactsWithStableMediaType() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("created gzip tar archive\n", Duration.ofMillis(180)),
                command -> Files.write(command.workingDirectory().resolve("bundle.tar.gz"),
                        new byte[]{0x1F, (byte) 0x8B, 0x08}));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('created gzip tar archive')",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("bundle.tar.gz");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("application/gzip");
    }

    @Test
    void shouldRunFileConversionWithGeneratedConverterAndCollectOnlyOutputArtifact() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted 1 rows from csv to json\n", Duration.ofMillis(210)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("csv.DictReader", "source_format = \"csv\"", "converted.json")
                            .doesNotContain("Ada,42");
                    assertThat(Files.readString(command.workingDirectory().resolve("input.csv")))
                            .isEqualTo("name,score\nAda,42\n");
                    Files.writeString(command.workingDirectory().resolve("converted.json"),
                            "[{\"name\":\"Ada\",\"score\":\"42\"}]");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"csv","targetFormat":"json","content":"name,score\\nAda,42\\n"}
                        """,
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().reasonCode()).isEqualTo(SandboxPolicyReasonCode.VALID_REQUEST);
        assertThat(result.execution().resultSummary()).contains("converted 1 rows");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.json");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("application/json");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.csv"));
        assertThat(runner.lastCommand.commandLine())
                .containsSubsequence("docker", "run", "--rm")
                .containsSubsequence("--network", "none")
                .containsSubsequence("python:3.11-alpine", "python", "/workspace/main.py");
    }

    @Test
    void shouldRunJsonToCsvFileConversionAndCollectCsvOutputOnly() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted 2 rows from json to csv\n", Duration.ofMillis(220)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("csv.DictWriter", "source_format = \"json\"", "converted.csv")
                            .doesNotContain("Grace");
                    assertThat(Files.readString(command.workingDirectory().resolve("input.json")))
                            .contains("\"Ada\"", "\"Grace\"");
                    Files.writeString(command.workingDirectory().resolve("converted.csv"),
                            "name,score\nAda,42\nGrace,99\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"json","targetFormat":"csv","content":"[{\\"name\\":\\"Ada\\",\\"score\\":42},{\\"name\\":\\"Grace\\",\\"score\\":99}]"}
                        """,
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().reasonCode()).isEqualTo(SandboxPolicyReasonCode.VALID_REQUEST);
        assertThat(result.execution().resultSummary()).contains("converted 2 rows");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.csv");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/csv");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.json"));
    }

    @Test
    void shouldRunTsvToJsonFileConversion() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted 1 rows from tsv to json\n", Duration.ofMillis(210)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("csv.DictReader", "source_format = \"tsv\"", "converted.json")
                            .doesNotContain("Ada\t42");
                    assertThat(Files.readString(command.workingDirectory().resolve("input.tsv")))
                            .isEqualTo("name\tscore\nAda\t42\n");
                    Files.writeString(command.workingDirectory().resolve("converted.json"),
                            "[{\"name\":\"Ada\",\"score\":\"42\"}]");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"tsv","targetFormat":"json","content":"name\\tscore\\nAda\\t42\\n"}
                        """,
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted 1 rows");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.json");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("application/json");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.tsv"));
    }

    @Test
    void shouldRunMarkdownToHtmlFileConversionAndCollectHtmlOutputOnly() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted markdown document to html\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("markdown_to_html", "source_format = \"markdown\"", "converted.html")
                            .doesNotContain("Sandbox Document");
                    assertThat(Files.readString(command.workingDirectory().resolve("input.md")))
                            .isEqualTo("# Sandbox Document\n\nHello **Ada**\n");
                    Files.writeString(command.workingDirectory().resolve("converted.html"),
                            "<!doctype html>\n<html><body><h1>Sandbox Document</h1></body></html>\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"md","targetFormat":"html","content":"# Sandbox Document\\n\\nHello **Ada**\\n"}
                        """,
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted markdown document to html");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.html");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/html");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.md"));
    }

    @Test
    void shouldRunDocxToTextFileConversionAndCollectTextOutputOnly() throws Exception {
        byte[] docxBytes = zipBytes("word/document.xml", "<w:document><w:p><w:r><w:t>safe docx</w:t></w:r></w:p></w:document>");
        String docxBase64 = Base64.getEncoder().encodeToString(docxBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted docx document to text\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("docx_to_text",
                                    "source_format = \"docx\"",
                                    "docx word/document.xml exceeds extraction budget",
                                    "converted.txt")
                            .doesNotContain(docxBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.docx")))
                            .isEqualTo(docxBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.txt"),
                            "Sandbox DOCX marker\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"docx","targetFormat":"txt","contentEncoding":"base64","content":"%s"}
                        """.formatted(docxBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted docx document to text");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.txt");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/plain");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.docx"));
    }

    @Test
    void shouldRunDocxToHtmlFileConversionAndCollectHtmlOutputOnly() throws Exception {
        byte[] docxBytes = zipBytes("word/document.xml",
                "<w:document><w:p><w:r><w:t>safe docx title</w:t></w:r></w:p>"
                        + "<w:p><w:r><w:t>second paragraph</w:t></w:r></w:p></w:document>");
        String docxBase64 = Base64.getEncoder().encodeToString(docxBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted docx document to html\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("docx_to_html",
                                    "docx_paragraphs",
                                    "source_format = \"docx\"",
                                    "target_format = \"html\"",
                                    "converted.html")
                            .doesNotContain(docxBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.docx")))
                            .isEqualTo(docxBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.html"),
                            "<!doctype html>\n<html><body><p>safe docx title</p><p>second paragraph</p></body></html>\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"docx","targetFormat":"html","contentEncoding":"base64","content":"%s"}
                        """.formatted(docxBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted docx document to html");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.html");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/html");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.docx"));
    }

    @Test
    void shouldRejectDocxWithActiveContentBeforeRunningContainer() throws Exception {
        byte[] docxBytes = zipBytes(
                "word/vbaProject.bin",
                "macro marker",
                "word/document.xml",
                "<w:document><w:p><w:r><w:t>unsafe docx</w:t></w:r></w:p></w:document>");
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"docx","targetFormat":"txt","contentEncoding":"base64","content":"%s"}
                        """.formatted(Base64.getEncoder().encodeToString(docxBytes)),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("docx active content is not supported");
        assertThat(runner.lastCommand).isNull();
        assertThat(tempDir.resolve(session.sessionId()).resolve("main.py")).doesNotExist();
        assertThat(tempDir.resolve(session.sessionId()).resolve("input.docx")).doesNotExist();
    }

    @Test
    void shouldRunOdtToTextFileConversionAndCollectTextOutputOnly() throws Exception {
        byte[] odtBytes = odtBytes("safe odt title", "second odt paragraph");
        String odtBase64 = Base64.getEncoder().encodeToString(odtBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted odt document to text\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("odt_to_text",
                                    "odt_paragraphs",
                                    "source_format = \"odt\"",
                                    "odt content.xml exceeds extraction budget",
                                    "converted.txt")
                            .doesNotContain(odtBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.odt")))
                            .isEqualTo(odtBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.txt"),
                            "safe odt title\nsecond odt paragraph\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"odt","targetFormat":"txt","contentEncoding":"base64","content":"%s"}
                        """.formatted(odtBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted odt document to text");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.txt");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/plain");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.odt"));
    }

    @Test
    void shouldRunOdtToHtmlFileConversionAndCollectHtmlOutputOnly() throws Exception {
        byte[] odtBytes = odtBytes("safe odt html title", "second odt html paragraph");
        String odtBase64 = Base64.getEncoder().encodeToString(odtBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted odt document to html\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("odt_to_html",
                                    "odt_paragraphs",
                                    "source_format = \"odt\"",
                                    "target_format = \"html\"",
                                    "converted.html")
                            .doesNotContain(odtBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.odt")))
                            .isEqualTo(odtBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.html"),
                            "<!doctype html>\n<html><body><p>safe odt html title</p><p>second odt html paragraph</p></body></html>\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"odt","targetFormat":"html","contentEncoding":"base64","content":"%s"}
                        """.formatted(odtBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted odt document to html");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.html");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/html");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.odt"));
    }

    @Test
    void shouldRejectOdtWithActiveContentBeforeRunningContainer() throws Exception {
        byte[] odtBytes = zipBytes(
                "content.xml",
                """
                        <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                                 xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                          <office:body><office:text><text:p>unsafe odt</text:p></office:text></office:body>
                        </office:document-content>
                        """,
                "Scripts/macro.js",
                "alert('macro')");
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"odt","targetFormat":"txt","contentEncoding":"base64","content":"%s"}
                        """.formatted(Base64.getEncoder().encodeToString(odtBytes)),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("odt active content is not supported");
        assertThat(runner.lastCommand).isNull();
        assertThat(tempDir.resolve(session.sessionId()).resolve("main.py")).doesNotExist();
        assertThat(tempDir.resolve(session.sessionId()).resolve("input.odt")).doesNotExist();
    }

    @Test
    void shouldRunOdpToTextFileConversionAndCollectTextOutputOnly() throws Exception {
        byte[] odpBytes = odpBytes("safe odp title", "second odp slide text");
        String odpBase64 = Base64.getEncoder().encodeToString(odpBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted odp presentation to text\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("odp_to_text",
                                    "odp_paragraphs",
                                    "source_format = \"odp\"",
                                    "odp content.xml exceeds extraction budget",
                                    "converted.txt")
                            .doesNotContain(odpBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.odp")))
                            .isEqualTo(odpBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.txt"),
                            "safe odp title\nsecond odp slide text\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"odp","targetFormat":"txt","contentEncoding":"base64","content":"%s"}
                        """.formatted(odpBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted odp presentation to text");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.txt");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/plain");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.odp"));
    }

    @Test
    void shouldRunOdpToHtmlFileConversionAndCollectHtmlOutputOnly() throws Exception {
        byte[] odpBytes = odpBytes("safe odp html title", "second odp html slide text");
        String odpBase64 = Base64.getEncoder().encodeToString(odpBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted odp presentation to html\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("odp_to_html",
                                    "odp_paragraphs",
                                    "source_format = \"odp\"",
                                    "target_format = \"html\"",
                                    "converted.html")
                            .doesNotContain(odpBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.odp")))
                            .isEqualTo(odpBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.html"),
                            "<!doctype html>\n<html><body><p>safe odp html title</p><p>second odp html slide text</p></body></html>\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"odp","targetFormat":"html","contentEncoding":"base64","content":"%s"}
                        """.formatted(odpBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted odp presentation to html");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.html");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/html");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.odp"));
    }

    @Test
    void shouldRejectOdpWithActiveContentBeforeRunningContainer() throws Exception {
        byte[] odpBytes = zipBytes(
                "content.xml",
                """
                        <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                                 xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
                                                 xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                          <office:body><office:presentation><draw:page draw:name="page1"><draw:frame><draw:text-box><text:p>unsafe odp</text:p></draw:text-box></draw:frame></draw:page></office:presentation></office:body>
                        </office:document-content>
                        """,
                "Scripts/macro.js",
                "alert('macro')");
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"odp","targetFormat":"txt","contentEncoding":"base64","content":"%s"}
                        """.formatted(Base64.getEncoder().encodeToString(odpBytes)),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("odp active content is not supported");
        assertThat(runner.lastCommand).isNull();
        assertThat(tempDir.resolve(session.sessionId()).resolve("main.py")).doesNotExist();
        assertThat(tempDir.resolve(session.sessionId()).resolve("input.odp")).doesNotExist();
    }

    @Test
    void shouldRunOdsToCsvFileConversionAndCollectCsvOutputOnly() throws Exception {
        byte[] odsBytes = odsBytes("safe ods marker", "ODS conversion extracts first table");
        String odsBase64 = Base64.getEncoder().encodeToString(odsBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted ods spreadsheet to csv\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("ods_to_csv",
                                    "ods_rows",
                                    "source_format = \"ods\"",
                                    "ods content.xml exceeds extraction budget",
                                    "converted.csv")
                            .doesNotContain(odsBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.ods")))
                            .isEqualTo(odsBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.csv"),
                            "label,value\nsafe ods marker,ODS conversion extracts first table\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"ods","targetFormat":"csv","contentEncoding":"base64","content":"%s"}
                        """.formatted(odsBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted ods spreadsheet to csv");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.csv");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/csv");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.ods"));
    }

    @Test
    void shouldRunOdsToHtmlFileConversionAndCollectHtmlOutputOnly() throws Exception {
        byte[] odsBytes = odsBytes("safe ods html marker", "ODS HTML conversion renders first table");
        String odsBase64 = Base64.getEncoder().encodeToString(odsBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted ods spreadsheet to html\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("ods_to_html",
                                    "ods_rows",
                                    "source_format = \"ods\"",
                                    "target_format = \"html\"",
                                    "converted.html")
                            .doesNotContain(odsBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.ods")))
                            .isEqualTo(odsBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.html"),
                            "<!doctype html>\n<html><body><table><tr><td>label</td></tr></table></body></html>\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"ods","targetFormat":"html","contentEncoding":"base64","content":"%s"}
                        """.formatted(odsBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted ods spreadsheet to html");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.html");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/html");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.ods"));
    }

    @Test
    void shouldRejectOdsWithActiveContentBeforeRunningContainer() throws Exception {
        byte[] odsBytes = zipBytes(
                "content.xml",
                """
                        <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                                 xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                                                 xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                          <office:body><office:spreadsheet><table:table><table:table-row><table:table-cell><text:p>unsafe ods</text:p></table:table-cell></table:table-row></table:table></office:spreadsheet></office:body>
                        </office:document-content>
                        """,
                "Scripts/macro.js",
                "alert('macro')");
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"ods","targetFormat":"csv","contentEncoding":"base64","content":"%s"}
                        """.formatted(Base64.getEncoder().encodeToString(odsBytes)),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("ods active content is not supported");
        assertThat(runner.lastCommand).isNull();
        assertThat(tempDir.resolve(session.sessionId()).resolve("main.py")).doesNotExist();
        assertThat(tempDir.resolve(session.sessionId()).resolve("input.ods")).doesNotExist();
    }

    @Test
    void shouldRunPdfToTextFileConversionAndCollectTextOutputOnly() throws Exception {
        String pdfBase64 = Base64.getEncoder().encodeToString("%PDF-1.4\nfake-pdf-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted pdf document to text\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("pdf_to_text",
                                    "source_format = \"pdf\"",
                                    "b\"%PDF-\"",
                                    "encrypted pdf is not supported",
                                    "bounded_pdf_flate_decode",
                                    "pdf FlateDecode stream exceeds decompression budget",
                                    "converted.txt")
                            .doesNotContain(pdfBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.pdf")))
                            .isEqualTo("%PDF-1.4\nfake-pdf-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    Files.writeString(command.workingDirectory().resolve("converted.txt"),
                            "Sandbox PDF marker\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"pdf","targetFormat":"txt","contentEncoding":"base64","content":"%s"}
                        """.formatted(pdfBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted pdf document to text");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.txt");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/plain");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.pdf"));
    }

    @Test
    void shouldRunPdfToHtmlFileConversionAndCollectHtmlOutputOnly() throws Exception {
        String pdfBase64 = Base64.getEncoder().encodeToString("%PDF-1.4\nfake-pdf-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted pdf document to html\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("pdf_to_html",
                                    "pdf_to_text(path)",
                                    "source_format = \"pdf\"",
                                    "target_format = \"html\"",
                                    "converted.html")
                            .doesNotContain(pdfBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.pdf")))
                            .isEqualTo("%PDF-1.4\nfake-pdf-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    Files.writeString(command.workingDirectory().resolve("converted.html"),
                            "<!doctype html>\n<html><body><p>Sandbox PDF marker</p></body></html>\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"pdf","targetFormat":"html","contentEncoding":"base64","content":"%s"}
                        """.formatted(pdfBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted pdf document to html");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.html");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/html");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.pdf"));
    }

    @Test
    void shouldRunXlsxToCsvFileConversionAndCollectCsvOutputOnly() throws Exception {
        byte[] xlsxBytes = xlsxBytes();
        String xlsxBase64 = Base64.getEncoder().encodeToString(xlsxBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted xlsx worksheet to csv\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("xlsx_to_csv",
                                    "source_format = \"xlsx\"",
                                    "xl/worksheets/sheet1.xml",
                                    "converted.csv")
                            .doesNotContain(xlsxBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.xlsx")))
                            .isEqualTo(xlsxBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.csv"),
                            "name,score\nAda,42\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"xlsx","targetFormat":"csv","contentEncoding":"base64","content":"%s"}
                        """.formatted(xlsxBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted xlsx worksheet to csv");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.csv");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/csv");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.xlsx"));
    }

    @Test
    void shouldRunXlsxToHtmlFileConversionAndCollectHtmlOutputOnly() throws Exception {
        byte[] xlsxBytes = xlsxBytes();
        String xlsxBase64 = Base64.getEncoder().encodeToString(xlsxBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted xlsx worksheet to html\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("xlsx_to_html",
                                    "xlsx_rows",
                                    "source_format = \"xlsx\"",
                                    "target_format = \"html\"",
                                    "converted.html")
                            .doesNotContain(xlsxBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.xlsx")))
                            .isEqualTo(xlsxBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.html"),
                            "<!doctype html>\n<html><body><table><tr><td>name</td></tr></table></body></html>\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"xlsx","targetFormat":"html","contentEncoding":"base64","content":"%s"}
                        """.formatted(xlsxBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted xlsx worksheet to html");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.html");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/html");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.xlsx"));
    }

    @Test
    void shouldRunPptxToTextFileConversionAndCollectTextOutputOnly() throws Exception {
        byte[] pptxBytes = pptxBytes();
        String pptxBase64 = Base64.getEncoder().encodeToString(pptxBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted pptx presentation to text\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("pptx_to_text",
                                    "source_format = \"pptx\"",
                                    "ppt/slides/slide",
                                    "converted.txt")
                            .doesNotContain(pptxBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.pptx")))
                            .isEqualTo(pptxBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.txt"),
                            "Sandbox PPTX Title\nSlide body\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"pptx","targetFormat":"txt","contentEncoding":"base64","content":"%s"}
                        """.formatted(pptxBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted pptx presentation to text");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.txt");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/plain");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.pptx"));
    }

    @Test
    void shouldRunPptxToHtmlFileConversionAndCollectHtmlOutputOnly() throws Exception {
        byte[] pptxBytes = pptxBytes();
        String pptxBase64 = Base64.getEncoder().encodeToString(pptxBytes);
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted pptx presentation to html\n", Duration.ofMillis(190)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("pptx_to_html",
                                    "source_format = \"pptx\"",
                                    "ppt/slides/slide",
                                    "converted.html")
                            .doesNotContain(pptxBase64);
                    assertThat(Files.readAllBytes(command.workingDirectory().resolve("input.pptx")))
                            .isEqualTo(pptxBytes);
                    Files.writeString(command.workingDirectory().resolve("converted.html"),
                            "<!doctype html>\n<html><body>\n<p>Sandbox PPTX Title</p>\n<p>Slide body</p>\n</body></html>\n");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"pptx","targetFormat":"html","contentEncoding":"base64","content":"%s"}
                        """.formatted(pptxBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().resultSummary()).contains("converted pptx presentation to html");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.html");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("text/html");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.pptx"));
    }

    @Test
    void shouldRejectPptxWithActiveContentBeforeRunningContainer() throws Exception {
        byte[] pptxBytes = zipBytes(
                "ppt/slides/slide1.xml",
                """
                        <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                               xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                          <p:cSld><p:spTree><p:sp><p:txBody><a:p><a:r><a:t>unsafe pptx</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld>
                        </p:sld>
                        """,
                "ppt/vbaProject.bin",
                "macro bytes");
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"pptx","targetFormat":"txt","contentEncoding":"base64","content":"%s"}
                        """.formatted(Base64.getEncoder().encodeToString(pptxBytes)),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("pptx active content is not supported");
        assertThat(runner.lastCommand).isNull();
        assertThat(tempDir.resolve(session.sessionId()).resolve("main.py")).doesNotExist();
        assertThat(tempDir.resolve(session.sessionId()).resolve("input.pptx")).doesNotExist();
    }

    @Test
    void shouldRejectPdfWithActiveContentBeforeRunningContainer() {
        shouldRejectPdfActiveContentMarkerBeforeRunningContainer("/OpenAction");
        shouldRejectPdfActiveContentMarkerBeforeRunningContainer("/ImportData");
    }

    private void shouldRejectPdfActiveContentMarkerBeforeRunningContainer(String marker) {
        String pdfBase64 = Base64.getEncoder().encodeToString(
                ("%PDF-1.4\n1 0 obj\n<< " + marker + " 2 0 R >>\nendobj")
                        .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"pdf","targetFormat":"txt","contentEncoding":"base64","content":"%s"}
                        """.formatted(pdfBase64),
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("pdf active content is not supported");
        assertThat(runner.lastCommand).isNull();
        assertThat(tempDir.resolve(session.sessionId()).resolve("main.py")).doesNotExist();
        assertThat(tempDir.resolve(session.sessionId()).resolve("input.pdf")).doesNotExist();
    }

    @Test
    void shouldRunBrowserAutomationWithGeneratedPlaywrightScriptAndCollectOnlyOutputs() throws Exception {
        String html = "<!doctype html><html><head><title>Browser Smoke</title></head><body><main>browser marker</main></body></html>";
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded(
                        "browser snapshot completed; textLength=14; screenshot=True; har=True; video=True\n",
                        Duration.ofMillis(320)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("sync_playwright",
                                    "page.set_content",
                                    "screenshot.png",
                                    "browser-network.har",
                                    "browser-video.webm",
                                    "record_video_dir")
                            .doesNotContain("browser marker");
                    assertThat(Files.readString(command.workingDirectory().resolve("browser-input.html")))
                            .isEqualTo(html);
                    assertThat(command.workingDirectory().resolve("browser-cookies.json")).doesNotExist();
                    Files.writeString(command.workingDirectory().resolve("browser-result.json"),
                            """
                                    {"action":"snapshot","title":"Browser Smoke","text":"browser marker"}
                                    """);
                    Files.write(command.workingDirectory().resolve("screenshot.png"), new byte[]{1, 2, 3});
                    Files.writeString(command.workingDirectory().resolve("browser-network.har"),
                            """
                                    {"log":{"version":"1.2","entries":[{"_blocked":true}]}}
                                    """);
                    Files.write(command.workingDirectory().resolve("browser-video.webm"), new byte[]{1, 2, 3, 4});
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","html":"<!doctype html><html><head><title>Browser Smoke</title></head><body><main>browser marker</main></body></html>","cookies":[],"viewportWidth":1024,"viewportHeight":640,"screenshot":true,"har":true,"video":true}
                        """,
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().reasonCode()).isEqualTo(SandboxPolicyReasonCode.VALID_REQUEST);
        assertThat(result.execution().resultSummary()).contains("browser snapshot completed");
        assertThat(result.artifacts()).hasSize(4);
        assertThat(result.artifacts())
                .anySatisfy(artifact -> {
                    assertThat(artifact.objectUri()).contains("browser-result.json");
                    assertThat(artifact.mediaType()).isEqualTo("application/json");
                })
                .anySatisfy(artifact -> {
                    assertThat(artifact.objectUri()).contains("screenshot.png");
                    assertThat(artifact.mediaType()).isEqualTo("image/png");
                })
                .anySatisfy(artifact -> {
                    assertThat(artifact.objectUri()).contains("browser-network.har");
                    assertThat(artifact.mediaType()).isEqualTo("application/har+json");
                })
                .anySatisfy(artifact -> {
                    assertThat(artifact.objectUri()).contains("browser-video.webm");
                    assertThat(artifact.mediaType()).isEqualTo("video/webm");
                })
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("browser-input.html"));
        assertThat(runner.lastCommand.commandLine())
                .containsSubsequence("docker", "run", "--rm")
                .containsSubsequence("--network", "none")
                .containsSubsequence("--memory", "768m")
                .containsSubsequence("seahorse-sandbox-browser:playwright-1.48.0", "python", "/workspace/main.py");
    }

    @Test
    void shouldRunBrowserAutomationUrlModeWithAllowlistedHostNetwork() throws Exception {
        String cookieValue = "session-secret-value";
        String storageValue = "restored-local-storage-secret";
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded(
                        "browser snapshot completed; textLength=18; screenshot=True; har=True; video=False; cookies=1; sessionStateReplay=True; sessionStateCapture=True\n",
                        Duration.ofMillis(320)),
                command -> {
                    String script = Files.readString(command.workingDirectory().resolve("main.py"));
                    assertThat(script)
                            .contains("target_url = \"http://host.docker.internal:18080/page\"",
                                    "allowed_hosts = set([\"host.docker.internal\"])",
                                    "private_network_allowed_hosts = set([])",
                                    "import ipaddress",
                                    "import socket",
                                    "from urllib.parse import unquote_plus, urlparse",
                                    "sensitive_query_parameter_names = {",
                                    "\"sessiontoken\",",
                                    "\"clientsecret\",",
                                    "def normalized_query_parameter_name(value):",
                                    "return \"\".join(ch for ch in name if ch.isalnum())",
                                    "def has_credential_url_parts(url):",
                                    "if parsed.username or parsed.password or parsed.fragment:",
                                    "for parameter in parsed.query.replace(\";\", \"&\").split(\"&\"):",
                                    "def redacted_har_url(url):",
                                    "return \"data:<redacted>\"",
                                    "return \"blob:<redacted>\"",
                                    "return \"<redacted-url>\"",
                                    "redacted += \"?<redacted-userinfo>\"",
                                    "\"url\": redacted_har_url(request.url)",
                                    "\"url\": redacted_har_url(current_url)",
                                    "\"url\": redacted_har_url(page.url)",
                                    "\"blocked\": blocked",
                                    "if has_credential_url_parts(url):",
                                    "scheme = parsed.scheme.lower()",
                                    "host = (parsed.hostname or \"\").lower()",
                                    "if scheme in (\"http\", \"https\") and host:",
                                    "return resolved_host_decision(host)",
                                    "not ipaddress.ip_address(value).is_global",
                                    "socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)",
                                    "\"resolved_private_ip\"",
                                    "browser navigation blocked by egress policy",
                                    "cookies_path = Path(\"/workspace/browser-cookies.json\")",
                                    "session_state_input_path = Path(\"/workspace/browser-session-state-input.json\")",
                                    "context_options[\"storage_state\"] = str(session_state_input_path)",
                                    "context.add_cookies(browser_cookies)",
                                    "capture_session_state = True",
                                    "context.storage_state(path=str(session_state_path))",
                                    "max_session_state_bytes = 131072",
                                    "browser session state capture exceeds storage budget",
                                    "session_state_path.unlink(missing_ok=True)",
                                    "browser-session-state.json",
                                    "browser-session-summary.json",
                                    "page.goto",
                                    "\"source\": \"url\" if target_url else \"html\"")
                            .doesNotContain("def origin_key",
                                    "target_origin",
                                    "return origin_key(url) == target_origin",
                                    "url mode marker",
                                    cookieValue,
                                    storageValue);
                    assertThat(Files.readString(command.workingDirectory().resolve("browser-cookies.json")))
                            .contains("seahorse_session", cookieValue, "host.docker.internal");
                    assertThat(Files.readString(command.workingDirectory().resolve("browser-session-state-input.json")))
                            .contains("restored_session", storageValue, "host.docker.internal")
                            .doesNotContain("objectUri", "storageRef");
                    assertThat(command.workingDirectory().resolve("browser-input.html")).doesNotExist();
                    Files.writeString(command.workingDirectory().resolve("browser-result.json"),
                            """
                                    {"action":"snapshot","source":"url","url":"http://host.docker.internal:18080/page","allowedHosts":["host.docker.internal"],"cookies":{"count":1,"domains":["host.docker.internal"]},"sessionState":{"replayed":true,"replay":{"cookies":{"count":1,"domains":["host.docker.internal"]},"origins":[{"origin":"http://host.docker.internal:18080","localStorageCount":1}]},"captured":true},"text":"url mode marker"}
                                    """);
                    Files.writeString(command.workingDirectory().resolve("browser-network.har"),
                            """
                                    {"log":{"version":"1.2","entries":[{"request":{"url":"http://host.docker.internal:18080/page"},"response":{"status":200},"_blocked":false}]}}
                                    """);
                    Files.writeString(command.workingDirectory().resolve("browser-session-summary.json"),
                            """
                                    {"cookies":{"count":1,"domains":["host.docker.internal"]},"origins":[{"origin":"http://host.docker.internal:18080","localStorageCount":1}]}
                                    """);
                    Files.writeString(command.workingDirectory().resolve("browser-session-state.json"),
                            """
                                    {"cookies":[{"name":"seahorse_session","value":"session-secret-value","domain":"host.docker.internal"}],"origins":[{"origin":"http://host.docker.internal:18080","localStorage":[{"name":"token","value":"secret-token-value"}]}]}
                                    """);
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://host.docker.internal:18080/page","allowedHosts":["host.docker.internal"],"cookies":[{"name":"seahorse_session","value":"%s","domain":"host.docker.internal","path":"/","httpOnly":true,"secure":false,"sameSite":"Lax"}],"sessionState":{"cookies":[{"name":"restored_session","value":"restored-secret-value","domain":"host.docker.internal","path":"/","httpOnly":true,"secure":false,"sameSite":"Lax"}],"origins":[{"origin":"http://host.docker.internal:18080","localStorage":[{"name":"seahorse_session_marker","value":"%s"}]}]},"har":true,"captureSessionState":true}
                        """.formatted(cookieValue, storageValue),
                true,
                List.of("host.docker.internal")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.artifacts()).hasSize(4);
        assertThat(result.artifacts())
                .anySatisfy(artifact -> assertThat(artifact.objectUri()).contains("browser-result.json"))
                .anySatisfy(artifact -> assertThat(artifact.objectUri()).contains("browser-network.har"))
                .anySatisfy(artifact -> {
                    assertThat(artifact.objectUri()).contains("browser-session-summary.json");
                    assertThat(artifact.sensitivity()).isEqualTo(ContextSensitivity.INTERNAL);
                })
                .anySatisfy(artifact -> {
                    assertThat(artifact.objectUri()).contains("browser-session-state.json");
                    assertThat(artifact.sensitivity()).isEqualTo(ContextSensitivity.SECRET);
                })
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("browser-input.html"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("browser-cookies.json"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("browser-session-state-input.json"));
        assertThat(runner.lastCommand.commandLine())
                .containsSubsequence("docker", "run", "--rm")
                .containsSubsequence("--add-host", "host.docker.internal:host-gateway")
                .containsSubsequence("--memory", "768m")
                .containsSubsequence("seahorse-sandbox-browser:playwright-1.48.0", "python", "/workspace/main.py")
                .doesNotContain("--network");
    }

    @Test
    void shouldInjectConfiguredBrowserProxyForUrlModeOnly() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("browser snapshot completed; proxy=True\n", Duration.ofMillis(320)),
                command -> {
                    String script = Files.readString(command.workingDirectory().resolve("main.py"));
                    assertThat(script)
                            .contains("browser_proxy_server = \"http://proxy.docker.internal:18082\"",
                                    "if target_url and browser_proxy_server:",
                                    "proxy_options = {\"server\": browser_proxy_server}",
                                    "context_options[\"proxy\"] = proxy_options",
                                    "def build_egress_summary(events):",
                                    "\"proxy\": {",
                                    "\"enabled\": bool(target_url and browser_proxy_server)",
                                    "\"egress\": egress_summary",
                                    "egressRequests={egress_summary['requestCount']}");
                    Files.writeString(command.workingDirectory().resolve("browser-result.json"),
                            """
                                    {"action":"snapshot","source":"url","url":"http://host.docker.internal:18080/page","allowedHosts":["host.docker.internal"],"proxy":{"enabled":true},"text":"proxy marker"}
                                    """);
                });
        ContainerSandboxRuntimeAdapter adapter = adapterWithBrowserProxy(runner, "http://proxy.docker.internal:18082");
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://host.docker.internal:18080/page","allowedHosts":["host.docker.internal"],"screenshot":false,"har":false,"video":false}
                        """,
                true,
                List.of("host.docker.internal")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.artifacts()).hasSize(1);
        assertThat(runner.lastCommand.commandLine())
                .containsSubsequence("--add-host", "host.docker.internal:host-gateway")
                .containsSubsequence("--add-host", "proxy.docker.internal:host-gateway")
                .doesNotContain("--network");
    }

    @Test
    void shouldInjectConfiguredBrowserProxyAuthentication() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("browser snapshot completed; proxy=True\n", Duration.ofMillis(320)),
                command -> {
                    String script = Files.readString(command.workingDirectory().resolve("main.py"));
                    assertThat(script)
                            .contains("browser_proxy_server = \"http://proxy.docker.internal:18082\"",
                                    "browser_proxy_username = \"proxy-user\"",
                                    "browser_proxy_password = \"proxy-password-secret\"",
                                    "proxy_options[\"username\"] = browser_proxy_username",
                                    "proxy_options[\"password\"] = browser_proxy_password",
                                    "\"authenticated\": bool(target_url and browser_proxy_server and browser_proxy_username and browser_proxy_password)",
                                    "\"egress\": egress_summary",
                                    "proxyAuthenticated={egress_summary['proxy']['authenticated']}");
                    Files.writeString(command.workingDirectory().resolve("browser-result.json"),
                            """
                                    {"action":"snapshot","source":"url","url":"http://host.docker.internal:18080/page","allowedHosts":["host.docker.internal"],"proxy":{"enabled":true,"authenticated":true},"text":"proxy auth marker"}
                                    """);
                });
        ContainerSandboxRuntimeAdapter adapter = adapterWithBrowserProxy(
                runner,
                "http://proxy.docker.internal:18082",
                "proxy-user",
                "proxy-password-secret");
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://host.docker.internal:18080/page","allowedHosts":["host.docker.internal"],"screenshot":false,"har":false,"video":false}
                        """,
                true,
                List.of("host.docker.internal")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.artifacts()).hasSize(1);
        assertThat(runner.lastCommand.commandLine())
                .containsSubsequence("--add-host", "host.docker.internal:host-gateway")
                .containsSubsequence("--add-host", "proxy.docker.internal:host-gateway")
                .doesNotContain("--network");
    }

    @Test
    void shouldRotateConfiguredBrowserProxyServersForUrlMode() throws Exception {
        AtomicInteger runIndex = new AtomicInteger();
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("browser snapshot completed; proxy=True\n", Duration.ofMillis(320)),
                command -> {
                    int index = runIndex.getAndIncrement();
                    String expectedProxy = index == 0
                            ? "http://proxy-a.docker.internal:18082"
                            : "http://proxy-b.docker.internal:18083";
                    String script = Files.readString(command.workingDirectory().resolve("main.py"));
                    assertThat(script)
                            .contains("browser_proxy_server = \"%s\"".formatted(expectedProxy),
                                    "browser_proxy_pool_size = 2",
                                    "\"poolSize\": browser_proxy_pool_size",
                                    "\"rotationEnabled\": bool(browser_proxy_pool_size > 1)",
                                    "proxyPoolSize={egress_summary['proxy']['poolSize']}",
                                    "proxyRotation={egress_summary['proxy']['rotationEnabled']}");
                    Files.writeString(command.workingDirectory().resolve("browser-result.json"),
                            """
                                    {"action":"snapshot","source":"url","url":"http://host.docker.internal:18080/page","allowedHosts":["host.docker.internal"],"proxy":{"enabled":true,"poolSize":2,"rotationEnabled":true},"text":"proxy rotation marker"}
                                    """);
                });
        ContainerSandboxRuntimeAdapter adapter = adapterWithBrowserProxyServers(
                runner,
                "http://proxy-a.docker.internal:18082, http://proxy-b.docker.internal:18083");

        SandboxExecutionResult first = adapter.execute(new SandboxExecutionRequest(
                adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION)),
                """
                        {"action":"snapshot","url":"http://host.docker.internal:18080/page","allowedHosts":["host.docker.internal"],"screenshot":false,"har":false,"video":false}
                        """,
                true,
                List.of("host.docker.internal")));
        SandboxExecutionResult second = adapter.execute(new SandboxExecutionRequest(
                adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION)),
                """
                        {"action":"snapshot","url":"http://host.docker.internal:18080/page","allowedHosts":["host.docker.internal"],"screenshot":false,"har":false,"video":false}
                        """,
                true,
                List.of("host.docker.internal")));

        assertThat(first.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(second.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(runner.commands).hasSize(2);
        assertThat(runner.commands.get(0).commandLine())
                .containsSubsequence("--add-host", "proxy-a.docker.internal:host-gateway")
                .containsSubsequence("--add-host", "proxy-b.docker.internal:host-gateway");
        assertThat(runner.commands.get(1).commandLine())
                .containsSubsequence("--add-host", "proxy-a.docker.internal:host-gateway")
                .containsSubsequence("--add-host", "proxy-b.docker.internal:host-gateway")
                .doesNotContain("--network");
    }

    @Test
    void shouldMapRequestedDockerInternalAllowedHostsForBrowserUrlMode() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("browser snapshot completed\n", Duration.ofMillis(320)),
                command -> {
                    String script = Files.readString(command.workingDirectory().resolve("main.py"));
                    assertThat(script)
                            .contains("allowed_hosts = set([\"host.docker.internal\",\"assets.docker.internal\"])",
                                    "return resolved_host_decision(host)");
                    Files.writeString(command.workingDirectory().resolve("browser-result.json"),
                            """
                                    {"action":"snapshot","source":"url","url":"http://host.docker.internal:18080/page","allowedHosts":["assets.docker.internal","host.docker.internal"],"text":"url mode marker"}
                                    """);
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://host.docker.internal:18080/page","allowedHosts":["host.docker.internal","assets.docker.internal"],"screenshot":false,"har":false,"video":false}
                        """,
                true,
                List.of("host.docker.internal", "assets.docker.internal")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(runner.lastCommand.commandLine())
                .containsSubsequence("--add-host", "host.docker.internal:host-gateway")
                .containsSubsequence("--add-host", "assets.docker.internal:host-gateway")
                .doesNotContain("--network");
    }

    @Test
    void shouldFailClosedWhenBrowserAllowedHostsDoNotMatchPolicyRequestedHosts() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://evil.example/page","allowedHosts":["evil.example"]}
                        """,
                true,
                List.of("allowed.example")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("allowedHosts must be included in requestedHosts");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserSessionStateCaptureIsRequestedForInlineHtml() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","html":"<main>inline</main>","captureSessionState":true}
                        """,
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("session state capture is only supported for url mode");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserSessionStateReplayIsRequestedForInlineHtml() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","html":"<main>inline</main>","sessionState":{"cookies":[]}}
                        """,
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("session state replay is only supported for url mode");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserSessionStateCookieUsesLeadingDotDomainBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/page","allowedHosts":["example.test"],"sessionState":{"cookies":[{"name":"restored_session","value":"restored-secret-value","domain":".example.test","path":"/"}]}}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("cookie domain must be a host name only");
        assertThat(result.execution().resultSummary()).doesNotContain("restored-secret-value");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserSessionStateContainsUnsupportedFieldsBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/page","allowedHosts":["example.test"],"sessionState":{"cookies":[{"name":"restored_session","value":"restored-secret-value","domain":"example.test","path":"/","storageRef":"secret-storage-ref"}]}}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("sessionState cookie contains unsupported fields");
        assertThat(result.execution().resultSummary()).doesNotContain("restored-secret-value");
        assertThat(result.execution().resultSummary()).doesNotContain("secret-storage-ref");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserSessionStateOriginIsNotAllowlistedBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/page","allowedHosts":["example.test"],"sessionState":{"origins":[{"origin":"http://other.test","localStorage":[{"name":"seahorse_session_marker","value":"secret"}]}]}}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("sessionState origin host must be included in allowedHosts");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserSessionStateOriginDoesNotMatchTargetUrlHostBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/page","allowedHosts":["example.test","other.test"],"sessionState":{"origins":[{"origin":"http://other.test","localStorage":[{"name":"seahorse_session_marker","value":"secret"}]}]}}
                        """,
                true,
                List.of("example.test", "other.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("sessionState origin host must match the target URL host");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserSessionStateOriginDoesNotMatchTargetUrlOriginBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test:8080/page","allowedHosts":["example.test"],"sessionState":{"origins":[{"origin":"http://example.test:9090","localStorage":[{"name":"seahorse_session_marker","value":"secret"}]}]}}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("sessionState origin must match the target URL origin");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserSessionStateOriginContainsCredentialPartsBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/page","allowedHosts":["example.test"],"sessionState":{"origins":[{"origin":"http://alice:secret@example.test/path?token=secret#frag","localStorage":[{"name":"marker","value":"value"}]}]}}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("sessionState origin must be an origin only");
        assertThat(result.execution().resultSummary()).doesNotContain("alice:secret");
        assertThat(result.execution().resultSummary()).doesNotContain("token=secret");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlHostIsNotAllowlistedBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/page","allowedHosts":["other.test"]}
                        """,
                true,
                List.of("other.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("url host must be included in allowedHosts");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesLocalhostBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://localhost:8080/admin","allowedHosts":["localhost"]}
                        """,
                true,
                List.of("localhost")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("not localhost or an IP literal");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesIpLiteralBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://127.0.0.1:8080/admin","allowedHosts":["127.0.0.1"]}
                        """,
                true,
                List.of("127.0.0.1")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("not localhost or an IP literal");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesIpv6LiteralBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://[::1]:8080/admin","allowedHosts":["example.test"]}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("not localhost or an IP literal");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesSingleLabelHostBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://metadata/admin","allowedHosts":["metadata"]}
                        """,
                true,
                List.of("metadata")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("dotted DNS host");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesMalformedDnsHostBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.test/admin","allowedHosts":["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.test"]}
                        """,
                true,
                List.of("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("valid dotted DNS host");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesUserinfoBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://alice:secret@example.test/admin","allowedHosts":["example.test"]}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("must not include userinfo credentials");
        assertThat(result.execution().resultSummary()).doesNotContain("alice:secret");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesFragmentBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/admin#access_token=secret","allowedHosts":["example.test"]}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("must not include fragment identifiers");
        assertThat(result.execution().resultSummary()).doesNotContain("access_token=secret");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesCredentialQueryBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/admin?access_token=secret","allowedHosts":["example.test"]}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("url query must not include credential parameters");
        assertThat(result.execution().resultSummary()).doesNotContain("access_token=secret");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlQueryExceedsLimitBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));
        String queryMarker = "oversized-query-marker-" + "a".repeat(513);

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/search?q=%s","allowedHosts":["example.test"]}
                        """.formatted(queryMarker),
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("url query exceeds 512 chars");
        assertThat(result.execution().resultSummary()).doesNotContain(queryMarker);
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesEncodedCredentialQueryBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/admin?access%5Ftoken=secret","allowedHosts":["example.test"]}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("url query must not include credential parameters");
        assertThat(result.execution().resultSummary()).doesNotContain("access%5Ftoken=secret");
        assertThat(result.execution().resultSummary()).doesNotContain("secret");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesSemicolonCredentialQueryBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/admin?q=roadmap;access_token=secret","allowedHosts":["example.test"]}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("url query must not include credential parameters");
        assertThat(result.execution().resultSummary()).doesNotContain("access_token=secret");
        assertThat(result.execution().resultSummary()).doesNotContain("secret");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesBracketCredentialQueryBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/admin?access_token[]=secret","allowedHosts":["example.test"]}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("url query must not include credential parameters");
        assertThat(result.execution().resultSummary()).doesNotContain("access_token[]=secret");
        assertThat(result.execution().resultSummary()).doesNotContain("secret");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserUrlUsesVariantCredentialQueryBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/admin?sessionToken=secret-session-value&client-secret=secret-client-value","allowedHosts":["example.test"]}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("url query must not include credential parameters");
        assertThat(result.execution().resultSummary()).doesNotContain("sessionToken=secret-session-value");
        assertThat(result.execution().resultSummary()).doesNotContain("client-secret=secret-client-value");
        assertThat(result.execution().resultSummary()).doesNotContain("secret-session-value");
        assertThat(result.execution().resultSummary()).doesNotContain("secret-client-value");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldAllowBrowserUrlWithNonCredentialQueryBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("browser snapshot completed\n", Duration.ofMillis(100)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("target_url = \"http://example.test/search?q=roadmap\"");
                    Files.writeString(command.workingDirectory().resolve("browser-result.json"),
                            """
                                    {"action":"snapshot","source":"url","url":"http://example.test/search?q=roadmap","text":"query marker"}
                                    """);
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/search?q=roadmap","allowedHosts":["example.test"]}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(runner.lastCommand).isNotNull();
    }

    @Test
    void shouldFailClosedWhenBrowserCookieDomainIsNotAllowlistedBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/page","allowedHosts":["example.test"],"cookies":[{"name":"seahorse_session","value":"secret","domain":"other.test"}]}
                        """,
                true,
                List.of("example.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("cookie domain must be included in allowedHosts");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenBrowserCookieDomainDoesNotMatchTargetUrlHostBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.BROWSER_AUTOMATION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"action":"snapshot","url":"http://example.test/page","allowedHosts":["example.test","other.test"],"cookies":[{"name":"seahorse_session","value":"secret","domain":"other.test"}]}
                        """,
                true,
                List.of("example.test", "other.test")));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("cookie domain must match the target URL host");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldRejectUnsupportedFileConversionPairBeforeRunningContainer() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"csv","targetFormat":"tsv","content":"name,score\\nAda,42\\n"}
                        """,
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED);
        assertThat(result.execution().resultSummary())
                .contains("supports csv/tsv to json, csv to xlsx, json to csv/tsv, txt to html, html to txt/docx, markdown/md to html/txt, docx/odt/odp/pdf to html/txt, docx/odt/ods/odp/pptx/xlsx to pdf, docx/odt/ods/odp/pdf/pptx/xlsx to png, pdf to ocr_txt, xlsx/ods to csv/html, and pptx to html/txt only");
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldFailClosedWhenContainerRunnerThrows() {
        RecordingRunner runner = new RecordingRunner(new IOException("docker missing"));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('nope')",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("docker missing");
    }

    @Test
    void shouldUseConfiguredMountSourceRootWhileWritingToWorkspaceRoot() throws Exception {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                "mount source smoke\n",
                Duration.ofMillis(100)));
        ContainerSandboxAdapterProperties properties = properties();
        Path containerWorkspaceRoot = tempDir.resolve("container-workspaces");
        Path hostMountSourceRoot = tempDir.resolve("host-workspaces");
        properties.setWorkspaceRoot(containerWorkspaceRoot.toString());
        properties.setWorkspaceMountSourceRoot(hostMountSourceRoot.toString());
        ContainerSandboxRuntimeAdapter adapter = new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);

        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));
        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('mount source smoke')",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(runner.lastCommand.workingDirectory())
                .isEqualTo(containerWorkspaceRoot.resolve(session.sessionId()).toAbsolutePath().normalize());
        assertThat(Files.readString(runner.lastCommand.workingDirectory().resolve("main.py")))
                .isEqualTo("print('mount source smoke')");
        assertThat(runner.lastCommand.commandLine())
                .contains(hostMountSourceRoot + "/" + session.sessionId() + ":/workspace:rw");
    }

    @Test
    void shouldMarkExecutionTimedOut() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.timedOut(
                "",
                "still running",
                Duration.ofSeconds(30)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "while True: pass",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.TIMED_OUT);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_TIMED_OUT);
        assertThat(result.execution().resultSummary()).contains("timed out", "still running");
    }

    @Test
    void shouldReturnUnsupportedForNonCodeInterpreterRuntime() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.SHELL));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "echo unsafe",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED);
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldDeleteSessionWorkspaceOnClose() throws Exception {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));
        Path workspace = tempDir.resolve(session.sessionId());
        assertThat(workspace).exists();

        SandboxSession closed = adapter.closeSession(session);

        assertThat(closed.status()).isEqualTo(SandboxExecutionStatus.CANCELLED);
        assertThat(workspace).doesNotExist();
    }

    @Test
    void shouldSweepOnlyOldOrphanedManagedWorkspaces() throws Exception {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxAdapterProperties properties = properties();
        properties.setOrphanWorkspaceMinAge(Duration.ofMinutes(10));
        ContainerSandboxRuntimeAdapter adapter = new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);
        Path active = createWorkspace("sandbox_container_active", CLOCK.instant().minusSeconds(3600));
        Path orphan = createWorkspace("sandbox_container_orphan", CLOCK.instant().minusSeconds(3600));
        Path recent = createWorkspace("sandbox_container_recent", CLOCK.instant());
        Path unmanaged = createWorkspace("not_sandbox_container_orphan", CLOCK.instant().minusSeconds(3600));

        SandboxRuntimeCleanupResult result = adapter.sweepOrphanedResources(Set.of("sandbox_container_active"));

        assertThat(result.activeSessionCount()).isEqualTo(1);
        assertThat(result.inspectedWorkspaceCount()).isEqualTo(3);
        assertThat(result.skippedActiveWorkspaceCount()).isEqualTo(1);
        assertThat(result.skippedRecentWorkspaceCount()).isEqualTo(1);
        assertThat(result.removedWorkspaceCount()).isEqualTo(1);
        assertThat(result.failedWorkspaceCount()).isZero();
        assertThat(result.removedWorkspaceNames()).containsExactly("sandbox_container_orphan");
        assertThat(active).exists();
        assertThat(orphan).doesNotExist();
        assertThat(recent).exists();
        assertThat(unmanaged).exists();
    }

    @Test
    void shouldInspectManagedContainersAndClassifyOrphans() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                """
                        seahorse-sandbox-sandbox_container_active\tUp 10 seconds
                        seahorse-sandbox-orphan-live\tUp 2 minutes
                        unrelated-container\tUp 1 hour
                        """,
                Duration.ofMillis(80)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);

        SandboxRuntimeCleanupResult result = adapter.sweepOrphanedResources(Set.of("sandbox_container_active"));

        assertThat(result.inspectedContainerCount()).isEqualTo(2);
        assertThat(result.activeContainerCount()).isEqualTo(1);
        assertThat(result.orphanContainerCount()).isEqualTo(1);
        assertThat(result.failedContainerInspectionCount()).isZero();
        assertThat(result.activeContainerNames())
                .containsExactly("seahorse-sandbox-sandbox_container_active");
        assertThat(result.orphanContainerNames())
                .containsExactly("seahorse-sandbox-orphan-live");
        assertThat(runner.lastCommand.commandLine())
                .containsSubsequence("docker", "ps", "-a")
                .containsSubsequence("--filter", "name=seahorse-sandbox-")
                .containsSubsequence("--format", "{{.Names}}\t{{.Status}}");
    }

    @Test
    void shouldKeepWorkspaceSweepResultWhenContainerInspectionFails() throws Exception {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.failed(
                125,
                "",
                "Cannot connect to Docker daemon",
                Duration.ofMillis(50)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        Path orphan = createWorkspace("sandbox_container_orphan", CLOCK.instant().minusSeconds(3600));

        SandboxRuntimeCleanupResult result = adapter.sweepOrphanedResources(Set.of());

        assertThat(result.removedWorkspaceCount()).isEqualTo(1);
        assertThat(result.failedWorkspaceCount()).isZero();
        assertThat(result.failedContainerInspectionCount()).isEqualTo(1);
        assertThat(result.failedContainerInspectionMessages())
                .singleElement()
                .satisfies(message -> assertThat(message).contains("exitCode=125", "Cannot connect"));
        assertThat(orphan).doesNotExist();
    }

    @Test
    void shouldInspectRuntimeHealthFromWorkspaceAndManagedContainers() throws IOException {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                """
                        seahorse-sandbox-sandbox_container_active\tUp 10 seconds
                        seahorse-sandbox-orphan-live\tUp 2 minutes
                        """,
                Duration.ofMillis(80)));
        ContainerSandboxAdapterProperties properties = properties();
        properties.setBrowserPrivateNetworkAllowedHosts("host.docker.internal,assets.docker.internal");
        ContainerSandboxRuntimeAdapter adapter = new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);
        Files.createDirectory(tempDir.resolve("sandbox_container_active"));

        SandboxRuntimeHealth health = adapter.inspectHealth(Set.of(
                "sandbox_container_active",
                "sandbox_remote_active"));

        assertThat(health.runtime()).isEqualTo("container");
        assertThat(health.engine()).isEqualTo("docker");
        assertThat(health.status()).isEqualTo(SandboxRuntimeHealth.STATUS_DEGRADED);
        assertThat(health.engineAvailable()).isTrue();
        assertThat(health.workspaceAvailable()).isTrue();
        assertThat(health.workspaceFreeBytes()).isGreaterThanOrEqualTo(0L);
        assertThat(health.workspaceMinFreeBytes()).isZero();
        assertThat(health.workspaceDiskAvailable()).isTrue();
        assertThat(health.workspaceDiskStatus()).isEqualTo(SandboxRuntimeHealth.DISK_UNBOUNDED);
        assertThat(health.activeSessionCount()).isEqualTo(1);
        assertThat(health.activeSessionLimit()).isZero();
        assertThat(health.activeSessionRemaining()).isZero();
        assertThat(health.activeSessionCapacityAvailable()).isTrue();
        assertThat(health.capacityStatus()).isEqualTo(SandboxRuntimeHealth.CAPACITY_UNBOUNDED);
        assertThat(health.inspectedContainerCount()).isEqualTo(2);
        assertThat(health.activeContainerCount()).isEqualTo(1);
        assertThat(health.orphanContainerCount()).isEqualTo(1);
        assertThat(health.activeContainerNames())
                .containsExactly("seahorse-sandbox-sandbox_container_active");
        assertThat(health.orphanContainerNames())
                .containsExactly("seahorse-sandbox-orphan-live");
        assertThat(health.browserPrivateNetworkAllowedHosts())
                .containsExactly("host.docker.internal", "assets.docker.internal");
        assertThat(health.failureMessages()).isEmpty();
    }

    @Test
    void shouldReportRuntimeHealthUnavailableWhenContainerInspectionFails() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.failed(
                125,
                "",
                "Cannot connect to Docker daemon",
                Duration.ofMillis(50)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);

        SandboxRuntimeHealth health = adapter.inspectHealth(Set.of());

        assertThat(health.status()).isEqualTo(SandboxRuntimeHealth.STATUS_UNAVAILABLE);
        assertThat(health.engineAvailable()).isFalse();
        assertThat(health.workspaceAvailable()).isTrue();
        assertThat(health.failedContainerInspectionCount()).isEqualTo(1);
        assertThat(health.failureMessages())
                .singleElement()
                .satisfies(message -> assertThat(message).contains("exitCode=125", "Cannot connect"));
    }

    @Test
    void shouldReportRuntimeHealthDegradedWhenWorkspaceDiskThresholdIsNotMet() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                "",
                Duration.ofMillis(80)));
        ContainerSandboxAdapterProperties properties = properties();
        properties.setMinWorkspaceFreeBytes(Long.MAX_VALUE);
        ContainerSandboxRuntimeAdapter adapter = new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);

        SandboxRuntimeHealth health = adapter.inspectHealth(Set.of());

        assertThat(health.status()).isEqualTo(SandboxRuntimeHealth.STATUS_DEGRADED);
        assertThat(health.workspaceAvailable()).isTrue();
        assertThat(health.workspaceFreeBytes()).isGreaterThanOrEqualTo(0L);
        assertThat(health.workspaceMinFreeBytes()).isEqualTo(Long.MAX_VALUE);
        assertThat(health.workspaceDiskAvailable()).isFalse();
        assertThat(health.workspaceDiskStatus()).isEqualTo(SandboxRuntimeHealth.DISK_LOW);
        assertThat(health.failureMessages()).isEmpty();
    }

    @Test
    void shouldReportRuntimeHealthCapacitySaturation() throws IOException {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                "seahorse-sandbox-sandbox_container_active\tUp 10 seconds\n",
                Duration.ofMillis(80)));
        ContainerSandboxAdapterProperties properties = properties();
        properties.setMaxActiveSessions(1);
        ContainerSandboxRuntimeAdapter adapter = new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);
        Files.createDirectory(tempDir.resolve("sandbox_container_active"));

        SandboxRuntimeHealth health = adapter.inspectHealth(Set.of("sandbox_container_active"));

        assertThat(health.status()).isEqualTo(SandboxRuntimeHealth.STATUS_DEGRADED);
        assertThat(health.activeSessionCount()).isEqualTo(1);
        assertThat(health.activeSessionLimit()).isEqualTo(1);
        assertThat(health.activeSessionRemaining()).isZero();
        assertThat(health.activeSessionCapacityAvailable()).isFalse();
        assertThat(health.capacityStatus()).isEqualTo(SandboxRuntimeHealth.CAPACITY_SATURATED);
        assertThat(health.failureMessages()).isEmpty();
    }

    @Test
    void shouldDryRunOrphanContainerReapWithoutRemovingContainers() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                """
                        seahorse-sandbox-sandbox_container_active\tUp 10 seconds
                        seahorse-sandbox-orphan-live\tUp 2 minutes
                        """,
                Duration.ofMillis(80)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);

        SandboxRuntimeContainerReapResult result = adapter.reapOrphanedContainers(
                Set.of("sandbox_container_active"),
                true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.activeSessionCount()).isEqualTo(1);
        assertThat(result.inspectedContainerCount()).isEqualTo(2);
        assertThat(result.activeContainerNames())
                .containsExactly("seahorse-sandbox-sandbox_container_active");
        assertThat(result.orphanContainerNames())
                .containsExactly("seahorse-sandbox-orphan-live");
        assertThat(result.reapedContainerCount()).isZero();
        assertThat(result.failedContainerCount()).isZero();
        assertThat(runner.commands).hasSize(1);
        assertThat(runner.commands.getFirst().commandLine())
                .containsSubsequence("docker", "ps", "-a");
    }

    @Test
    void shouldReapOnlyOrphanManagedContainers() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                """
                        seahorse-sandbox-sandbox_container_active\tUp 10 seconds
                        seahorse-sandbox-orphan-live\tUp 2 minutes
                        """,
                Duration.ofMillis(80)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);

        SandboxRuntimeContainerReapResult result = adapter.reapOrphanedContainers(
                Set.of("sandbox_container_active"),
                false);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.reapedContainerCount()).isEqualTo(1);
        assertThat(result.failedContainerCount()).isZero();
        assertThat(result.reapedContainerNames())
                .containsExactly("seahorse-sandbox-orphan-live");
        assertThat(result.activeContainerNames())
                .containsExactly("seahorse-sandbox-sandbox_container_active");
        assertThat(runner.commands).hasSize(2);
        assertThat(runner.commands.get(1).commandLine())
                .containsExactly("docker", "rm", "-f", "seahorse-sandbox-orphan-live");
    }

    private Path createWorkspace(String name, Instant modifiedAt) throws IOException {
        Path workspace = tempDir.resolve(name);
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("marker.txt"), name);
        FileTime fileTime = FileTime.from(modifiedAt);
        Files.setLastModifiedTime(workspace.resolve("marker.txt"), fileTime);
        Files.setLastModifiedTime(workspace, fileTime);
        return workspace;
    }

    private ContainerSandboxRuntimeAdapter adapter(RecordingRunner runner) {
        return new ContainerSandboxRuntimeAdapter(properties(), runner, CLOCK);
    }

    private ContainerSandboxRuntimeAdapter adapterWithBrowserProxy(RecordingRunner runner, String browserProxyServer) {
        ContainerSandboxAdapterProperties properties = properties();
        properties.setBrowserProxyServer(browserProxyServer);
        return new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);
    }

    private ContainerSandboxRuntimeAdapter adapterWithBrowserProxy(
            RecordingRunner runner,
            String browserProxyServer,
            String browserProxyUsername,
            String browserProxyPassword) {
        ContainerSandboxAdapterProperties properties = properties();
        properties.setBrowserProxyServer(browserProxyServer);
        properties.setBrowserProxyUsername(browserProxyUsername);
        properties.setBrowserProxyPassword(browserProxyPassword);
        return new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);
    }

    private ContainerSandboxRuntimeAdapter adapterWithBrowserProxyServers(
            RecordingRunner runner,
            String browserProxyServers) {
        ContainerSandboxAdapterProperties properties = properties();
        properties.setBrowserProxyServers(browserProxyServers);
        return new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);
    }

    private ContainerSandboxAdapterProperties properties() {
        ContainerSandboxAdapterProperties properties = new ContainerSandboxAdapterProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        properties.setMemory("128m");
        properties.setCpus("0.5");
        properties.setPidsLimit(64L);
        properties.setExecutionTimeout(Duration.ofSeconds(3));
        return properties;
    }

    private SandboxSessionRequest sessionRequest(SandboxRuntimeType runtimeType) {
        return new SandboxSessionRequest("default", "run-1", runtimeType, false, List.of());
    }

    private static byte[] zipBytes(String entryName, String content) throws IOException {
        return zipBytes(entryName, content, null, null);
    }

    private static byte[] xlsxBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
            writeZipEntry(archive, "[Content_Types].xml", "<Types/>");
            writeZipEntry(archive, "xl/workbook.xml",
                    """
                            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                              <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"/></sheets>
                            </workbook>
                            """);
            writeZipEntry(archive, "xl/sharedStrings.xml",
                    """
                            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                              <si><t>name</t></si><si><t>score</t></si><si><t>Ada</t></si>
                            </sst>
                            """);
            writeZipEntry(archive, "xl/worksheets/sheet1.xml",
                    """
                            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                              <sheetData>
                                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                                <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2"><v>42</v></c></row>
                              </sheetData>
                            </worksheet>
                            """);
        }
        return bytes.toByteArray();
    }

    private static byte[] odtBytes(String firstParagraph, String secondParagraph) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
            writeZipEntry(archive, "mimetype", "application/vnd.oasis.opendocument.text");
            writeZipEntry(archive, "content.xml",
                    """
                            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                                     xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                              <office:body>
                                <office:text>
                                  <text:p>%s</text:p>
                                  <text:p>%s</text:p>
                                </office:text>
                              </office:body>
                            </office:document-content>
                            """.formatted(firstParagraph, secondParagraph));
        }
        return bytes.toByteArray();
    }

    private static byte[] odsBytes(String marker, String secondValue) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
            writeZipEntry(archive, "mimetype", "application/vnd.oasis.opendocument.spreadsheet");
            writeZipEntry(archive, "content.xml",
                    """
                            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                                     xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0"
                                                     xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                              <office:body>
                                <office:spreadsheet>
                                  <table:table table:name="Sheet1">
                                    <table:table-row>
                                      <table:table-cell><text:p>label</text:p></table:table-cell>
                                      <table:table-cell><text:p>value</text:p></table:table-cell>
                                    </table:table-row>
                                    <table:table-row>
                                      <table:table-cell><text:p>%s</text:p></table:table-cell>
                                      <table:table-cell><text:p>%s</text:p></table:table-cell>
                                    </table:table-row>
                                  </table:table>
                                </office:spreadsheet>
                              </office:body>
                            </office:document-content>
                            """.formatted(marker, secondValue));
        }
        return bytes.toByteArray();
    }

    private static byte[] odpBytes(String firstSlideText, String secondSlideText) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
            writeZipEntry(archive, "mimetype", "application/vnd.oasis.opendocument.presentation");
            writeZipEntry(archive, "content.xml",
                    """
                            <office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                                                     xmlns:draw="urn:oasis:names:tc:opendocument:xmlns:drawing:1.0"
                                                     xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                              <office:body>
                                <office:presentation>
                                  <draw:page draw:name="page1">
                                    <draw:frame><draw:text-box><text:p>%s</text:p></draw:text-box></draw:frame>
                                  </draw:page>
                                  <draw:page draw:name="page2">
                                    <draw:frame><draw:text-box><text:p>%s</text:p></draw:text-box></draw:frame>
                                  </draw:page>
                                </office:presentation>
                              </office:body>
                            </office:document-content>
                            """.formatted(firstSlideText, secondSlideText));
        }
        return bytes.toByteArray();
    }

    private static byte[] pptxBytes() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
            writeZipEntry(archive, "[Content_Types].xml", "<Types/>");
            writeZipEntry(archive, "ppt/presentation.xml",
                    """
                            <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"/>
                            """);
            writeZipEntry(archive, "ppt/slides/slide1.xml",
                    """
                            <p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                                   xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">
                              <p:cSld>
                                <p:spTree>
                                  <p:sp><p:txBody><a:p><a:r><a:t>Sandbox PPTX Title</a:t></a:r></a:p></p:txBody></p:sp>
                                  <p:sp><p:txBody><a:p><a:r><a:t>Slide body</a:t></a:r></a:p></p:txBody></p:sp>
                                </p:spTree>
                              </p:cSld>
                            </p:sld>
                            """);
        }
        return bytes.toByteArray();
    }

    private static byte[] zipBytes(String firstEntryName,
                                   String firstContent,
                                   String secondEntryName,
                                   String secondContent) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
            writeZipEntry(archive, firstEntryName, firstContent);
            if (secondEntryName != null) {
                writeZipEntry(archive, secondEntryName, secondContent);
            }
        }
        return bytes.toByteArray();
    }

    private static void writeZipEntry(ZipOutputStream archive, String entryName, String content) throws IOException {
        archive.putNextEntry(new ZipEntry(entryName));
        archive.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        archive.closeEntry();
    }

    private static final class RecordingRunner implements ContainerCommandRunner {

        private final ContainerCommandResult result;
        private final IOException exception;
        private final OnRun onRun;
        private final List<ContainerCommand> commands = new java.util.ArrayList<>();
        private ContainerCommand lastCommand;

        private RecordingRunner(ContainerCommandResult result) {
            this.result = result;
            this.exception = null;
            this.onRun = null;
        }

        private RecordingRunner(ContainerCommandResult result, OnRun onRun) {
            this.result = result;
            this.exception = null;
            this.onRun = onRun;
        }

        private RecordingRunner(IOException exception) {
            this.result = null;
            this.exception = exception;
            this.onRun = null;
        }

        @Override
        public ContainerCommandResult run(ContainerCommand command) throws IOException {
            lastCommand = command;
            commands.add(command);
            if (exception != null) {
                throw exception;
            }
            if (onRun != null) {
                onRun.accept(command);
            }
            return result;
        }
    }

    private interface OnRun {

        void accept(ContainerCommand command) throws IOException;
    }
}
