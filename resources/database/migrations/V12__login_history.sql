-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

CREATE TABLE IF NOT EXISTS t_login_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    login_type VARCHAR(32) NOT NULL DEFAULT 'PASSWORD',
    ip_address VARCHAR(45),
    user_agent VARCHAR(512),
    device_info VARCHAR(256),
    status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    failure_reason VARCHAR(256),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_login_history_user ON t_login_history (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_history_tenant ON t_login_history (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_login_history_ip ON t_login_history (ip_address, created_at DESC);
