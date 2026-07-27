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

import java.net.URI;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** R4: adapter Maven modules must not directly depend on other adapter modules. */
public class R4AdapterIsolationTest {

    private static final String AGENTSCOPE_MODULE = "seahorse-agent-adapter-agent-agentscope";
    private static final String AGENTSCOPE_CORE_MODULE = "seahorse-agent-adapter-agent-agentscope-core";
    private static final Pattern ADAPTER_MODULE_PATTERN =
            Pattern.compile("(?:^|/)(seahorse-agent-adapter-[^/!]+)(?:/|!)");

    private final JavaClasses adapterClasses = new ClassFileImporter()
            .importPackages("com.miracle.ai.seahorse.agent.adapters");

    static String adapterModuleFromUri(URI sourceUri) {
        String normalized = sourceUri.toString().replace('\\', '/');
        Matcher matcher = ADAPTER_MODULE_PATTERN.matcher(normalized);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String adapterModule(JavaClass javaClass) {
        return javaClass.getSource()
                .map(source -> adapterModuleFromUri(source.getUri()))
                .orElse(null);
    }

    private static boolean isApprovedModuleDependency(String sourceModule, String targetModule) {
        return AGENTSCOPE_MODULE.equals(sourceModule) && AGENTSCOPE_CORE_MODULE.equals(targetModule);
    }

    @Test
    void adaptersShouldNotDependOnOtherAdapters() {
        Set<String> violations = new TreeSet<>();

        for (JavaClass source : adapterClasses) {
            String sourceModule = adapterModule(source);
            if (sourceModule == null) {
                continue;
            }

            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetModule = adapterModule(target);
                if (targetModule == null
                        || sourceModule.equals(targetModule)
                        || isApprovedModuleDependency(sourceModule, targetModule)) {
                    continue;
                }
                violations.add(source.getName() + " [" + sourceModule + "] -> "
                        + target.getName() + " [" + targetModule + "]");
            }
        }

        assertTrue(violations.isEmpty(),
                "Adapter modules must not depend on other adapter modules:\n"
                        + String.join("\n", violations));
    }
}
