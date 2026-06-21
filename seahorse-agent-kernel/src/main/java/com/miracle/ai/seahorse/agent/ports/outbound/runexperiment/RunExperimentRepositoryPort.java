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

package com.miracle.ai.seahorse.agent.ports.outbound.runexperiment;

import java.util.List;
import java.util.Optional;

public interface RunExperimentRepositoryPort {

    RunExperimentDetails create(RunExperimentRecord experiment, List<RunExperimentTrialRecord> trials);

    Optional<RunExperimentDetails> findById(String userId, Long id);

    Optional<RunExperimentDetails> updateExperimentStatus(String userId, Long id, String status);

    Optional<RunExperimentDetails> updateExperimentOnlyStatus(String userId, Long id, String status);

    Optional<RunExperimentDetails> updateTrialScore(String userId, Long experimentId, Long trialId, String scoreJson);

    Optional<RunExperimentDetails> updateTrialExecution(
            String userId,
            Long experimentId,
            Long trialId,
            String status,
            String runId,
            Long outputMessageId,
            String metricJson,
            String errorMessage);
}
