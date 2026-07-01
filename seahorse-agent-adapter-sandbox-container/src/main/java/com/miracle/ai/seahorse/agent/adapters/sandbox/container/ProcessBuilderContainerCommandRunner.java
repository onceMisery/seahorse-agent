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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

final class ProcessBuilderContainerCommandRunner implements ContainerCommandRunner {

    @Override
    public ContainerCommandResult run(ContainerCommand command) throws IOException, InterruptedException {
        Objects.requireNonNull(command, "command must not be null");
        Instant startedAt = Instant.now();
        Process process = new ProcessBuilder(command.commandLine())
                .directory(command.workingDirectory().toFile())
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        CompletableFuture<String> stdout = CompletableFuture.supplyAsync(
                () -> readLimited(process.getInputStream(), command.stdoutLimitBytes()));
        CompletableFuture<String> stderr = CompletableFuture.supplyAsync(
                () -> readLimited(process.getErrorStream(), command.stderrLimitBytes()));
        boolean finished = process.waitFor(command.timeout().toMillis(), TimeUnit.MILLISECONDS);
        Duration duration = Duration.between(startedAt, Instant.now());
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            return ContainerCommandResult.timedOut(join(stdout), join(stderr), duration);
        }
        return new ContainerCommandResult(process.exitValue(), false, join(stdout), join(stderr), duration);
    }

    private static String readLimited(InputStream inputStream, int limitBytes) {
        try (InputStream source = inputStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int retained = 0;
            while (true) {
                int read = source.read(buffer);
                if (read == -1) {
                    break;
                }
                if (retained < limitBytes) {
                    int writable = Math.min(read, limitBytes - retained);
                    output.write(buffer, 0, writable);
                    retained += writable;
                }
            }
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private static String join(CompletableFuture<String> future) {
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | java.util.concurrent.TimeoutException ex) {
            return "";
        }
    }
}
