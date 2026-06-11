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

package com.miracle.ai.seahorse.agent.adapters.spring.config;

import com.miracle.ai.seahorse.agent.kernel.config.KernelRuntimeMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Spring Boot binding model for Seahorse kernel runtime options.
 */
@ConfigurationProperties(prefix = "seahorse.agent.kernel")
public class AgentKernelProperties {

    public static final KernelRuntimeMode DEFAULT_MODE = KernelRuntimeMode.KERNEL;
    public static final Duration DEFAULT_EXTENSION_RESOLVE_TIMEOUT = Duration.ofMillis(50L);

    private String mode = DEFAULT_MODE.value();
    private Duration extensionResolveTimeout = DEFAULT_EXTENSION_RESOLVE_TIMEOUT;
    private boolean failFastOnMissingDefault = true;
    private boolean strictBoundaryCheck;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = Objects.requireNonNullElse(mode, DEFAULT_MODE.value());
    }

    public KernelRuntimeMode resolveMode() {
        return KernelRuntimeMode.from(mode);
    }

    public Duration getExtensionResolveTimeout() {
        return extensionResolveTimeout;
    }

    public void setExtensionResolveTimeout(Duration extensionResolveTimeout) {
        this.extensionResolveTimeout = Objects.requireNonNullElse(extensionResolveTimeout,
                DEFAULT_EXTENSION_RESOLVE_TIMEOUT);
    }

    public boolean isFailFastOnMissingDefault() {
        return failFastOnMissingDefault;
    }

    public void setFailFastOnMissingDefault(boolean failFastOnMissingDefault) {
        this.failFastOnMissingDefault = failFastOnMissingDefault;
    }

    public boolean isStrictBoundaryCheck() {
        return strictBoundaryCheck;
    }

    public void setStrictBoundaryCheck(boolean strictBoundaryCheck) {
        this.strictBoundaryCheck = strictBoundaryCheck;
    }
}
