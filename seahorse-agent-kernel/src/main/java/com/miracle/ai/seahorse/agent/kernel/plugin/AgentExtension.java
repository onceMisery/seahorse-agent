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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * seahorse-agent 扩展实现标记。
 * <p>
 * Adapter 或 Feature 可以使用该注解声明名称、顺序和能力标签。
 * 显式注册和扩展加载器都可以读取该元数据。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentExtension {

    /**
     * 扩展名称。
     *
     * @return 扩展名称
     */
    String name();

    /**
     * 排序值，数字越小越靠前。
     *
     * @return 排序值
     */
    int order() default 0;

    /**
     * 扩展能力标签。
     *
     * @return 能力标签数组
     */
    String[] capabilities() default {};
}
