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

package com.miracle.ai.seahorse.agent.adapters.web.alert;

import com.miracle.ai.seahorse.agent.kernel.domain.alert.AlertLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.alert.AlertSignal;
import com.miracle.ai.seahorse.agent.ports.outbound.alert.AlertNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * DingTalk webhook implementation of {@link AlertNotifierPort}.
 *
 * <p>Supports optional HMAC-SHA256 signing for webhook security. All failures
 * are caught and logged — this adapter never throws.
 *
 * <p>Uses Java's built-in {@link HttpClient} (no third-party HTTP dependency required).
 */
public class DingTalkAlertNotifierAdapter implements AlertNotifierPort {

    private static final Logger LOG = LoggerFactory.getLogger(DingTalkAlertNotifierAdapter.class);

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final String webhookUrl;
    private final String secret;
    private final HttpClient httpClient;

    /**
     * Create a DingTalk notifier without signing.
     *
     * @param webhookUrl full DingTalk robot webhook URL
     */
    public DingTalkAlertNotifierAdapter(String webhookUrl) {
        this(webhookUrl, null);
    }

    /**
     * Create a DingTalk notifier with optional HMAC-SHA256 signing.
     *
     * @param webhookUrl full DingTalk robot webhook URL
     * @param secret     the signing secret configured in the DingTalk robot settings; may be {@code null}
     */
    public DingTalkAlertNotifierAdapter(String webhookUrl, String secret) {
        this.webhookUrl = Objects.requireNonNull(webhookUrl, "webhookUrl must not be null");
        this.secret = (secret == null || secret.isBlank()) ? null : secret.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    @Override
    public void notify(AlertSignal alert) {
        try {
            String url = buildSignedUrl();
            String body = buildMarkdownBody(alert);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(HTTP_TIMEOUT)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warn("DingTalk webhook returned HTTP {}: {}", response.statusCode(), response.body());
                return;
            }

            // DingTalk returns errcode=0 on success
            String responseBody = response.body();
            if (responseBody != null && responseBody.contains("\"errcode\":0")) {
                LOG.debug("DingTalk alert sent successfully: title={}", alert.title());
            } else {
                LOG.warn("DingTalk webhook response indicates failure: {}", responseBody);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("DingTalk alert interrupted for title={}: {}", alert.title(), e.getMessage());
        } catch (Exception e) {
            LOG.warn("Failed to send DingTalk alert for title={}: {}", alert.title(), e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(AlertLevel level) {
        // DingTalk supports all alert levels
        return true;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private String buildSignedUrl() {
        if (secret == null) {
            return webhookUrl;
        }
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
            return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
        } catch (Exception e) {
            LOG.warn("Failed to compute DingTalk HMAC signature, sending unsigned: {}", e.getMessage());
            return webhookUrl;
        }
    }

    private String buildMarkdownBody(AlertSignal alert) {
        StringBuilder sb = new StringBuilder(512);
        String levelEmoji = switch (alert.level()) {
            case CRITICAL -> "🔴";
            case WARNING  -> "🟡";
            case INFO     -> "🔵";
        };

        String title = escapeJson(alert.title());
        String levelLabel = alert.level().name();
        String time = FORMATTER.format(alert.timestamp() != null ? alert.timestamp() : Instant.now());
        String message = escapeJson(alert.message());

        sb.append("{");
        sb.append("\"msgtype\":\"markdown\",");
        sb.append("\"markdown\":{");
        sb.append("\"title\":\"").append(levelEmoji).append(" [").append(levelLabel).append("] ").append(title).append("\",");
        sb.append("\"text\":\"")
          .append("### ").append(levelEmoji).append(" [").append(levelLabel).append("] ").append(title).append("\\n\\n")
          .append("**时间**: ").append(time).append("\\n\\n")
          .append("**详情**: ").append(message);

        // Append extra details if present
        if (alert.details() != null && !alert.details().isEmpty()) {
            sb.append("\\n\\n");
            for (Map.Entry<String, String> entry : alert.details().entrySet()) {
                sb.append("- **").append(escapeJson(entry.getKey())).append("**: ")
                  .append(escapeJson(entry.getValue())).append("\\n");
            }
        }

        sb.append("\"},");
        // @all for CRITICAL alerts
        if (alert.level() == AlertLevel.CRITICAL) {
            sb.append("\"at\":{\"isAtAll\":true}");
        } else {
            sb.append("\"at\":{\"isAtAll\":false}");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
