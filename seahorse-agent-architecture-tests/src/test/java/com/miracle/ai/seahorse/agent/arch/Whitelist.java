package com.miracle.ai.seahorse.agent.arch;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads cross-domain whitelist from resources/archunit/cross-domain-whitelist.txt
 * Format per line: sourceDomain -> targetDomain | sourceClass | targetClass | reason
 */
public final class Whitelist {

    private final Set<String> domainTransitions = new HashSet<>();
    private final Set<String> exactSourceToTarget = new HashSet<>();
    private final List<String> rawLines = new ArrayList<>();

    public Whitelist() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("archunit/cross-domain-whitelist.txt")) {
            if (is == null) {
                throw new IllegalStateException("Whitelist file not found: archunit/cross-domain-whitelist.txt");
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                rawLines.add(line);
                // Parse domain transition
                String[] parts = line.split("\\|");
                if (parts.length >= 1) {
                    String transition = parts[0].trim(); // e.g. "chat -> agent"
                    domainTransitions.add(normalizeTransition(transition));
                    if (parts.length >= 3) {
                        String sourceClass = parts[1].trim();
                        String targetClass = parts[2].trim();
                        exactSourceToTarget.add(sourceClass + " -> " + targetClass);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load whitelist", e);
        }
    }

    private String normalizeTransition(String transition) {
        return transition.replaceAll("\\s+", "").toLowerCase(); // "chat->agent"
    }

    public boolean allowsDomainTransition(String sourceDomain, String targetDomain) {
        String key = (sourceDomain + "->" + targetDomain).toLowerCase();
        return domainTransitions.contains(key);
    }

    public boolean allowsExact(String sourceClass, String targetClass) {
        return exactSourceToTarget.contains(sourceClass + " -> " + targetClass);
    }

    public int size() {
        return rawLines.size();
    }

    public Set<String> getDomainTransitions() {
        return Collections.unmodifiableSet(domainTransitions);
    }

    public List<String> getRawLines() {
        return Collections.unmodifiableList(rawLines);
    }

    public static String extractDomain(String packageName) {
        // package pattern: com.miracle.ai.seahorse.agent.kernel.application.<domain>...
        String prefix = "com.miracle.ai.seahorse.agent.kernel.application.";
        if (!packageName.startsWith(prefix)) return null;
        String remainder = packageName.substring(prefix.length());
        int dot = remainder.indexOf('.');
        if (dot > 0) {
            return remainder.substring(0, dot);
        } else {
            return remainder;
        }
    }
}
