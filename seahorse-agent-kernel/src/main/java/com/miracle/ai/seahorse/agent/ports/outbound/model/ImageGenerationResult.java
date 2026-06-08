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

package com.miracle.ai.seahorse.agent.ports.outbound.model;

import java.util.Objects;

public record ImageGenerationResult(String status,
                                    String prompt,
                                    String model,
                                    String imageUrl,
                                    String b64Json,
                                    String mimeType) {

    public ImageGenerationResult {
        status = Objects.requireNonNullElse(status, "GENERATED").trim();
        prompt = Objects.requireNonNullElse(prompt, "");
        model = Objects.requireNonNullElse(model, "").trim();
        imageUrl = Objects.requireNonNullElse(imageUrl, "").trim();
        b64Json = Objects.requireNonNullElse(b64Json, "");
        mimeType = Objects.requireNonNullElse(mimeType, "image/png").trim();
    }

    public static ImageGenerationResult generated(String prompt,
                                                  String model,
                                                  String imageUrl,
                                                  String b64Json,
                                                  String mimeType) {
        return new ImageGenerationResult("GENERATED", prompt, model, imageUrl, b64Json, mimeType);
    }
}
