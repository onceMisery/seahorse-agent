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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.ports.outbound.auth.IpGeolocationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IP geolocation adapter using ip-api.com service.
 * Implements graceful degradation and simple LRU cache.
 */
public class IpApiGeolocationAdapter implements IpGeolocationPort {

    private static final Logger log = LoggerFactory.getLogger(IpApiGeolocationAdapter.class);
    private static final int MAX_CACHE_SIZE = 1000;
    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final String API_URL_TEMPLATE = "http://ip-api.com/json/%s?fields=status,country,regionName,city,isp";

    private final HttpClient httpClient;
    private final Map<String, GeoInfo> cache;

    public IpApiGeolocationAdapter() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, GeoInfo> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };
    }

    @Override
    public GeoInfo resolve(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return new GeoInfo(null, null, null, null);
        }

        // Check cache
        synchronized (cache) {
            GeoInfo cached = cache.get(ipAddress);
            if (cached != null) {
                return cached;
            }
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(API_URL_TEMPLATE, ipAddress)))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                GeoInfo geoInfo = parseResponse(response.body());
                synchronized (cache) {
                    cache.put(ipAddress, geoInfo);
                }
                return geoInfo;
            }
        } catch (Exception e) {
            log.debug("Failed to resolve geolocation for IP {}: {}", ipAddress, e.getMessage());
        }

        return new GeoInfo(null, null, null, null);
    }

    private GeoInfo parseResponse(String json) {
        // Simple JSON parsing without external dependencies
        // Response format: {"status":"success","country":"China","regionName":"Beijing","city":"Beijing","isp":"China Telecom"}
        String status = extractJsonValue(json, "status");
        if (!"success".equals(status)) {
            return new GeoInfo(null, null, null, null);
        }

        String country = extractJsonValue(json, "country");
        String region = extractJsonValue(json, "regionName");
        String city = extractJsonValue(json, "city");
        String isp = extractJsonValue(json, "isp");

        return new GeoInfo(country, region, city, isp);
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            return null;
        }
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) {
            return null;
        }
        return json.substring(start, end);
    }
}
