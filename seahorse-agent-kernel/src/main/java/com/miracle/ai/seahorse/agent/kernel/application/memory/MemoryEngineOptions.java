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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

/**
 * 默认记忆引擎的可配置策略。
 *
 * <p>该对象只表达内核可理解的策略值，Spring 属性解析留在 starter 自动配置中，保持 kernel
 * 对外部框架无感知。</p>
 */
public record MemoryEngineOptions(int shortTermLimit,
                                  int longTermLimit,
                                  int semanticLimit,
                                  boolean captureEnabled) {

    public static final int DEFAULT_SHORT_TERM_LIMIT = 5;
    public static final int DEFAULT_LONG_TERM_LIMIT = 3;
    public static final int DEFAULT_SEMANTIC_LIMIT = 10;

    public MemoryEngineOptions {
        shortTermLimit = positive(shortTermLimit, DEFAULT_SHORT_TERM_LIMIT);
        longTermLimit = positive(longTermLimit, DEFAULT_LONG_TERM_LIMIT);
        semanticLimit = positive(semanticLimit, DEFAULT_SEMANTIC_LIMIT);
    }

    public static MemoryEngineOptions defaults() {
        return new MemoryEngineOptions(
                DEFAULT_SHORT_TERM_LIMIT,
                DEFAULT_LONG_TERM_LIMIT,
                DEFAULT_SEMANTIC_LIMIT,
                true);
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
