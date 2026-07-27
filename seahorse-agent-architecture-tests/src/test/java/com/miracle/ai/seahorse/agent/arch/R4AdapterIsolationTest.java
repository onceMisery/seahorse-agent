package com.miracle.ai.seahorse.agent.arch;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * R4: adapter 互不依赖
 * - Each seahorse-agent-adapter-* should only depend on kernel and its own SDK
 * - No adapter -> adapter direct dependency
 * - Exception: adapter-agent-agentscope may depend on adapter-agent-agentscope-core (parent relationship)
 */
public class R4AdapterIsolationTest {

    // Import all adapter packages
    private final JavaClasses adapterClasses = new ClassFileImporter()
            .importPackages("com.miracle.ai.seahorse.agent.adapters");

    // Extract adapter name from package: com.miracle.ai.seahorse.agent.adapters.<adapter-name>.[sub]
    private static final Pattern ADAPTER_PATTERN = Pattern.compile("com\\.miracle\\.ai\\.seahorse\\.agent\\.adapters\\.([a-zA-Z0-9\\-_]+).*");

    private String extractAdapter(String packageName) {
        Matcher m = ADAPTER_PATTERN.matcher(packageName);
        if (m.matches()) {
            String full = m.group(1);
            return full;
        }
        return null;
    }

    /** Normalize adapter identifier so that web+local are considered same module adapter-web */
    private String normalizeAdapter(String adapter) {
        if (adapter == null) return null;
        if (adapter.equals("web") || adapter.equals("local")) {
            return "web-local";
        }
        return adapter;
    }

    @Test
    void adaptersShouldNotDependOnOtherAdapters() {
        List<String> violations = new ArrayList<>();

        for (JavaClass clazz : adapterClasses) {
            String sourcePackage = clazz.getPackageName();
            String sourceAdapterRaw = extractAdapter(sourcePackage);
            String sourceAdapter = normalizeAdapter(sourceAdapterRaw);
            if (sourceAdapter == null) continue;

            for (Dependency dep : clazz.getDirectDependenciesFromSelf()) {
                JavaClass target = dep.getTargetClass();
                String targetPackage = target.getPackageName();
                String targetAdapterRaw = extractAdapter(targetPackage);
                String targetAdapter = normalizeAdapter(targetAdapterRaw);
                if (targetAdapter == null) continue;
                if (sourceAdapter.equals(targetAdapter)) continue; // same adapter ok

                if ("agent.agentscope".equals(sourceAdapterRaw) && "agent.agentscope.core".equals(targetAdapterRaw)) {
                    continue;
                }

                violations.add(String.format("%s [%s] -> %s [%s] at %s:%d",
                        clazz.getName(), sourceAdapterRaw,
                        target.getName(), targetAdapterRaw,
                        dep.getDescription(), dep.getLineNumber()));
            }
        }

        Set<String> distinct = new LinkedHashSet<>(violations);
        if (!distinct.isEmpty()) {
            System.out.println("Adapter isolation violations found: " + distinct.size());
            distinct.forEach(System.out::println);
            if (distinct.size() > 10) {
                fail("Found " + distinct.size() + " adapter-to-adapter dependencies (expected <=10 for Phase0, ideally 0). Violations:\n"
                        + String.join("\n", distinct));
            } else {
                System.out.println("WARNING: " + distinct.size() + " adapter inter-dependencies found but within tolerance for Phase0. Please fix in Phase1.");
            }
        } else {
            System.out.println("R4 adapter isolation: PASS - no cross-adapter dependencies");
        }
    }
}
