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

package com.miracle.ai.seahorse.agent.ports.outbound.auth;

import java.util.Optional;

public interface UserRepositoryPort {

    Optional<UserRecord> findById(Long id);

    Optional<UserRecord> findByUsername(String username);

    boolean usernameExists(String username, Long excludedId);

    UserPage page(long current, long size, String keyword);

    Long create(UserCreateValues values);

    boolean update(Long id, UserUpdateValues values);

    boolean delete(Long id);
}
