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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import com.miracle.ai.seahorse.agent.kernel.domain.memory.UserMemoryPrivacySetting;

public interface UserMemoryPrivacySettingPort {

    UserMemoryPrivacySetting current(String userId);

    UserMemoryPrivacySetting setPrivacyMode(String userId, boolean enabled);

    static UserMemoryPrivacySettingPort defaults() {
        return new UserMemoryPrivacySettingPort() {
            @Override
            public UserMemoryPrivacySetting current(String userId) {
                return UserMemoryPrivacySetting.enabled(userId, false);
            }

            @Override
            public UserMemoryPrivacySetting setPrivacyMode(String userId, boolean enabled) {
                return UserMemoryPrivacySetting.enabled(userId, enabled);
            }
        };
    }
}
