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

package com.miracle.ai.seahorse.agent.kernel.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * 基于 classpath 资源的微内核扩展加载器。
 *
 * <p>该加载器读取 {@code META-INF/seahorse-agent/{port-fqcn}} 描述文件，并把扩展实例注册到
 * {@link ExtensionRegistry}。加载动作只应发生在启动期，运行期请求链路继续使用已构建好的注册表，避免反射扫描影响 RAG 性能。
 */
public class ExtensionLoader {

    private static final String RESOURCE_ROOT = "META-INF/seahorse-agent/";
    private static final String KEY_DEFAULT = "default";
    private static final String KEY_CLASS_SUFFIX = ".class";
    private static final String KEY_ORDER_SUFFIX = ".order";
    private static final String KEY_DEFAULT_SUFFIX = ".default";
    private static final String KEY_MANAGED_SUFFIX = ".managed";
    private static final String KEY_CAPABILITIES_SUFFIX = ".capabilities";
    private static final String KEY_ENABLED_BY_DEFAULT_SUFFIX = ".enabled-by-default";

    private final ClassLoader classLoader;
    private final List<ExtensionLoadDiagnostic> diagnostics = new ArrayList<>();

    public ExtensionLoader(ClassLoader classLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader must not be null");
    }

    /**
     * 使用当前线程上下文 ClassLoader 创建扩展加载器。
     *
     * @return 扩展加载器
     */
    public static ExtensionLoader usingContextClassLoader() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            return new ExtensionLoader(contextClassLoader);
        }
        return new ExtensionLoader(ExtensionLoader.class.getClassLoader());
    }

    /**
     * 加载并注册某个端口类型下的全部扩展。
     *
     * @param portType    端口类型
     * @param featureType 扩展所属 Feature 类型
     * @param registry    扩展注册表
     * @param <T>         端口泛型
     * @return 成功注册的扩展数量
     */
    public <T> int load(Class<T> portType, FeatureType featureType, ExtensionRegistry registry) {
        Objects.requireNonNull(portType, "portType must not be null");
        Objects.requireNonNull(featureType, "featureType must not be null");
        Objects.requireNonNull(registry, "registry must not be null");
        return loadResources(new LoadContext<>(portType, featureType, registry, resourceName(portType)));
    }

    /**
     * 获取最近一次加载过程中保留的诊断信息。
     *
     * @return 诊断信息快照
     */
    public List<ExtensionLoadDiagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    private <T> int loadResources(LoadContext<T> context) {
        try {
            Enumeration<URL> resources = classLoader.getResources(context.resourceName());
            int loaded = 0;
            while (resources.hasMoreElements()) {
                loaded += loadResource(context, resources.nextElement());
            }
            return loaded;
        } catch (IOException ex) {
            throw new IllegalStateException("load extension resources failed: " + context.resourceName(), ex);
        }
    }

    private <T> int loadResource(LoadContext<T> context, URL resource) {
        Properties properties = readProperties(resource);
        String defaultName = properties.getProperty(KEY_DEFAULT, "");
        return extensionNames(properties).stream()
                .mapToInt(name -> registerExtension(context, properties, defaultName, name))
                .sum();
    }

    private Set<String> extensionNames(Properties properties) {
        Set<String> names = new LinkedHashSet<>();
        for (String key : properties.stringPropertyNames()) {
            collectExtensionName(names, key);
        }
        return names;
    }

    private void collectExtensionName(Set<String> names, String key) {
        if (KEY_DEFAULT.equals(key)) {
            return;
        }
        String name = stripKnownSuffix(key);
        if (!name.isBlank()) {
            names.add(name);
        }
    }

    private String stripKnownSuffix(String key) {
        if (key.endsWith(KEY_CLASS_SUFFIX)) {
            return key.substring(0, key.length() - KEY_CLASS_SUFFIX.length());
        }
        if (key.endsWith(KEY_ORDER_SUFFIX)) {
            return key.substring(0, key.length() - KEY_ORDER_SUFFIX.length());
        }
        if (key.endsWith(KEY_DEFAULT_SUFFIX)) {
            return key.substring(0, key.length() - KEY_DEFAULT_SUFFIX.length());
        }
        if (key.endsWith(KEY_MANAGED_SUFFIX)) {
            return key.substring(0, key.length() - KEY_MANAGED_SUFFIX.length());
        }
        if (key.endsWith(KEY_CAPABILITIES_SUFFIX)) {
            return key.substring(0, key.length() - KEY_CAPABILITIES_SUFFIX.length());
        }
        if (key.endsWith(KEY_ENABLED_BY_DEFAULT_SUFFIX)) {
            return key.substring(0, key.length() - KEY_ENABLED_BY_DEFAULT_SUFFIX.length());
        }
        return key;
    }

    private <T> int registerExtension(LoadContext<T> context, Properties properties, String defaultName, String name) {
        String className = className(properties, name);
        if (className.isBlank() || managedByContainer(properties, name)) {
            return 0;
        }
        try {
            T instance = instantiate(context.portType(), className);
            ExtensionDescriptor descriptor = descriptor(context, properties, defaultName, name);
            context.registry().register(descriptor, instance);
            return 1;
        } catch (RuntimeException ex) {
            diagnostics.add(new ExtensionLoadDiagnostic(
                    context.resourceName(), name, className, ex.getMessage()));
            throw ex;
        }
    }

    private String className(Properties properties, String name) {
        String directValue = properties.getProperty(name, "");
        if (!directValue.isBlank()) {
            return directValue.trim();
        }
        return properties.getProperty(name + KEY_CLASS_SUFFIX, "").trim();
    }

    private <T> ExtensionDescriptor descriptor(
            LoadContext<T> context, Properties properties, String defaultName, String name) {
        int order = parseOrder(properties.getProperty(name + KEY_ORDER_SUFFIX, "0"), name);
        boolean defaultCandidate = defaultCandidate(properties, defaultName, name);
        Set<String> capabilities = capabilities(properties.getProperty(name + KEY_CAPABILITIES_SUFFIX, ""));
        boolean enabledByDefault = enabledByDefault(properties, name);
        return new ExtensionDescriptor(
                name, context.portType(), context.featureType(), order, defaultCandidate, capabilities,
                enabledByDefault);
    }

    private boolean defaultCandidate(Properties properties, String defaultName, String name) {
        String candidateValue = properties.getProperty(name + KEY_DEFAULT_SUFFIX, "false");
        return name.equals(defaultName) || Boolean.parseBoolean(candidateValue);
    }

    private boolean managedByContainer(Properties properties, String name) {
        String managedValue = properties.getProperty(name + KEY_MANAGED_SUFFIX, "false");
        return Boolean.parseBoolean(managedValue);
    }

    private boolean enabledByDefault(Properties properties, String name) {
        String enabledValue = properties.getProperty(name + KEY_ENABLED_BY_DEFAULT_SUFFIX, "true");
        return Boolean.parseBoolean(enabledValue);
    }

    private Set<String> capabilities(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> capabilities = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(candidate -> !candidate.isBlank())
                .forEach(capabilities::add);
        return capabilities;
    }

    private int parseOrder(String value, String name) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("invalid extension order for " + name + ": " + value, ex);
        }
    }

    private <T> T instantiate(Class<T> portType, String className) {
        try {
            Class<?> extensionClass = Class.forName(className, true, classLoader);
            Object instance = extensionClass.getDeclaredConstructor().newInstance();
            return portType.cast(instance);
        } catch (ClassCastException ex) {
            throw new IllegalArgumentException("extension does not implement port: " + className, ex);
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                | NoSuchMethodException | InvocationTargetException ex) {
            throw new IllegalStateException("instantiate extension failed: " + className, ex);
        }
    }

    private Properties readProperties(URL resource) {
        try (InputStream inputStream = resource.openStream()) {
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties;
        } catch (IOException ex) {
            throw new IllegalStateException("read extension resource failed: " + resource, ex);
        }
    }

    private String resourceName(Class<?> portType) {
        return RESOURCE_ROOT + portType.getName();
    }

    private record LoadContext<T>(
            Class<T> portType,
            FeatureType featureType,
            ExtensionRegistry registry,
            String resourceName
    ) {
    }
}
