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

package com.miracle.ai.seahorse.agent.ports.outbound.connector;

/**
 * Verifies that connector credentials are valid and functional before binding.
 */
public interface ConnectorCredentialVerificationPort {

    /**
     * Verify the given credential JSON for the specified connector.
     *
     * @param connectorId    the connector identifier
     * @param credentialJson the credential payload as JSON
     * @return verification result indicating success or failure with a message
     */
    VerificationResult verify(String connectorId, String credentialJson);

    /**
     * Outcome of a credential verification attempt.
     *
     * @param success whether the verification passed
     * @param message human-readable description of the result
     */
    record VerificationResult(boolean success, String message) {

        public static VerificationResult ok() {
            return new VerificationResult(true, "OK");
        }

        public static VerificationResult fail(String message) {
            return new VerificationResult(false, message);
        }
    }
}
