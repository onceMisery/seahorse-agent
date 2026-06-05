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

package com.miracle.ai.seahorse.agent.ports.inbound.auth;

/**
 * Command object for login requests.
 *
 * @param username   the username (required)
 * @param password   the password (required)
 * @param ipAddress  the client IP address (optional, may be null)
 * @param userAgent  the User-Agent header (optional, may be null)
 * @param deviceInfo parsed device information (optional, may be null)
 */
public record LoginCommand(String username, String password, String ipAddress, String userAgent, String deviceInfo) {

    /**
     * Backward-compatible constructor for username/password only.
     *
     * @param username the username
     * @param password the password
     */
    public LoginCommand(String username, String password) {
        this(username, password, null, null, null);
    }
}
