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

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R3: application subdomains may only use exact cross-domain class dependencies
 * recorded in the Phase 0 baseline.
 */
public class R3SubdomainIsolationTest {

    private final JavaClasses applicationClasses = new ClassFileImporter()
            .importPackages("com.miracle.ai.seahorse.agent.kernel.application");

    private final Whitelist whitelist = new Whitelist();

    @Test
    void whitelistMustNotContainDuplicateClassPairs() {
        assertEquals(whitelist.size(), whitelist.getExactSourceToTarget().size(),
                "Each whitelist row must identify one unique source-to-target class pair");
    }

    @Test
    void subdomainShouldNotDependOnOtherSubdomainUnlessWhitelisted() {
        List<Dependency> crossDomainDependencies = new ArrayList<>();

        for (JavaClass source : applicationClasses) {
            String sourceDomain = Whitelist.extractDomain(source.getPackageName());
            if (sourceDomain == null) {
                continue;
            }

            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetDomain = Whitelist.extractDomain(target.getPackageName());
                if (targetDomain == null || sourceDomain.equals(targetDomain)) {
                    continue;
                }
                crossDomainDependencies.add(dependency);
            }
        }

        Set<String> distinctClassPairs = crossDomainDependencies.stream()
                .map(dependency -> dependency.getOriginClass().getName()
                        + " -> " + dependency.getTargetClass().getName())
                .collect(Collectors.toSet());
        Set<String> unapprovedPairs = new TreeSet<>(distinctClassPairs);
        unapprovedPairs.removeAll(whitelist.getExactSourceToTarget());
        Set<String> staleBaselinePairs = new TreeSet<>(whitelist.getExactSourceToTarget());
        staleBaselinePairs.removeAll(distinctClassPairs);

        assertTrue(unapprovedPairs.isEmpty() && staleBaselinePairs.isEmpty(),
                "New cross-domain class dependencies are not in the Phase 0 baseline:\n"
                        + String.join("\n", unapprovedPairs)
                        + "\nBaseline entries no longer present in the code:\n"
                        + String.join("\n", staleBaselinePairs));
    }
}
