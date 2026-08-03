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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Governs real public Port interfaces rather than files stored in the ports package. */
public class PortArchitectureTest {

    private static final String PORT_PACKAGE = "com.miracle.ai.seahorse.agent.ports";
    private static final int REVIEWED_PORT_CEILING = 376;
    private static final int PORT_OPERATION_LIMIT = 8;
    private static final Map<String, Integer> LEGACY_OVERSIZED_PORT_BUDGETS = Map.ofEntries(
            Map.entry(PORT_PACKAGE + ".inbound.agent.AgentDefinitionInboundPort", 9),
            Map.entry(PORT_PACKAGE + ".inbound.agent.SandboxRuntimeInboundPort", 12),
            Map.entry(PORT_PACKAGE + ".inbound.agent.skill.AgentSkillManagementInboundPort", 10),
            Map.entry(PORT_PACKAGE + ".inbound.knowledge.KnowledgeDocumentInboundPort", 10),
            Map.entry(PORT_PACKAGE + ".inbound.metadata.MetadataReviewInboundPort", 9),
            Map.entry(PORT_PACKAGE + ".inbound.retrieval.RetrievalEvaluationDatasetInboundPort", 10),
            Map.entry(PORT_PACKAGE + ".inbound.task.TaskInboundPort", 9),
            Map.entry(PORT_PACKAGE + ".outbound.admin.AdminRepositoryPort", 11),
            Map.entry(PORT_PACKAGE + ".outbound.agent.AgentDefinitionRepositoryPort", 9),
            Map.entry(PORT_PACKAGE + ".outbound.agent.AgentSkillRepositoryPort", 9),
            Map.entry(PORT_PACKAGE + ".outbound.agent.ConnectorRepositoryPort", 9));

    private final JavaClasses portClasses = new ClassFileImporter().importPackages(PORT_PACKAGE);

    @Test
    void complexityBaselineMustDescribeActualPublicPortInterfaces() {
        PortMetrics actual = inventory();
        Properties baseline = loadBaseline();

        assertAll(
                () -> assertEquals(metric(baseline, "port_interfaces"), actual.total(),
                        "port_interfaces must count public interfaces, not Java files"),
                () -> assertEquals(metric(baseline, "port_inbound"), actual.inbound()),
                () -> assertEquals(metric(baseline, "port_outbound"), actual.outbound()),
                () -> assertEquals(metric(baseline, "port_common"), actual.common()));
    }

    @Test
    void publicPortCountMustOnlyDecreaseFromTheReviewedBaseline() {
        PortMetrics actual = inventory();
        assertTrue(actual.total() <= REVIEWED_PORT_CEILING,
                () -> "Public Port interface count increased: " + actual.total()
                        + " > " + REVIEWED_PORT_CEILING);
    }

    @Test
    void publicPortsMustNotGrowIntoGodInterfaces() {
        Map<String, Integer> violations = publicPorts().stream()
                .collect(Collectors.toMap(JavaClass::getName, PortArchitectureTest::declaredAbstractOperations))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > PORT_OPERATION_LIMIT)
                .filter(entry -> entry.getValue() > LEGACY_OVERSIZED_PORT_BUDGETS
                        .getOrDefault(entry.getKey(), PORT_OPERATION_LIMIT))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, TreeMap::new));

        assertTrue(violations.isEmpty(),
                () -> "Public Ports exceed the abstract-operation budget; split by capability/failure semantics: "
                        + violations);
    }

    private PortMetrics inventory() {
        List<JavaClass> ports = publicPorts();

        long inbound = countUnder(ports, PORT_PACKAGE + ".inbound");
        long outbound = countUnder(ports, PORT_PACKAGE + ".outbound");
        long common = ports.size() - inbound - outbound;
        return new PortMetrics(ports.size(), Math.toIntExact(inbound),
                Math.toIntExact(outbound), Math.toIntExact(common));
    }

    private List<JavaClass> publicPorts() {
        return portClasses.stream()
                .filter(JavaClass::isInterface)
                .filter(javaClass -> javaClass.getModifiers().contains(JavaModifier.PUBLIC))
                .filter(javaClass -> !javaClass.getName().contains("$"))
                .toList();
    }

    private static int declaredAbstractOperations(JavaClass port) {
        return Math.toIntExact(port.getMethods().stream()
                .filter(method -> method.getOwner().equals(port))
                .filter(method -> method.getModifiers().contains(JavaModifier.ABSTRACT))
                .count());
    }

    private static long countUnder(List<JavaClass> ports, String packagePrefix) {
        return ports.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(packagePrefix)
                        || javaClass.getPackageName().startsWith(packagePrefix + "."))
                .count();
    }

    private static Properties loadBaseline() {
        Path root = locateRepositoryRoot();
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(root.resolve("complexity-baseline.txt"))) {
            properties.load(reader);
            return properties;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot read complexity-baseline.txt", exception);
        }
    }

    private static Path locateRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("complexity-baseline.txt"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root containing complexity-baseline.txt was not found");
    }

    private static int metric(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || !value.matches("\\d+")) {
            throw new IllegalStateException("Missing or invalid complexity metric: " + name);
        }
        return Integer.parseInt(value);
    }

    private record PortMetrics(int total, int inbound, int outbound, int common) {
    }
}
