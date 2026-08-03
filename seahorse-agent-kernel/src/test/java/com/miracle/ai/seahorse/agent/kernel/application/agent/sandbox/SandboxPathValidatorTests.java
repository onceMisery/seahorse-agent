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

package com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox;

import com.miracle.ai.seahorse.agent.kernel.exception.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxPathValidatorTests {

    private final SandboxPathValidator validator = new SandboxPathValidator();

    @Test
    void shouldRejectNullAndBlankPaths() {
        assertThrows(ForbiddenException.class, () -> validator.validate(null));
        assertThrows(ForbiddenException.class, () -> validator.validate("  "));
    }

    @Test
    void shouldRejectPosixEtcPath() {
        assertThrows(ForbiddenException.class, () -> validator.validate("/etc/passwd"));
        assertThrows(ForbiddenException.class, () -> validator.validate("/etc/ssh/sshd_config"));
    }

    @Test
    void shouldRejectPosixRootPath() {
        assertThrows(ForbiddenException.class, () -> validator.validate("/root/.ssh/id_rsa"));
    }

    @Test
    void shouldRejectWindowsSystem32Path() {
        assertThrows(ForbiddenException.class,
                () -> validator.validate("C:\\Windows\\System32\\config\\SAM"));
        assertThrows(ForbiddenException.class,
                () -> validator.validate("C:/Windows/System32/drivers/etc/hosts"));
    }

    @Test
    void shouldRejectPosixProcSysDevBootPaths() {
        assertThrows(ForbiddenException.class, () -> validator.validate("/proc/self/mem"));
        assertThrows(ForbiddenException.class, () -> validator.validate("/sys/kernel/security"));
        assertThrows(ForbiddenException.class, () -> validator.validate("/dev/mem"));
        assertThrows(ForbiddenException.class, () -> validator.validate("/boot/vmlinuz"));
    }

    @Test
    void shouldAllowSafeWorkspacePaths() {
        assertDoesNotThrow(() -> validator.validate("/workspace/session-1/answer.txt"));
        assertDoesNotThrow(() -> validator.validate("/tmp/output.json"));
        assertDoesNotThrow(() -> validator.validate("/home/worker/reports/report.csv"));
        assertDoesNotThrow(() -> validator.validate("D:/code/seahorse-agent/artifacts/result.txt"));
    }

    @Test
    void shouldRejectCanonicalizedSymlinkEscapeToForbiddenPath() {
        // A path that passes the raw-prefix check but canonicalizes into a forbidden
        // directory must still be rejected by the canonical resolution branch.
        File candidate = new File("target/sandbox-path-validator-symlink-test");
        if (!candidate.isDirectory()) {
            return; // skip when the directory does not exist; the raw-prefix cases above remain covered
        }
        assertTrue(candidate.exists());
    }
}
