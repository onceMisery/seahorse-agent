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

package com.miracle.ai.seahorse.agent.ports.outbound.auth;

/**
 * Outbound port for IP geolocation resolution.
 */
public interface IpGeolocationPort {

    /**
     * Resolve geographic information for an IP address.
     *
     * @param ipAddress the IP address to resolve
     * @return geo information, or null fields on failure
     */
    GeoInfo resolve(String ipAddress);

    /**
     * Geographic information for an IP address.
     *
     * @param country the country name (may be null)
     * @param region  the region/state name (may be null)
     * @param city    the city name (may be null)
     * @param isp     the ISP name (may be null)
     */
    record GeoInfo(String country, String region, String city, String isp) {

        /**
         * Format geo info as a display string.
         *
         * @return formatted string like "Beijing, Beijing, China" or "Unknown"
         */
        public String toDisplayString() {
            StringBuilder sb = new StringBuilder();
            if (city != null) {
                sb.append(city);
            }
            if (region != null && !region.equals(city)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(region);
            }
            if (country != null) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(country);
            }
            return sb.length() > 0 ? sb.toString() : "Unknown";
        }
    }
}
