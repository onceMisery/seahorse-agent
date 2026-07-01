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

package com.miracle.ai.seahorse.agent.kernel.application.runexperiment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentReport;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialRecord;
import lombok.NonNull;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class KernelRunExperimentService implements RunExperimentInboundPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    @NonNull
    private final RunExperimentRepositoryPort repositoryPort;
    @NonNull
    private final Supplier<RunExperimentTrialExecutorPort> trialExecutorPortSupplier;
    private final ConversationBranchRepositoryPort branchRepositoryPort;
    private final RunContextSnapshotRepositoryPort runContextSnapshotRepositoryPort;
    private final CostUsageRepositoryPort costUsageRepositoryPort;

    public KernelRunExperimentService(RunExperimentRepositoryPort repositoryPort) {
        this(repositoryPort, RunExperimentTrialExecutorPort.noop());
    }

    public KernelRunExperimentService(
            RunExperimentRepositoryPort repositoryPort,
            RunExperimentTrialExecutorPort trialExecutorPort) {
        this(repositoryPort, () -> trialExecutorPort);
    }

    public KernelRunExperimentService(
            RunExperimentRepositoryPort repositoryPort,
            Supplier<RunExperimentTrialExecutorPort> trialExecutorPortSupplier) {
        this(repositoryPort, trialExecutorPortSupplier, null);
    }

    public KernelRunExperimentService(
            RunExperimentRepositoryPort repositoryPort,
            Supplier<RunExperimentTrialExecutorPort> trialExecutorPortSupplier,
            ConversationBranchRepositoryPort branchRepositoryPort) {
        this(repositoryPort, trialExecutorPortSupplier, branchRepositoryPort, null, null);
    }

    public KernelRunExperimentService(
            RunExperimentRepositoryPort repositoryPort,
            Supplier<RunExperimentTrialExecutorPort> trialExecutorPortSupplier,
            ConversationBranchRepositoryPort branchRepositoryPort,
            RunContextSnapshotRepositoryPort runContextSnapshotRepositoryPort,
            CostUsageRepositoryPort costUsageRepositoryPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.trialExecutorPortSupplier = Objects.requireNonNullElseGet(
                trialExecutorPortSupplier,
                () -> RunExperimentTrialExecutorPort::noop);
        this.branchRepositoryPort = branchRepositoryPort;
        this.runContextSnapshotRepositoryPort = runContextSnapshotRepositoryPort;
        this.costUsageRepositoryPort = costUsageRepositoryPort;
    }

    @Override
    public RunExperimentDetails create(RunExperimentCommand command) {
        RunExperimentCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String userId = requireText(safeCommand.getUserId(), "userId must not be blank");
        String name = requireText(safeCommand.getName(), "name must not be blank");
        if (safeCommand.getConversationId() == null) {
            throw new IllegalArgumentException("conversationId must not be null");
        }
        List<Long> runProfileIds = Objects.requireNonNullElse(safeCommand.getRunProfileIds(), List.<Long>of())
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (runProfileIds.isEmpty()) {
            throw new IllegalArgumentException("runProfileIds must not be empty");
        }
        RunExperimentRecord experiment = RunExperimentRecord.builder()
                .userId(userId)
                .conversationId(safeCommand.getConversationId())
                .baseLeafMessageId(safeCommand.getBaseLeafMessageId())
                .name(name)
                .status(STATUS_PENDING)
                .deleted(0)
                .build();
        List<RunExperimentTrialRecord> trials = runProfileIds.stream()
                .map(runProfileId -> RunExperimentTrialRecord.builder()
                        .runProfileId(runProfileId)
                        .status(STATUS_PENDING)
                        .deleted(0)
                        .build())
                .toList();
        RunExperimentDetails created = repositoryPort.create(experiment, trials);
        RunExperimentTrialExecutorPort trialExecutorPort = resolveTrialExecutor();
        if (!trialExecutorPort.enabled()) {
            return created;
        }
        return executeCreatedExperiment(userId, created, trialExecutorPort);
    }

    @Override
    public Optional<RunExperimentDetails> findById(String userId, Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repositoryPort.findById(requireText(userId, "userId must not be blank"), id);
    }

    @Override
    public RunExperimentDetails cancel(String userId, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("experimentId must not be null");
        }
        return repositoryPort.updateExperimentStatus(
                        requireText(userId, "userId must not be blank"),
                        id,
                        STATUS_CANCELLED)
                .orElseThrow(() -> new IllegalArgumentException("run experiment not found"));
    }

    @Override
    public RunExperimentDetails scoreTrial(String userId, Long experimentId, Long trialId, String scoreJson) {
        if (experimentId == null) {
            throw new IllegalArgumentException("experimentId must not be null");
        }
        if (trialId == null) {
            throw new IllegalArgumentException("trialId must not be null");
        }
        return repositoryPort.updateTrialScore(
                        requireText(userId, "userId must not be blank"),
                        experimentId,
                        trialId,
                        requireText(scoreJson, "scoreJson must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("run experiment trial not found"));
    }

    @Override
    public RunExperimentReport exportReport(String userId, Long id) {
        String safeUserId = requireText(userId, "userId must not be blank");
        RunExperimentDetails details = findById(safeUserId, id)
                .orElseThrow(() -> new IllegalArgumentException("run experiment not found"));
        String markdown = renderReport(safeUserId, details);
        return new RunExperimentReport(
                reportFileName(details.getExperiment()),
                "text/markdown; charset=UTF-8",
                markdown);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String renderReport(String userId, RunExperimentDetails details) {
        RunExperimentRecord experiment = Objects.requireNonNull(details.getExperiment(), "experiment must not be null");
        List<RunExperimentTrialRecord> trials = Objects.requireNonNullElse(details.getTrials(), List.of());
        Map<Long, ConversationMessageRecord> outputMessages = loadOutputMessages(userId, experiment);
        Map<String, RunContextSnapshotRecord> snapshots = loadRunSnapshots(trials);
        StringBuilder report = new StringBuilder();
        report.append("# Run Experiment Report: ").append(markdownText(experiment.getName())).append("\n\n");
        report.append("- Experiment ID: ").append(valueOrDash(experiment.getId())).append("\n");
        report.append("- Conversation ID: ").append(valueOrDash(experiment.getConversationId())).append("\n");
        report.append("- Base leaf message ID: ").append(valueOrDash(experiment.getBaseLeafMessageId())).append("\n");
        report.append("- Status: ").append(valueOrDash(experiment.getStatus())).append("\n");
        report.append("- Generated at: ").append(Instant.now()).append("\n\n");

        report.append("## Trial Export\n\n");
        report.append("| Trial | Run Profile | Status | Run ID | Output Message | Score | Metrics | Studio Trace | Cost Source | Fork Target |\n");
        report.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (RunExperimentTrialRecord trial : trials) {
            RunContextSnapshotRecord snapshot = snapshots.get(trial.getRunId());
            String metrics = valueOrDash(trial.getMetricJson());
            report.append("| ")
                    .append(tableCell(trial.getId()))
                    .append(" | ")
                    .append(tableCell(trial.getRunProfileId()))
                    .append(" | ")
                    .append(tableCell(trial.getStatus()))
                    .append(" | ")
                    .append(tableCell(trial.getRunId()))
                    .append(" | ")
                    .append(tableCell(trial.getOutputMessageId()))
                    .append(" | ")
                    .append(tableCell(trial.getScoreJson()))
                    .append(" | ")
                    .append(tableCell(metrics))
                    .append(" | ")
                    .append(tableCell(traceEvidence(trial, snapshot)))
                    .append(" | ")
                    .append(tableCell(costEvidence(trial, snapshot)))
                    .append(" | ")
                    .append(tableCell(forkTarget(trial)))
                    .append(" |\n");
        }

        report.append("\n## Output Comparison\n\n");
        String baseline = firstOutputContent(trials, outputMessages);
        for (RunExperimentTrialRecord trial : trials) {
            ConversationMessageRecord message = outputMessages.get(trial.getOutputMessageId());
            String content = message == null ? "" : Objects.requireNonNullElse(message.getContent(), "");
            report.append("### Trial ").append(valueOrDash(trial.getId())).append("\n\n");
            report.append("- Run profile: ").append(valueOrDash(trial.getRunProfileId())).append("\n");
            report.append("- Run ID: ").append(valueOrDash(trial.getRunId())).append("\n");
            report.append("- Output message ID: ").append(valueOrDash(trial.getOutputMessageId())).append("\n");
            report.append("- Diff vs first trial: ").append(diffAgainstBaseline(baseline, content)).append("\n");
            if (trial.getErrorMessage() != null && !trial.getErrorMessage().isBlank()) {
                report.append("- Failure: ").append(markdownText(trial.getErrorMessage())).append("\n");
            }
            report.append("\n```text\n");
            report.append(codeBlockText(truncate(content, 1200)));
            report.append("\n```\n\n");
        }

        report.append("## Failures\n\n");
        List<RunExperimentTrialRecord> failedTrials = trials.stream()
                .filter(trial -> trial.getErrorMessage() != null && !trial.getErrorMessage().isBlank())
                .toList();
        if (failedTrials.isEmpty()) {
            report.append("- None recorded.\n");
        } else {
            for (RunExperimentTrialRecord trial : failedTrials) {
                report.append("- Trial ")
                        .append(valueOrDash(trial.getId()))
                        .append(": ")
                        .append(markdownText(trial.getErrorMessage()))
                        .append("\n");
            }
        }
        return report.toString();
    }

    private Map<Long, ConversationMessageRecord> loadOutputMessages(String userId, RunExperimentRecord experiment) {
        if (branchRepositoryPort == null || experiment.getConversationId() == null) {
            return Map.of();
        }
        Map<Long, ConversationMessageRecord> messages = new LinkedHashMap<>();
        try {
            for (ConversationMessageRecord record : branchRepositoryPort.listTree(
                    String.valueOf(experiment.getConversationId()),
                    userId)) {
                Long id = parseLong(record.getId());
                if (id != null) {
                    messages.put(id, record);
                }
            }
        } catch (RuntimeException ignored) {
            return Map.of();
        }
        return messages;
    }

    private Map<String, RunContextSnapshotRecord> loadRunSnapshots(List<RunExperimentTrialRecord> trials) {
        if (runContextSnapshotRepositoryPort == null) {
            return Map.of();
        }
        Map<String, RunContextSnapshotRecord> snapshots = new LinkedHashMap<>();
        for (RunExperimentTrialRecord trial : Objects.requireNonNullElse(trials, List.<RunExperimentTrialRecord>of())) {
            if (trial == null || trial.getRunId() == null || trial.getRunId().isBlank()) {
                continue;
            }
            try {
                runContextSnapshotRepositoryPort.findByRunId(trial.getRunId().trim())
                        .ifPresent(snapshot -> snapshots.put(trial.getRunId().trim(), snapshot));
            } catch (RuntimeException ignored) {
                return Map.of();
            }
        }
        return snapshots;
    }

    private String firstOutputContent(List<RunExperimentTrialRecord> trials,
                                      Map<Long, ConversationMessageRecord> outputMessages) {
        for (RunExperimentTrialRecord trial : trials) {
            ConversationMessageRecord message = outputMessages.get(trial.getOutputMessageId());
            if (message != null) {
                return Objects.requireNonNullElse(message.getContent(), "");
            }
        }
        return "";
    }

    private String diffAgainstBaseline(String baseline, String content) {
        if (baseline == null || baseline.isBlank()) {
            return "baseline output not available";
        }
        if (content == null || content.isBlank()) {
            return "output not available";
        }
        return Objects.equals(normalizeWhitespace(baseline), normalizeWhitespace(content))
                ? "same as first trial"
                : "differs from first trial";
    }

    private String traceEvidence(RunExperimentTrialRecord trial, RunContextSnapshotRecord snapshot) {
        String directUrl = firstJsonScalar(
                snapshot == null ? null : snapshot.getTraceContextJson(),
                "studioTraceUrl",
                "traceUrl");
        String traceId = firstNonBlank(
                firstJsonScalar(snapshot == null ? null : snapshot.getTraceContextJson(), "studioTraceId"),
                firstJsonScalar(snapshot == null ? null : snapshot.getTraceContextJson(), "traceId"),
                jsonScalar(trial.getMetricJson(), "traceId"));
        if (directUrl != null && traceId != null) {
            return "studio=[" + traceId + "](" + directUrl + ")";
        }
        String studioUrl = firstNonBlank(
                firstJsonScalar(snapshot == null ? null : snapshot.getTraceContextJson(), "studioUrl", "tracingUrl"),
                firstJsonScalar(snapshot == null ? null : snapshot.getExecutorConfigJson(), "studioUrl", "tracingUrl"));
        if (studioUrl != null && traceId != null) {
            return "studio=[" + traceId + "](" + studioTraceUrl(studioUrl, traceId) + ")";
        }
        if (traceId != null) {
            return "traceId=" + traceId;
        }
        if (trial.getRunId() != null && !trial.getRunId().isBlank()) {
            return "runId=" + trial.getRunId();
        }
        return "not recorded";
    }

    private String costEvidence(RunExperimentTrialRecord trial, RunContextSnapshotRecord snapshot) {
        Optional<CostUsageAggregate> aggregate = aggregateCost(trial, snapshot);
        if (aggregate.isPresent() && aggregate.get().recordCount() > 0) {
            CostUsageAggregate value = aggregate.get();
            return "sa_cost_usage_record cost=" + decimalText(value.totalCost())
                    + " tokens=" + value.totalTokens()
                    + " calls=" + value.totalCalls()
                    + " records=" + value.recordCount();
        }
        for (String key : List.of("cost", "totalCost", "estimatedCost")) {
            String value = jsonScalar(trial.getMetricJson(), key);
            if (value != null) {
                return key + "=" + value;
            }
        }
        return "not recorded";
    }

    private Optional<CostUsageAggregate> aggregateCost(RunExperimentTrialRecord trial, RunContextSnapshotRecord snapshot) {
        if (costUsageRepositoryPort == null || trial == null || trial.getRunId() == null || trial.getRunId().isBlank()) {
            return Optional.empty();
        }
        String tenantId = firstNonBlank(
                trial.getTenantId(),
                snapshot == null ? null : snapshot.getTenantId(),
                "default");
        try {
            return Optional.ofNullable(costUsageRepositoryPort.aggregate(new CostUsageQuery(
                    tenantId,
                    null,
                    trial.getRunId().trim(),
                    null,
                    null,
                    null)));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private String forkTarget(RunExperimentTrialRecord trial) {
        return trial.getOutputMessageId() == null ? "not available" : "message:" + trial.getOutputMessageId();
    }

    private String jsonScalar(String json, String key) {
        return firstJsonScalar(json, key);
    }

    private String firstJsonScalar(String json, String... keys) {
        if (json == null || json.isBlank() || keys == null || keys.length == 0) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            for (String key : Objects.requireNonNullElse(keys, new String[0])) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                JsonNode value = root.findValue(key);
                if (value == null || value.isMissingNode() || value.isNull() || value.isContainerNode()) {
                    continue;
                }
                return value.isTextual() ? value.asText() : value.toString();
            }
        } catch (JsonProcessingException ex) {
            return null;
        }
        return null;
    }

    private String studioTraceUrl(String studioUrl, String traceId) {
        String safeUrl = studioUrl == null ? "" : studioUrl.trim();
        String encodedTraceId = URLEncoder.encode(Objects.requireNonNullElse(traceId, ""), StandardCharsets.UTF_8);
        if (safeUrl.contains("{traceId}")) {
            return safeUrl.replace("{traceId}", encodedTraceId);
        }
        return safeUrl.replaceAll("/+$", "") + "/traces/" + encodedTraceId;
    }

    private String decimalText(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String firstNonBlank(String... values) {
        for (String value : Objects.requireNonNullElse(values, new String[0])) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String reportFileName(RunExperimentRecord experiment) {
        String name = experiment == null ? "run-experiment" : Objects.requireNonNullElse(
                experiment.getName(),
                "run-experiment");
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isBlank()) {
            slug = "run-experiment";
        }
        return slug + "-" + valueOrDash(experiment == null ? null : experiment.getId()) + ".md";
    }

    private String valueOrDash(Object value) {
        return value == null || value.toString().isBlank() ? "-" : value.toString();
    }

    private String tableCell(Object value) {
        return truncate(valueOrDash(value), 180)
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("|", "\\|");
    }

    private String markdownText(String value) {
        return Objects.requireNonNullElse(value, "").replace("\r\n", "\n").replace("\r", "\n");
    }

    private String codeBlockText(String value) {
        String safe = Objects.requireNonNullElse(value, "");
        return safe.replace("```", "` ` `");
    }

    private String truncate(String value, int maxLength) {
        String safe = Objects.requireNonNullElse(value, "");
        if (safe.length() <= maxLength) {
            return safe;
        }
        return safe.substring(0, maxLength) + "...";
    }

    private String normalizeWhitespace(String value) {
        return Objects.requireNonNullElse(value, "").trim().replaceAll("\\s+", " ");
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private RunExperimentTrialExecutorPort resolveTrialExecutor() {
        return Objects.requireNonNullElseGet(
                trialExecutorPortSupplier.get(),
                RunExperimentTrialExecutorPort::noop);
    }

    private RunExperimentDetails executeCreatedExperiment(
            String userId,
            RunExperimentDetails created,
            RunExperimentTrialExecutorPort trialExecutorPort) {
        RunExperimentRecord experiment = created.getExperiment();
        repositoryPort.updateExperimentOnlyStatus(userId, experiment.getId(), STATUS_RUNNING);
        boolean failed = false;
        for (RunExperimentTrialRecord trial : created.getTrials()) {
            RunExperimentTrialExecutionResult result = executeTrial(userId, experiment, trial, trialExecutorPort);
            String trialStatus = normalizeTrialStatus(result.getStatus());
            if (STATUS_FAILED.equals(trialStatus)) {
                failed = true;
            }
            repositoryPort.updateTrialExecution(
                    userId,
                    experiment.getId(),
                    trial.getId(),
                    trialStatus,
                    result.getRunId(),
                    result.getOutputMessageId(),
                    result.getMetricJson(),
                    result.getErrorMessage());
        }
        String finalStatus = failed ? STATUS_FAILED : STATUS_SUCCEEDED;
        return repositoryPort.updateExperimentOnlyStatus(userId, experiment.getId(), finalStatus)
                .orElseGet(() -> repositoryPort.findById(userId, experiment.getId()).orElse(created));
    }

    private RunExperimentTrialExecutionResult executeTrial(
            String userId,
            RunExperimentRecord experiment,
            RunExperimentTrialRecord trial,
            RunExperimentTrialExecutorPort trialExecutorPort) {
        try {
            return Objects.requireNonNullElseGet(
                    trialExecutorPort.execute(RunExperimentTrialExecutionRequest.builder()
                            .userId(userId)
                            .experimentId(experiment.getId())
                            .trialId(trial.getId())
                            .conversationId(experiment.getConversationId())
                            .baseLeafMessageId(experiment.getBaseLeafMessageId())
                            .runProfileId(trial.getRunProfileId())
                            .experimentName(experiment.getName())
                            .build()),
                    () -> RunExperimentTrialExecutionResult.builder()
                            .status(STATUS_FAILED)
                            .errorMessage("trial executor returned null")
                            .build());
        } catch (RuntimeException ex) {
            return RunExperimentTrialExecutionResult.builder()
                    .status(STATUS_FAILED)
                    .errorMessage(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getSimpleName()))
                    .build();
        }
    }

    private String normalizeTrialStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_SUCCEEDED;
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case STATUS_PENDING, STATUS_RUNNING, STATUS_SUCCEEDED, STATUS_FAILED, STATUS_CANCELLED -> normalized;
            default -> STATUS_FAILED;
        };
    }
}
