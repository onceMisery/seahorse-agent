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

package com.miracle.ai.seahorse.agent.ports.outbound.billing;

import java.util.function.Supplier;

/**
 * Outbound port for programmatic transaction management.
 *
 * <p>Allows kernel services to execute logic within a database transaction
 * without depending on Spring's {@code @Transactional} annotation directly.
 */
public interface TransactionRunnerPort {

    /**
     * Executes the given action within a new or existing transaction.
     *
     * @param action the action to execute
     * @param <T>    the return type
     * @return the result of the action
     */
    <T> T runInTransaction(Supplier<T> action);
}
