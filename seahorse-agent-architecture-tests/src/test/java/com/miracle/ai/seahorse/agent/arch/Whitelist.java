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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads cross-domain whitelist from resources/archunit/cross-domain-whitelist.txt
 * Format per line: sourceDomain -> targetDomain | sourceClass | targetClass | reason
 */
public final class Whitelist {

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
                String[] parts = line.split("\\|");
                if (parts.length < 3) {
                    throw new IllegalArgumentException("Invalid whitelist entry: " + line);
                }
                String sourceClass = parts[1].trim();
                String targetClass = parts[2].trim();
                exactSourceToTarget.add(sourceClass + " -> " + targetClass);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load whitelist", e);
        }
    }

    public boolean allowsExact(String sourceClass, String targetClass) {
        return exactSourceToTarget.contains(sourceClass + " -> " + targetClass);
    }

    public int size() {
        return rawLines.size();
    }

    public Set<String> getExactSourceToTarget() {
        return Collections.unmodifiableSet(exactSourceToTarget);
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
