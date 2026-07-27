/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

package com.miracle.ai.seahorse.agent.ports.outbound.model;

import java.util.Objects;

/** Fingerprint of the final provider request body produced by a model adapter. */
public record ModelRequestFingerprint(String payloadHash, String source) {

    public ModelRequestFingerprint {
        payloadHash = Objects.requireNonNullElse(payloadHash, "").trim();
        source = Objects.requireNonNullElse(source, "UNAVAILABLE").trim();
    }

    public boolean available() {
        return !payloadHash.isBlank();
    }

    public static ModelRequestFingerprint unavailable() {
        return new ModelRequestFingerprint("", "UNAVAILABLE");
    }
}
