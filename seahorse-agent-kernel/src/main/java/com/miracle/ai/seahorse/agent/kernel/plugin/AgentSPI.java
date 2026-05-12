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
 * seahorse-agent 扩展点标记。
 * <p>
 * 该注解标记一个端口是否参与微内核扩展加载。它只表达内核契约元数据，
 * 不触发类扫描或实例化，避免请求期引入反射开销。
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentSPI {

    /**
     * 默认扩展名称。
     *
     * @return 默认扩展名称
     */
    String defaultName() default "noop";

    /**
     * 是否为启动必需端口。
     *
     * @return true 表示缺少实现时启动失败
     */
    boolean required() default false;
}
