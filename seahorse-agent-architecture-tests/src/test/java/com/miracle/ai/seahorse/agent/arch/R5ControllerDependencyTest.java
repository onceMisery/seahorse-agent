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

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** R5: freeze the exact controller-to-kernel-service dependencies present in Phase 0. */
public class R5ControllerDependencyTest {

    private static final Set<String> PHASE_ZERO_BASELINE = Set.of(
            "com.miracle.ai.seahorse.agent.adapters.web.SeahorseAdminTenantController"
                    + " -> com.miracle.ai.seahorse.agent.kernel.application.admin.KernelAdminTenantService",
            "com.miracle.ai.seahorse.agent.adapters.web.SeahorseAdminUserController"
                    + " -> com.miracle.ai.seahorse.agent.kernel.application.admin.KernelAdminTenantService",
            "com.miracle.ai.seahorse.agent.adapters.web.SeahorseAuditLogController"
                    + " -> com.miracle.ai.seahorse.agent.kernel.application.admin.KernelAuditLogService",
            "com.miracle.ai.seahorse.agent.adapters.web.SeahorseEvalCandidateDecisionController"
                    + " -> com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalCandidateDecisionService",
            "com.miracle.ai.seahorse.agent.adapters.web.SeahorseEvalCandidateDecisionController"
                    + " -> com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalRegressionService",
            "com.miracle.ai.seahorse.agent.adapters.web.SeahorseMarketplaceController"
                    + " -> com.miracle.ai.seahorse.agent.kernel.application.agent.marketplace.KernelAgentMarketplaceService");

    private final JavaClasses webClasses = new ClassFileImporter()
            .importPackages("com.miracle.ai.seahorse.agent.adapters.web", "com.miracle.ai.seahorse.agent.adapters.local");

    @Test
    void controllerToKernelServiceDependenciesMustMatchPhaseZeroBaseline() {
        Set<String> actualDependencies = new TreeSet<>();

        for (JavaClass controller : webClasses) {
            if (!controller.getSimpleName().endsWith("Controller")) {
                continue;
            }

            for (Dependency dependency : controller.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                if (isKernelService(target)) {
                    actualDependencies.add(controller.getName() + " -> " + target.getName());
                }
            }
        }

        assertEquals(new TreeSet<>(PHASE_ZERO_BASELINE), actualDependencies,
                "Controller-to-kernel-service dependencies changed; replace them with inbound ports or update the reviewed baseline");
    }

    private static boolean isKernelService(JavaClass target) {
        String targetName = target.getSimpleName();
        return target.getPackageName().startsWith("com.miracle.ai.seahorse.agent.kernel.application")
                && targetName.startsWith("Kernel")
                && targetName.endsWith("Service")
                && !targetName.equals("KernelService");
    }
}
