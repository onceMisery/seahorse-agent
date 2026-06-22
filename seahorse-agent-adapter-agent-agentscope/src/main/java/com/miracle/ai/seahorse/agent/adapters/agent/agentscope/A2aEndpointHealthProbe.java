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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@FunctionalInterface
public interface A2aEndpointHealthProbe {

    A2aEndpointHealthStatus check(String healthUrl);

    static A2aEndpointHealthProbe unknown() {
        return ignored -> A2aEndpointHealthStatus.UNKNOWN;
    }

    static A2aEndpointHealthProbe http(Duration timeout) {
        Duration safeTimeout = timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofMillis(800)
                : timeout;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(safeTimeout)
                .build();
        return healthUrl -> {
            if (healthUrl == null || healthUrl.trim().isEmpty()) {
                return A2aEndpointHealthStatus.UNKNOWN;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl.trim()))
                        .timeout(safeTimeout)
                        .GET()
                        .build();
                int statusCode = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                return statusCode >= 200 && statusCode < 300
                        ? A2aEndpointHealthStatus.UP
                        : A2aEndpointHealthStatus.DOWN;
            } catch (RuntimeException ex) {
                return A2aEndpointHealthStatus.UNKNOWN;
            } catch (Exception ex) {
                return A2aEndpointHealthStatus.DOWN;
            }
        };
    }
}
