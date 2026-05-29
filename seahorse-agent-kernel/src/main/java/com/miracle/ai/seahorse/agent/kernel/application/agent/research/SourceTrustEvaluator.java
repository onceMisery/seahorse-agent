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

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.SourceTrustLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.WebSource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Scores external web sources with bounded, deterministic signals.
 */
public class SourceTrustEvaluator {

    private static final List<String> HIGH_REPUTATION_DOMAINS = List.of(
            "wikipedia.org",
            "nature.com",
            "science.org",
            "nih.gov",
            "ncbi.nlm.nih.gov");
    private static final List<String> MEDIUM_REPUTATION_DOMAINS = List.of(
            "github.com",
            "arxiv.org",
            "spring.io",
            "openai.com",
            "microsoft.com",
            "stackoverflow.com");

    private final Clock clock;

    public SourceTrustEvaluator() {
        this(Clock.systemUTC());
    }

    SourceTrustEvaluator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public SourceTrustLevel evaluate(WebSource source) {
        Objects.requireNonNull(source, "source must not be null");
        double domainScore = domainReputationScore(source.url());
        double score = 0.0d;
        score += isHttps(source.url()) ? 0.2d : 0.0d;
        score += domainScore * 0.4d;
        score += freshnessScore(source.retrievedAt()) * 0.2d;
        score += contentLengthScore(source) * 0.2d;

        if (domainScore < 0.8d && score >= 0.7d) {
            score = 0.69d;
        }

        if (score >= 0.7d) return SourceTrustLevel.HIGH;
        if (score >= 0.4d) return SourceTrustLevel.MEDIUM;
        if (score >= 0.2d) return SourceTrustLevel.LOW;
        return SourceTrustLevel.UNTRUSTED;
    }

    public String contentHash(WebSource source) {
        Objects.requireNonNull(source, "source must not be null");
        String material = String.join("\n",
                normalizeUrl(source.url()),
                normalizeText(source.title()),
                normalizeText(source.snippet()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash web source", ex);
        }
    }

    private double domainReputationScore(String url) {
        String host = host(url);
        if (host.isBlank()) return 0.0d;
        if (isPublicSectorOrAcademic(host) || matchesAny(host, HIGH_REPUTATION_DOMAINS)) {
            return 1.0d;
        }
        if (matchesAny(host, MEDIUM_REPUTATION_DOMAINS)) {
            return 0.45d;
        }
        return isHttps(url) ? 0.15d : 0.0d;
    }

    private boolean isHttps(String url) {
        try {
            return "https".equalsIgnoreCase(URI.create(Objects.requireNonNullElse(url, "")).getScheme());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private double freshnessScore(Instant retrievedAt) {
        if (retrievedAt == null) return 0.5d;
        long ageSeconds = Math.max(0, Duration.between(retrievedAt, Instant.now(clock)).toSeconds());
        long day = Duration.ofDays(1).toSeconds();
        long week = Duration.ofDays(7).toSeconds();
        if (ageSeconds <= day) return 1.0d;
        if (ageSeconds >= week) return 0.0d;
        return 1.0d - ((double) (ageSeconds - day) / (week - day));
    }

    private double contentLengthScore(WebSource source) {
        int length = normalizeText(source.title()).length() + normalizeText(source.snippet()).length();
        if (length >= 80 && length <= 5000) return 1.0d;
        if (length >= 40) return 0.7d;
        if (length > 0) return 0.25d;
        return 0.0d;
    }

    private boolean isPublicSectorOrAcademic(String host) {
        return host.endsWith(".gov")
                || host.endsWith(".gov.cn")
                || host.endsWith(".edu")
                || host.endsWith(".edu.cn");
    }

    private boolean matchesAny(String host, List<String> domains) {
        return domains.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
    }

    private String host(String url) {
        try {
            String host = URI.create(Objects.requireNonNullElse(url, "")).getHost();
            if (host == null) return "";
            return host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private String normalizeUrl(String url) {
        String value = Objects.requireNonNullElse(url, "").trim();
        try {
            URI uri = URI.create(value);
            return new URI(
                    uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT),
                    uri.getAuthority() == null ? null : uri.getAuthority().toLowerCase(Locale.ROOT),
                    uri.getPath(),
                    uri.getQuery(),
                    null).toString();
        } catch (Exception ex) {
            return value.toLowerCase(Locale.ROOT);
        }
    }

    private String normalizeText(String value) {
        return Objects.requireNonNullElse(value, "")
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
