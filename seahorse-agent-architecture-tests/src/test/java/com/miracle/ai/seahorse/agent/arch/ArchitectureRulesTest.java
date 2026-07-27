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

package com.miracle.ai.seahorse.agent.arch;

import org.junit.jupiter.api.Test;

/**
 * Aggregate entry point for Architecture tests R1-R5
 * Ensures all rules run in single Maven invocation if needed.
 */
public class ArchitectureRulesTest {

    @Test
    void runAllRules() {
        // Delegate to individual rule tests to keep CI output simple
        // JUnit will also discover other Test classes, but this ensures at least one entry
        new R1KernelIsolationTest().kernelShouldNotDependOnAdapters();
        new R1KernelIsolationTest().kernelShouldNotDependOnSpringWeb();
        new R1KernelIsolationTest().kernelShouldNotDependOnHeavySdk();

        new R2DomainIsolationTest().domainShouldNotDependOnApplication();
        new R2DomainIsolationTest().domainShouldNotDependOnAdapters();

        new R3SubdomainIsolationTest().whitelistMustNotContainDuplicateClassPairs();
        new R3SubdomainIsolationTest().subdomainShouldNotDependOnOtherSubdomainUnlessWhitelisted();

        new R4AdapterIsolationTest().adaptersShouldNotDependOnOtherAdapters();

        new R5ControllerDependencyTest().controllerToKernelServiceDependenciesMustMatchPhaseZeroBaseline();
    }
}
