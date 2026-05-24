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

package com.miracle.ai.seahorse.agent.ports.outbound.credential;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class CredentialMaterialTests {

    private static final String SECRET_REF = "secret:mcp/weather";
    private static final String RAW_TOKEN = "sk-live-secret";

    @Test
    void shouldRedactSecretValuesFromStringRepresentations() {
        SecretValue secretValue = SecretValue.of(RAW_TOKEN);
        CredentialMaterial material = CredentialMaterial.staticBearer(SECRET_REF, secretValue);

        Assertions.assertFalse(secretValue.toString().contains(RAW_TOKEN));
        Assertions.assertFalse(material.toString().contains(RAW_TOKEN));
        Assertions.assertTrue(material.toString().contains(SECRET_REF));
    }

    @Test
    void shouldResolveStaticBearerFromSecretStore() {
        CredentialProviderPort provider = new SecretStoreCredentialProvider(
                secretRef -> SECRET_REF.equals(secretRef)
                        ? Optional.of(SecretValue.of(RAW_TOKEN))
                        : Optional.empty());

        CredentialMaterial material = provider.resolve(CredentialRequest.staticBearer(SECRET_REF));

        Assertions.assertEquals(CredentialAuthType.STATIC_BEARER, material.authType());
        Assertions.assertEquals(SECRET_REF, material.secretRef());
        Assertions.assertEquals(RAW_TOKEN, material.secretValue().reveal());
    }
}
