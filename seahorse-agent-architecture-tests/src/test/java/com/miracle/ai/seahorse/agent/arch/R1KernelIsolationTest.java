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

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * R1: kernel 不依赖 adapter / Spring Web / SDK
 * - kernel classes reside in com.miracle.ai.seahorse.agent.kernel..
 * - should not depend on adapters, spring-web, and heavy SDKs
 */
public class R1KernelIsolationTest {

    private final JavaClasses kernelClasses = new ClassFileImporter()
            .importPackages("com.miracle.ai.seahorse.agent.kernel");

    @Test
    void kernelShouldNotDependOnAdapters() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.miracle.ai.seahorse.agent.kernel..")
                .should().dependOnClassesThat().resideInAPackage("com.miracle.ai.seahorse.agent.adapters..")
                .because("Kernel must be framework-agnostic and not depend on adapter layer (Ports & Adapters)");
        rule.check(kernelClasses);
    }

    @Test
    void kernelShouldNotDependOnSpringWeb() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.miracle.ai.seahorse.agent.kernel..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.web.servlet..",
                        "org.springframework.boot.web.."
                )
                .because("Kernel should not depend on Spring Web; web concerns belong to adapter-web");

        try {
            rule.check(kernelClasses);
            System.out.println("R1 kernel->spring-web: PASS");
        } catch (AssertionError e) {
            System.out.println("R1 kernel->spring-web violations:\n" + e.getMessage());
            throw e; // strict for spring-web, should be 0
        }
    }

    @Test
    void kernelShouldNotDependOnHeavySdk() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.miracle.ai.seahorse.agent.kernel..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "io.milvus..",
                        "org.apache.tika..",
                        "software.amazon.awssdk..",
                        "org.redisson..",
                        "org.apache.pulsar..",
                        "org.apache.lucene..",
                        "org.elasticsearch..",
                        "co.elastic.clients..",
                        "io.agentscope..",
                        "org.springframework.amqp.."
                )
                .because("SDK dependencies must be isolated in adapter modules, not kernel");

        try {
            rule.check(kernelClasses);
            System.out.println("R1 kernel->SDK: PASS");
        } catch (AssertionError e) {
            System.out.println("R1 kernel->SDK violations (should be 0):\n" + e.getMessage());
            throw e;
        }
    }
}
