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

package com.miracle.ai.seahorse.agent.adapters.sandbox.container;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeContainerReapResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class ContainerSandboxRuntimeAdapter implements SandboxRuntimePort {

    private static final String SESSION_ID_PREFIX = "sandbox_container_";
    private static final String EXECUTION_ID_PREFIX = "sandbox_exec_container_";
    private static final String ARTIFACT_ID_PREFIX = "sandbox_artifact_container_";
    private static final String SCRIPT_NAME = "main.py";
    private static final String CSV_FORMAT = "csv";
    private static final String TSV_FORMAT = "tsv";
    private static final String JSON_FORMAT = "json";
    private static final String TXT_FORMAT = "txt";
    private static final String HTML_FORMAT = "html";
    private static final String MARKDOWN_FORMAT = "markdown";
    private static final String DOCX_FORMAT = "docx";
    private static final String BASE64_ENCODING = "base64";
    private static final String PLAIN_ENCODING = "plain";
    private static final String BROWSER_ACTION_SNAPSHOT = "snapshot";
    private static final String BROWSER_ACTION_EXTRACT_TEXT = "extract_text";
    private static final String CONTAINER_WORKSPACE = "/workspace";
    private static final String CONTAINER_NAME_PREFIX = "seahorse-sandbox-";
    private static final int MAX_FILE_CONVERSION_CONTENT_CHARS = 256 * 1024;
    private static final int MAX_BROWSER_HTML_CHARS = 256 * 1024;
    private static final int MAX_BROWSER_URL_CHARS = 2048;
    private static final int MAX_BROWSER_ALLOWED_HOSTS = 16;
    private static final int MAX_BROWSER_COOKIES = 16;
    private static final int MAX_BROWSER_COOKIE_NAME_CHARS = 128;
    private static final int MAX_BROWSER_COOKIE_VALUE_CHARS = 4096;
    private static final int MAX_BROWSER_SESSION_STATE_CHARS = 128 * 1024;
    private static final int MAX_BROWSER_SESSION_STATE_COOKIES = 32;
    private static final int MAX_BROWSER_SESSION_STATE_ORIGINS = 16;
    private static final int MAX_BROWSER_SESSION_STATE_LOCAL_STORAGE_ITEMS = 128;
    private static final int MAX_BROWSER_SESSION_STATE_NAME_CHARS = 256;
    private static final int MAX_BROWSER_SESSION_STATE_VALUE_CHARS = 8192;
    private static final int DEFAULT_BROWSER_VIEWPORT_WIDTH = 1280;
    private static final int DEFAULT_BROWSER_VIEWPORT_HEIGHT = 720;
    private static final int MIN_BROWSER_VIEWPORT_SIZE = 320;
    private static final int MAX_BROWSER_VIEWPORT_SIZE = 2400;

    private final ContainerSandboxAdapterProperties properties;
    private final ContainerCommandRunner commandRunner;
    private final Clock clock;
    private final Path workspaceRoot;
    private final String workspaceMountSourceRoot;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContainerSandboxRuntimeAdapter(ContainerSandboxAdapterProperties properties,
                                          ContainerCommandRunner commandRunner,
                                          Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.workspaceRoot = resolveWorkspaceRoot(properties.getWorkspaceRoot());
        this.workspaceMountSourceRoot = trimToNull(properties.getWorkspaceMountSourceRoot());
    }

    @Override
    public SandboxSession createSession(SandboxSessionRequest request) {
        SandboxSessionRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Instant now = clock.instant();
        String sessionId = SESSION_ID_PREFIX + SnowflakeIds.nextIdString();
        try {
            Files.createDirectories(workspaceForSession(sessionId));
            return SandboxSession.created(
                    sessionId,
                    safeRequest.tenantId(),
                    safeRequest.runId(),
                    safeRequest.runtimeType(),
                    safeRequest.profileId(),
                    safeRequest.expiresAt(),
                    now);
        } catch (IOException ex) {
            return SandboxSession.failed(
                    sessionId,
                    safeRequest.tenantId(),
                    safeRequest.runId(),
                    safeRequest.runtimeType(),
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    safeRequest.profileId(),
                    safeRequest.expiresAt(),
                    now);
        }
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionRequest request) {
        SandboxExecutionRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        SandboxSession session = safeRequest.session();
        Instant startedAt = clock.instant();
        String executionId = EXECUTION_ID_PREFIX + SnowflakeIds.nextIdString();
        if (session.runtimeType() != SandboxRuntimeType.CODE_INTERPRETER
                && session.runtimeType() != SandboxRuntimeType.FILE_CONVERSION
                && session.runtimeType() != SandboxRuntimeType.BROWSER_AUTOMATION) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED,
                    "container sandbox supports CODE_INTERPRETER, FILE_CONVERSION, and BROWSER_AUTOMATION only");
        }
        try {
            Path workspace = workspaceForSession(session.sessionId());
            Files.createDirectories(workspace);
            Set<Path> excludedArtifacts = prepareWorkspace(session.runtimeType(), safeRequest.input(), workspace);
            ContainerCommandResult commandResult = commandRunner.run(
                    containerCommand(session, workspace, safeRequest.networkRequested()));
            Instant finishedAt = clock.instant();
            if (commandResult.timedOut()) {
                SandboxExecution execution = new SandboxExecution(
                        executionId,
                        session.sessionId(),
                        session.runtimeType(),
                        SandboxExecutionStatus.TIMED_OUT,
                        summary("timed out", commandResult),
                        SandboxPolicyReasonCode.RUNTIME_TIMED_OUT,
                        startedAt,
                        finishedAt);
                return SandboxExecutionResult.failed(execution, SandboxPolicyReasonCode.RUNTIME_TIMED_OUT);
            }
            if (commandResult.exitCode() == 0) {
                SandboxExecution execution = new SandboxExecution(
                        executionId,
                        session.sessionId(),
                        session.runtimeType(),
                        SandboxExecutionStatus.SUCCEEDED,
                        summary("exitCode=0", commandResult),
                        SandboxPolicyReasonCode.VALID_REQUEST,
                        startedAt,
                        finishedAt);
                return SandboxExecutionResult.succeeded(
                        execution,
                        collectArtifacts(session, execution.executionId(), workspace, finishedAt, excludedArtifacts));
            }
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    summary("exitCode=" + commandResult.exitCode(), commandResult));
        } catch (IOException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime io failure: " + nullToEmpty(ex.getMessage()));
        } catch (UnsupportedFileConversionException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED,
                    ex.getMessage());
        } catch (UnsupportedBrowserAutomationException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED,
                    ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime invalid request: " + nullToEmpty(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime interrupted");
        } catch (RuntimeException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime failure: " + nullToEmpty(ex.getMessage()));
        }
    }

    private Set<Path> prepareWorkspace(SandboxRuntimeType runtimeType, String input, Path workspace) throws IOException {
        Path safeWorkspace = workspace.toAbsolutePath().normalize();
        if (runtimeType == SandboxRuntimeType.CODE_INTERPRETER) {
            Files.writeString(safeWorkspace.resolve(SCRIPT_NAME), input, StandardCharsets.UTF_8);
            return Set.of(safeWorkspace.resolve(SCRIPT_NAME));
        }
        if (runtimeType == SandboxRuntimeType.FILE_CONVERSION) {
            FileConversionRequest request = parseFileConversionRequest(input);
            Path inputPath = safeWorkspace.resolve(fileConversionInputName(request.sourceFormat()));
            Files.writeString(safeWorkspace.resolve(SCRIPT_NAME), fileConversionScript(request), StandardCharsets.UTF_8);
            if (BASE64_ENCODING.equals(request.contentEncoding())) {
                Files.write(inputPath, decodeBase64Content(request.content()));
            } else {
                Files.writeString(
                        inputPath,
                        request.content(),
                        StandardCharsets.UTF_8);
            }
            return Set.of(
                    safeWorkspace.resolve(SCRIPT_NAME),
                    inputPath);
        }
        if (runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION) {
            BrowserAutomationRequest request = parseBrowserAutomationRequest(input);
            Path inputPath = safeWorkspace.resolve(browserInputName());
            Path cookiesPath = safeWorkspace.resolve(browserCookiesName());
            Path sessionStateInputPath = safeWorkspace.resolve(browserSessionStateInputName());
            Files.writeString(safeWorkspace.resolve(SCRIPT_NAME), browserAutomationScript(request), StandardCharsets.UTF_8);
            LinkedHashSet<Path> excluded = new LinkedHashSet<>();
            excluded.add(safeWorkspace.resolve(SCRIPT_NAME));
            if (!request.cookies().isEmpty()) {
                Files.writeString(cookiesPath, jsonForScript(request.cookies()), StandardCharsets.UTF_8);
                excluded.add(cookiesPath);
            }
            if (hasText(request.sessionStateJson())) {
                Files.writeString(sessionStateInputPath, request.sessionStateJson(), StandardCharsets.UTF_8);
                excluded.add(sessionStateInputPath);
            }
            if (!hasText(request.url())) {
                Files.writeString(
                        inputPath,
                        request.html(),
                        StandardCharsets.UTF_8);
                excluded.add(inputPath);
                return Set.copyOf(excluded);
            }
            return Set.copyOf(excluded);
        }
        throw new IllegalArgumentException("unsupported sandbox runtime type: " + runtimeType);
    }

    private FileConversionRequest parseFileConversionRequest(String input) throws IOException {
        JsonNode root = objectMapper.readTree(nullToEmpty(input));
        String sourceFormat = normalizedFormat(root.path("sourceFormat").asText());
        String targetFormat = normalizedFormat(root.path("targetFormat").asText());
        String contentEncoding = normalizedContentEncoding(root.path("contentEncoding").asText(PLAIN_ENCODING));
        if (!isSupportedFileConversion(sourceFormat, targetFormat)) {
            throw new UnsupportedFileConversionException(
                    "container file conversion supports csv/tsv to json, json to csv/tsv, txt to html, html to txt, markdown/md to html/txt, and docx to txt only");
        }
        if (DOCX_FORMAT.equals(sourceFormat) && !BASE64_ENCODING.equals(contentEncoding)) {
            throw new IllegalArgumentException("docx file conversion contentEncoding must be base64");
        }
        if (!DOCX_FORMAT.equals(sourceFormat) && BASE64_ENCODING.equals(contentEncoding)) {
            throw new IllegalArgumentException("base64 contentEncoding is only supported for docx input");
        }
        String content = root.path("content").asText("");
        if (!hasText(content)) {
            throw new IllegalArgumentException("file conversion content is required");
        }
        if (content.length() > MAX_FILE_CONVERSION_CONTENT_CHARS) {
            throw new IllegalArgumentException(
                    "file conversion content exceeds " + MAX_FILE_CONVERSION_CONTENT_CHARS + " chars");
        }
        return new FileConversionRequest(sourceFormat, targetFormat, contentEncoding, content);
    }

    private BrowserAutomationRequest parseBrowserAutomationRequest(String input) throws IOException {
        JsonNode root = objectMapper.readTree(nullToEmpty(input));
        String action = normalizedBrowserAction(root.path("action").asText(BROWSER_ACTION_SNAPSHOT));
        if (!isSupportedBrowserAction(action)) {
            throw new UnsupportedBrowserAutomationException(
                    "container browser automation supports snapshot and extract_text actions only");
        }
        String html = root.path("html").asText("");
        String url = normalizedBrowserUrl(root.path("url").asText(""));
        List<String> allowedHosts = normalizedBrowserAllowedHosts(root.get("allowedHosts"));
        List<BrowserCookie> cookies = normalizedBrowserCookies(
                root.get("cookies"),
                allowedHosts,
                hasText(url) ? browserUrlHost(url) : "");
        if (!hasText(url) && !hasText(html)) {
            throw new IllegalArgumentException("browser automation html or url is required");
        }
        if (hasText(url) && allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("browser automation allowedHosts is required for url mode");
        }
        if (hasText(url) && !allowedHosts.contains(browserUrlHost(url))) {
            throw new IllegalArgumentException("browser automation url host must be included in allowedHosts");
        }
        if (html.length() > MAX_BROWSER_HTML_CHARS) {
            throw new IllegalArgumentException(
                    "browser automation html exceeds " + MAX_BROWSER_HTML_CHARS + " chars");
        }
        int viewportWidth = boundedInt(root,
                "viewportWidth",
                DEFAULT_BROWSER_VIEWPORT_WIDTH,
                MIN_BROWSER_VIEWPORT_SIZE,
                MAX_BROWSER_VIEWPORT_SIZE);
        int viewportHeight = boundedInt(root,
                "viewportHeight",
                DEFAULT_BROWSER_VIEWPORT_HEIGHT,
                MIN_BROWSER_VIEWPORT_SIZE,
                MAX_BROWSER_VIEWPORT_SIZE);
        boolean screenshot = root.path("screenshot").isMissingNode()
                ? BROWSER_ACTION_SNAPSHOT.equals(action)
                : root.path("screenshot").asBoolean(BROWSER_ACTION_SNAPSHOT.equals(action));
        boolean har = root.path("har").asBoolean(false);
        boolean video = root.path("video").asBoolean(false);
        boolean captureSessionState = root.path("captureSessionState").asBoolean(false);
        String sessionStateJson = normalizedBrowserSessionState(root.get("sessionState"), allowedHosts, hasText(url));
        if (captureSessionState && !hasText(url)) {
            throw new IllegalArgumentException("browser automation session state capture is only supported for url mode");
        }
        return new BrowserAutomationRequest(action,
                html,
                url,
                allowedHosts,
                cookies,
                viewportWidth,
                viewportHeight,
                screenshot,
                har,
                video,
                captureSessionState,
                sessionStateJson);
    }

    private String browserAutomationScript(BrowserAutomationRequest request) {
        return """
                import json
                from datetime import datetime, timezone
                from pathlib import Path
                from urllib.parse import urlparse
                from playwright.sync_api import sync_playwright

                action = "%s"
                target_url = %s
                allowed_hosts = set(%s)
                viewport_width = %d
                viewport_height = %d
                screenshot_enabled = %s
                har_enabled = %s
                video_enabled = %s
                capture_session_state = %s
                input_path = Path("/workspace/%s")
                cookies_path = Path("/workspace/%s")
                session_state_input_path = Path("/workspace/%s")
                result_path = Path("/workspace/%s")
                screenshot_path = Path("/workspace/%s")
                har_path = Path("/workspace/%s")
                video_dir = Path("/workspace/browser-video-recordings")
                video_path = Path("/workspace/%s")
                session_state_path = Path("/workspace/%s")
                session_summary_path = Path("/workspace/%s")

                def compact_text(value, limit=12000):
                    normalized = "\\n".join(line.strip() for line in value.replace("\\r", "\\n").split("\\n") if line.strip())
                    return normalized[:limit]

                def utc_now():
                    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

                def allowed_url(url):
                    if url.startswith(("about:", "blob:", "data:")):
                        return True
                    parsed = urlparse(url)
                    host = (parsed.hostname or "").lower()
                    return parsed.scheme in ("http", "https") and host in allowed_hosts

                def empty_har_request(method, url):
                    return {
                        "method": method,
                        "url": url,
                        "httpVersion": "HTTP/1.1",
                        "cookies": [],
                        "headers": [],
                        "queryString": [],
                        "headersSize": -1,
                        "bodySize": 0,
                    }

                def empty_har_response(status, status_text):
                    return {
                        "status": status,
                        "statusText": status_text,
                        "httpVersion": "HTTP/1.1",
                        "cookies": [],
                        "headers": [],
                        "content": {"size": 0, "mimeType": ""},
                        "redirectURL": "",
                        "headersSize": -1,
                        "bodySize": 0,
                    }

                def build_har(events):
                    entries = []
                    for event in events:
                        status = event.get("status") or 0
                        status_text = event.get("statusText") or event.get("failure") or ("blocked" if event.get("blocked") else "")
                        entries.append({
                            "startedDateTime": event["startedDateTime"],
                            "time": 0,
                            "request": empty_har_request(event["method"], event["url"]),
                            "response": empty_har_response(status, status_text),
                            "cache": {},
                            "timings": {"send": 0, "wait": 0, "receive": 0},
                            "_resourceType": event.get("resourceType"),
                            "_blocked": bool(event.get("blocked")),
                            "_failure": event.get("failure"),
                        })
                    return {
                        "log": {
                            "version": "1.2",
                            "creator": {"name": "seahorse-sandbox-browser", "version": "1"},
                            "pages": [{
                                "startedDateTime": utc_now(),
                                "id": "sandbox-browser-page",
                                "title": "sandbox browser",
                                "pageTimings": {},
                            }],
                            "entries": entries,
                        }
                    }

                def build_session_summary(state, current_url):
                    cookies = state.get("cookies") or []
                    origins = state.get("origins") or []
                    return {
                        "source": "url" if target_url else "html",
                        "targetUrl": target_url or None,
                        "url": current_url,
                        "cookies": {
                            "count": len(cookies),
                            "domains": sorted({cookie.get("domain") for cookie in cookies if cookie.get("domain")}),
                        },
                        "origins": [
                            {
                                "origin": origin.get("origin"),
                                "localStorageCount": len(origin.get("localStorage") or []),
                            }
                            for origin in origins
                            if origin.get("origin")
                        ],
                    }

                html = "" if target_url else input_path.read_text(encoding="utf-8-sig")
                browser_cookies = json.loads(cookies_path.read_text(encoding="utf-8")) if cookies_path.exists() else []
                browser_session_state = json.loads(session_state_input_path.read_text(encoding="utf-8")) if session_state_input_path.exists() else None
                network_events = []
                network_event_index = {}
                video_file = None

                with sync_playwright() as playwright:
                    browser = playwright.chromium.launch(
                        headless=True,
                        args=["--no-sandbox", "--disable-dev-shm-usage"],
                    )
                    context = None
                    try:
                        context_options = {
                            "viewport": {"width": viewport_width, "height": viewport_height},
                        }
                        if browser_session_state is not None:
                            context_options["storage_state"] = str(session_state_input_path)
                        if video_enabled:
                            video_dir.mkdir(parents=True, exist_ok=True)
                            context_options["record_video_dir"] = str(video_dir)
                            context_options["record_video_size"] = {"width": viewport_width, "height": viewport_height}
                        context = browser.new_context(**context_options)
                        if browser_cookies:
                            context.add_cookies(browser_cookies)
                        page = context.new_page()

                        def on_request(request):
                            event = {
                                "startedDateTime": utc_now(),
                                "method": request.method,
                                "url": request.url,
                                "resourceType": request.resource_type,
                                "status": 0,
                                "statusText": "",
                                "failure": None,
                                "blocked": not allowed_url(request.url),
                            }
                            network_event_index[id(request)] = event
                            network_events.append(event)

                        def on_response(response):
                            event = network_event_index.get(id(response.request))
                            if event is not None:
                                event["status"] = response.status
                                event["statusText"] = response.status_text

                        def on_request_failed(request):
                            event = network_event_index.get(id(request))
                            if event is not None:
                                failure = request.failure or "request failed"
                                event["failure"] = failure
                                event["statusText"] = failure

                        def block_external(route):
                            url = route.request.url
                            if allowed_url(url):
                                route.continue_()
                            else:
                                route.abort()

                        page.on("request", on_request)
                        page.on("response", on_response)
                        page.on("requestfailed", on_request_failed)
                        page.route("**/*", block_external)
                        if target_url:
                            page.goto(target_url, wait_until="load", timeout=10000)
                        else:
                            page.set_content(html, wait_until="load", timeout=10000)
                        if har_enabled:
                            page.wait_for_timeout(250)
                        title = page.title()
                        try:
                            body_text = page.locator("body").inner_text(timeout=3000)
                        except Exception:
                            body_text = page.content()
                        screenshot_file = None
                        if screenshot_enabled:
                            page.screenshot(path=str(screenshot_path), full_page=True)
                            screenshot_file = screenshot_path.name
                        session_summary_file = None
                        session_state_file = None
                        session_replay_summary = build_session_summary(browser_session_state, target_url) if browser_session_state is not None else None
                        if capture_session_state:
                            state = context.storage_state(path=str(session_state_path))
                            session_summary_path.write_text(
                                json.dumps(build_session_summary(state, page.url), ensure_ascii=False, indent=2),
                                encoding="utf-8",
                            )
                            session_summary_file = session_summary_path.name
                            session_state_file = session_state_path.name
                        result = {
                            "action": action,
                            "source": "url" if target_url else "html",
                            "title": title,
                            "url": page.url,
                            "targetUrl": target_url or None,
                            "allowedHosts": sorted(allowed_hosts),
                            "cookies": {
                                "count": len(browser_cookies),
                                "domains": sorted({cookie.get("domain") for cookie in browser_cookies if cookie.get("domain")}),
                            },
                            "text": compact_text(body_text),
                            "textLength": len(body_text),
                            "viewport": {
                                "width": viewport_width,
                                "height": viewport_height,
                            },
                            "screenshot": screenshot_file,
                            "har": har_path.name if har_enabled else None,
                            "video": video_path.name if video_enabled else None,
                            "sessionState": {
                                "replayed": bool(browser_session_state is not None),
                                "replay": session_replay_summary,
                                "captured": bool(capture_session_state),
                                "summary": session_summary_file,
                                "state": session_state_file,
                            },
                        }
                        result_path.write_text(
                            json.dumps(result, ensure_ascii=False, indent=2),
                            encoding="utf-8",
                        )
                        if har_enabled:
                            har_path.write_text(
                                json.dumps(build_har(network_events), ensure_ascii=False, indent=2),
                                encoding="utf-8",
                            )
                        if video_enabled:
                            context.close()
                            context = None
                            videos = sorted(
                                video_dir.glob("*.webm"),
                                key=lambda item: item.stat().st_mtime_ns,
                                reverse=True,
                            )
                            if not videos:
                                raise RuntimeError("browser video recording was not created")
                            videos[0].replace(video_path)
                    finally:
                        if context is not None:
                            context.close()
                        browser.close()

                print(f"browser {action} completed; textLength={len(body_text)}; screenshot={screenshot_enabled}; har={har_enabled}; video={video_enabled}; cookies={len(browser_cookies)}; sessionStateReplay={browser_session_state is not None}; sessionStateCapture={capture_session_state}")
                """.formatted(
                request.action(),
                jsonForScript(request.url()),
                jsonForScript(request.allowedHosts()),
                request.viewportWidth(),
                request.viewportHeight(),
                request.screenshot() ? "True" : "False",
                request.har() ? "True" : "False",
                request.video() ? "True" : "False",
                request.captureSessionState() ? "True" : "False",
                browserInputName(),
                browserCookiesName(),
                browserSessionStateInputName(),
                browserResultName(),
                browserScreenshotName(),
                browserHarName(),
                browserVideoName(),
                browserSessionStateName(),
                browserSessionSummaryName());
    }

    private String fileConversionScript(FileConversionRequest request) {
        return """
                import csv
                import html
                import json
                import re
                import xml.etree.ElementTree as ET
                import zipfile
                from html.parser import HTMLParser
                from pathlib import Path

                source_format = "%s"
                target_format = "%s"
                input_path = Path("/workspace/%s")
                output_path = Path("/workspace/%s")

                def delimiter(format_name):
                    return "\\t" if format_name == "tsv" else ","

                def normalize_cell(value):
                    if value is None:
                        return ""
                    if isinstance(value, (dict, list)):
                        return json.dumps(
                            value,
                            ensure_ascii=False,
                            sort_keys=True,
                            separators=(",", ":"),
                        )
                    return str(value)

                def read_json_rows():
                    raw = json.loads(input_path.read_text(encoding="utf-8-sig"))
                    if isinstance(raw, dict) and isinstance(raw.get("rows"), list):
                        rows = raw["rows"]
                    elif isinstance(raw, dict):
                        rows = [raw]
                    elif isinstance(raw, list):
                        rows = raw
                    else:
                        raise ValueError("json input must be an object, an array, or an object with a rows array")

                    normalized_rows = []
                    fieldnames = []
                    for index, item in enumerate(rows):
                        if not isinstance(item, dict):
                            raise ValueError(f"json row {index} must be an object")
                        normalized = {}
                        for key, value in item.items():
                            name = str(key)
                            if name not in fieldnames:
                                fieldnames.append(name)
                            normalized[name] = normalize_cell(value)
                        normalized_rows.append(normalized)
                    return normalized_rows, fieldnames

                class TextExtractor(HTMLParser):
                    def __init__(self):
                        super().__init__(convert_charrefs=True)
                        self.parts = []

                    def handle_starttag(self, tag, attrs):
                        if tag == "li":
                            self.parts.append("\\n- ")
                        elif tag in ("br", "p", "div", "section", "article", "tr", "h1", "h2", "h3", "h4", "h5", "h6"):
                            self.parts.append("\\n")

                    def handle_endtag(self, tag):
                        if tag in ("p", "div", "section", "article", "li", "tr", "h1", "h2", "h3", "h4", "h5", "h6"):
                            self.parts.append("\\n")

                    def handle_data(self, data):
                        self.parts.append(data)

                    def text(self):
                        raw = "".join(self.parts)
                        lines = []
                        for line in raw.splitlines():
                            collapsed = re.sub(r"[ \\t]+", " ", line).strip()
                            if collapsed:
                                lines.append(collapsed)
                        return "\\n".join(lines) + ("\\n" if lines else "")

                def html_to_text(value):
                    parser = TextExtractor()
                    parser.feed(value)
                    parser.close()
                    return parser.text()

                def text_to_html(value):
                    return "<!doctype html>\\n<html><body><pre>" + html.escape(value) + "</pre></body></html>\\n"

                def inline_markdown(value):
                    escaped = html.escape(value)
                    escaped = re.sub(r"`([^`]+)`", r"<code>\\1</code>", escaped)
                    escaped = re.sub(r"\\*\\*([^*]+)\\*\\*", r"<strong>\\1</strong>", escaped)
                    return escaped

                def markdown_to_html(value):
                    lines = value.replace("\\r\\n", "\\n").replace("\\r", "\\n").split("\\n")
                    output = ["<!doctype html>", "<html><body>"]
                    in_list = False
                    in_code = False
                    code_lines = []

                    def close_list():
                        nonlocal in_list
                        if in_list:
                            output.append("</ul>")
                            in_list = False

                    for line in lines:
                        stripped = line.strip()
                        if stripped.startswith("```"):
                            if in_code:
                                output.append("<pre><code>" + html.escape("\\n".join(code_lines)) + "</code></pre>")
                                code_lines = []
                                in_code = False
                            else:
                                close_list()
                                in_code = True
                            continue
                        if in_code:
                            code_lines.append(line)
                            continue
                        if not stripped:
                            close_list()
                            continue
                        heading_match = re.match(r"^(#{1,6})\\s+(.+)$", stripped)
                        if heading_match:
                            close_list()
                            level = len(heading_match.group(1))
                            output.append(f"<h{level}>" + inline_markdown(heading_match.group(2)) + f"</h{level}>")
                            continue
                        if stripped.startswith("- ") or stripped.startswith("* "):
                            if not in_list:
                                output.append("<ul>")
                                in_list = True
                            output.append("<li>" + inline_markdown(stripped[2:].strip()) + "</li>")
                            continue
                        close_list()
                        output.append("<p>" + inline_markdown(stripped) + "</p>")

                    if in_code:
                        output.append("<pre><code>" + html.escape("\\n".join(code_lines)) + "</code></pre>")
                    close_list()
                    output.extend(["</body></html>", ""])
                    return "\\n".join(output)

                def docx_to_text(path):
                    with zipfile.ZipFile(path) as archive:
                        try:
                            document_xml = archive.read("word/document.xml")
                        except KeyError as exc:
                            raise ValueError("docx word/document.xml not found") from exc
                    root = ET.fromstring(document_xml)
                    ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
                    paragraphs = []
                    for paragraph in root.findall(".//w:p", ns):
                        parts = []
                        for node in paragraph.findall(".//w:t", ns):
                            if node.text:
                                parts.append(node.text)
                        text = "".join(parts).strip()
                        if text:
                            paragraphs.append(text)
                    return "\\n".join(paragraphs) + ("\\n" if paragraphs else "")

                if target_format == "json" and source_format in ("csv", "tsv"):
                    with input_path.open("r", encoding="utf-8-sig", newline="") as source:
                        reader = csv.DictReader(source, delimiter=delimiter(source_format))
                        rows = [dict(row) for row in reader]

                    output_path.write_text(
                        json.dumps(rows, ensure_ascii=False, indent=2),
                        encoding="utf-8",
                    )
                    print(f"converted {len(rows)} rows from {source_format} to json")
                elif source_format == "json" and target_format in ("csv", "tsv"):
                    rows, fieldnames = read_json_rows()
                    with output_path.open("w", encoding="utf-8", newline="") as target:
                        writer = csv.DictWriter(target, fieldnames=fieldnames, delimiter=delimiter(target_format))
                        writer.writeheader()
                        writer.writerows(rows)
                    print(f"converted {len(rows)} rows from json to {target_format}")
                elif source_format == "txt" and target_format == "html":
                    raw = input_path.read_text(encoding="utf-8-sig")
                    output_path.write_text(text_to_html(raw), encoding="utf-8")
                    print(f"converted text document to html")
                elif source_format == "html" and target_format == "txt":
                    raw = input_path.read_text(encoding="utf-8-sig")
                    output_path.write_text(html_to_text(raw), encoding="utf-8")
                    print(f"converted html document to text")
                elif source_format == "markdown" and target_format == "html":
                    raw = input_path.read_text(encoding="utf-8-sig")
                    output_path.write_text(markdown_to_html(raw), encoding="utf-8")
                    print(f"converted markdown document to html")
                elif source_format == "markdown" and target_format == "txt":
                    raw = input_path.read_text(encoding="utf-8-sig")
                    output_path.write_text(html_to_text(markdown_to_html(raw)), encoding="utf-8")
                    print(f"converted markdown document to text")
                elif source_format == "docx" and target_format == "txt":
                    output_path.write_text(docx_to_text(input_path), encoding="utf-8")
                    print(f"converted docx document to text")
                else:
                    raise ValueError(f"unsupported conversion: {source_format} to {target_format}")
                """.formatted(
                request.sourceFormat(),
                request.targetFormat(),
                fileConversionInputName(request.sourceFormat()),
                fileConversionOutputName(request.targetFormat()));
    }

    private boolean isSupportedFileConversion(String sourceFormat, String targetFormat) {
        return (isDelimitedFileFormat(sourceFormat) && JSON_FORMAT.equals(targetFormat))
                || (JSON_FORMAT.equals(sourceFormat) && isDelimitedFileFormat(targetFormat))
                || (TXT_FORMAT.equals(sourceFormat) && HTML_FORMAT.equals(targetFormat))
                || (HTML_FORMAT.equals(sourceFormat) && TXT_FORMAT.equals(targetFormat))
                || (MARKDOWN_FORMAT.equals(sourceFormat)
                && (HTML_FORMAT.equals(targetFormat) || TXT_FORMAT.equals(targetFormat)))
                || (DOCX_FORMAT.equals(sourceFormat) && TXT_FORMAT.equals(targetFormat));
    }

    private boolean isDelimitedFileFormat(String format) {
        return CSV_FORMAT.equals(format) || TSV_FORMAT.equals(format);
    }

    private String fileConversionInputName(String sourceFormat) {
        String extension = switch (sourceFormat) {
            case MARKDOWN_FORMAT -> "md";
            case TXT_FORMAT -> "txt";
            case HTML_FORMAT -> "html";
            case DOCX_FORMAT -> "docx";
            default -> sourceFormat;
        };
        return "input." + extension;
    }

    private String fileConversionOutputName(String targetFormat) {
        return "converted." + targetFormat;
    }

    private boolean isSupportedBrowserAction(String action) {
        return BROWSER_ACTION_SNAPSHOT.equals(action) || BROWSER_ACTION_EXTRACT_TEXT.equals(action);
    }

    private String browserInputName() {
        return "browser-input.html";
    }

    private String browserCookiesName() {
        return "browser-cookies.json";
    }

    private String browserSessionStateInputName() {
        return "browser-session-state-input.json";
    }

    private String browserResultName() {
        return "browser-result.json";
    }

    private String browserScreenshotName() {
        return "screenshot.png";
    }

    private String browserHarName() {
        return "browser-network.har";
    }

    private String browserVideoName() {
        return "browser-video.webm";
    }

    private String browserSessionStateName() {
        return "browser-session-state.json";
    }

    private String browserSessionSummaryName() {
        return "browser-session-summary.json";
    }

    private int boundedInt(JsonNode root, String name, int defaultValue, int min, int max) {
        int parsed = defaultValue;
        JsonNode value = root.path(name);
        if (value.isNumber()) {
            parsed = value.asInt(defaultValue);
        } else if (value.isTextual() && hasText(value.asText())) {
            try {
                parsed = Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException ignored) {
                parsed = defaultValue;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }

    private String normalizedBrowserUrl(String value) {
        if (!hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_BROWSER_URL_CHARS) {
            throw new IllegalArgumentException("browser automation url exceeds " + MAX_BROWSER_URL_CHARS + " chars");
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || !hasText(uri.getHost())) {
                throw new IllegalArgumentException("browser automation url must be an HTTP/HTTPS URL with a host");
            }
            return uri.normalize().toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browser automation url is not valid", ex);
        }
    }

    private String browserUrlHost(String url) {
        try {
            return new URI(url).getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browser automation url is not valid", ex);
        }
    }

    private List<String> normalizedBrowserAllowedHosts(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (value.isArray()) {
            value.forEach(item -> addNormalizedBrowserHost(hosts, item.asText("")));
        } else if (value.isTextual()) {
            for (String item : value.asText("").split(",")) {
                addNormalizedBrowserHost(hosts, item);
            }
        } else {
            addNormalizedBrowserHost(hosts, value.asText(""));
        }
        if (hosts.size() > MAX_BROWSER_ALLOWED_HOSTS) {
            throw new IllegalArgumentException(
                    "browser automation allowedHosts exceeds " + MAX_BROWSER_ALLOWED_HOSTS + " hosts");
        }
        return new ArrayList<>(hosts);
    }

    private void addNormalizedBrowserHost(Set<String> hosts, String value) {
        if (!hasText(value)) {
            return;
        }
        String host = value.trim().toLowerCase(Locale.ROOT);
        if (host.contains("/") || host.contains(":") || !host.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("browser automation allowedHosts must contain host names only");
        }
        hosts.add(host);
    }

    private String normalizedBrowserSessionState(JsonNode value,
                                                 List<String> allowedHosts,
                                                 boolean urlMode) throws IOException {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "";
        }
        if (!urlMode) {
            throw new IllegalArgumentException("browser automation session state replay is only supported for url mode");
        }
        JsonNode state = value.isTextual()
                ? objectMapper.readTree(value.asText(""))
                : value;
        if (state == null || !state.isObject()) {
            throw new IllegalArgumentException("browser automation sessionState must be an object");
        }
        validateBrowserSessionStateCookies(state.get("cookies"), allowedHosts);
        validateBrowserSessionStateOrigins(state.get("origins"), allowedHosts);
        String serialized = objectMapper.writeValueAsString(state);
        if (serialized.length() > MAX_BROWSER_SESSION_STATE_CHARS) {
            throw new IllegalArgumentException("browser automation sessionState exceeds "
                    + MAX_BROWSER_SESSION_STATE_CHARS + " chars");
        }
        return serialized;
    }

    private void validateBrowserSessionStateCookies(JsonNode value, List<String> allowedHosts) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("browser automation sessionState cookies must be an array");
        }
        if (value.size() > MAX_BROWSER_SESSION_STATE_COOKIES) {
            throw new IllegalArgumentException("browser automation sessionState cookies exceeds "
                    + MAX_BROWSER_SESSION_STATE_COOKIES + " items");
        }
        for (JsonNode cookieNode : value) {
            if (!cookieNode.isObject()) {
                throw new IllegalArgumentException("browser automation sessionState cookie must be an object");
            }
            normalizedBrowserCookieName(cookieNode.path("name").asText(""));
            normalizedBrowserCookieValue(cookieNode.path("value").asText(""));
            String domain = normalizedBrowserCookieDomain(cookieNode.path("domain").asText(""));
            if (!allowedHosts.contains(browserCookieDomainHost(domain))) {
                throw new IllegalArgumentException(
                        "browser automation sessionState cookie domain must be included in allowedHosts");
            }
            normalizedBrowserCookiePath(cookieNode.path("path").asText("/"));
        }
    }

    private void validateBrowserSessionStateOrigins(JsonNode value, List<String> allowedHosts) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("browser automation sessionState origins must be an array");
        }
        if (value.size() > MAX_BROWSER_SESSION_STATE_ORIGINS) {
            throw new IllegalArgumentException("browser automation sessionState origins exceeds "
                    + MAX_BROWSER_SESSION_STATE_ORIGINS + " items");
        }
        for (JsonNode originNode : value) {
            if (!originNode.isObject()) {
                throw new IllegalArgumentException("browser automation sessionState origin must be an object");
            }
            String host = browserSessionStateOriginHost(originNode.path("origin").asText(""));
            if (!allowedHosts.contains(host)) {
                throw new IllegalArgumentException(
                        "browser automation sessionState origin host must be included in allowedHosts");
            }
            validateBrowserSessionStateLocalStorage(originNode.get("localStorage"));
        }
    }

    private void validateBrowserSessionStateLocalStorage(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("browser automation sessionState localStorage must be an array");
        }
        if (value.size() > MAX_BROWSER_SESSION_STATE_LOCAL_STORAGE_ITEMS) {
            throw new IllegalArgumentException("browser automation sessionState localStorage exceeds "
                    + MAX_BROWSER_SESSION_STATE_LOCAL_STORAGE_ITEMS + " items");
        }
        for (JsonNode item : value) {
            if (!item.isObject()) {
                throw new IllegalArgumentException("browser automation sessionState localStorage item must be an object");
            }
            boundedBrowserSessionStateText(item.path("name").asText(""),
                    "sessionState localStorage name",
                    MAX_BROWSER_SESSION_STATE_NAME_CHARS,
                    true);
            boundedBrowserSessionStateText(item.path("value").asText(""),
                    "sessionState localStorage value",
                    MAX_BROWSER_SESSION_STATE_VALUE_CHARS,
                    false);
        }
    }

    private String boundedBrowserSessionStateText(String value, String label, int maxChars, boolean required) {
        String text = value == null ? "" : value;
        if ((required && !hasText(text)) || text.length() > maxChars || containsControlCharacter(text)) {
            throw new IllegalArgumentException("browser automation " + label + " is invalid");
        }
        return text;
    }

    private String browserCookieDomainHost(String domain) {
        String host = domain.startsWith(".") ? domain.substring(1) : domain;
        if (!hasText(host) || !host.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("browser automation sessionState cookie domain is invalid");
        }
        return host;
    }

    private String browserSessionStateOriginHost(String origin) {
        if (!hasText(origin)) {
            throw new IllegalArgumentException("browser automation sessionState origin is required");
        }
        try {
            URI uri = new URI(origin.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || !hasText(uri.getHost())) {
                throw new IllegalArgumentException("browser automation sessionState origin must be HTTP/HTTPS");
            }
            return uri.getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browser automation sessionState origin is not valid", ex);
        }
    }

    private List<BrowserCookie> normalizedBrowserCookies(JsonNode value,
                                                         List<String> allowedHosts,
                                                         String urlHost) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new IllegalArgumentException("browser automation cookies must be an array");
        }
        if (value.size() > MAX_BROWSER_COOKIES) {
            throw new IllegalArgumentException(
                    "browser automation cookies exceeds " + MAX_BROWSER_COOKIES + " items");
        }
        if (value.isEmpty()) {
            return List.of();
        }
        if (!hasText(urlHost)) {
            throw new IllegalArgumentException("browser automation cookies are only supported for url mode");
        }
        List<BrowserCookie> cookies = new ArrayList<>();
        for (JsonNode cookieNode : value) {
            if (!cookieNode.isObject()) {
                throw new IllegalArgumentException("browser automation cookie must be an object");
            }
            String name = normalizedBrowserCookieName(cookieNode.path("name").asText(""));
            String cookieValue = normalizedBrowserCookieValue(cookieNode.path("value").asText(""));
            String domain = normalizedBrowserCookieDomain(cookieNode.path("domain").asText(urlHost));
            if (!allowedHosts.contains(domain)) {
                throw new IllegalArgumentException("browser automation cookie domain must be included in allowedHosts");
            }
            cookies.add(new BrowserCookie(
                    name,
                    cookieValue,
                    domain,
                    normalizedBrowserCookiePath(cookieNode.path("path").asText("/")),
                    cookieNode.path("httpOnly").asBoolean(true),
                    cookieNode.path("secure").asBoolean(false),
                    normalizedBrowserCookieSameSite(cookieNode.path("sameSite").asText("Lax"))));
        }
        return cookies;
    }

    private String normalizedBrowserCookieName(String value) {
        String name = value == null ? "" : value.trim();
        if (!hasText(name)) {
            throw new IllegalArgumentException("browser automation cookie name is required");
        }
        if (name.length() > MAX_BROWSER_COOKIE_NAME_CHARS
                || name.matches(".*[\\s;,=].*")
                || containsControlCharacter(name)) {
            throw new IllegalArgumentException("browser automation cookie name is invalid");
        }
        return name;
    }

    private String normalizedBrowserCookieValue(String value) {
        String cookieValue = value == null ? "" : value;
        if (cookieValue.length() > MAX_BROWSER_COOKIE_VALUE_CHARS || containsControlCharacter(cookieValue)) {
            throw new IllegalArgumentException("browser automation cookie value is invalid");
        }
        return cookieValue;
    }

    private String normalizedBrowserCookieDomain(String value) {
        String domain = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!hasText(domain) || domain.contains("/") || domain.contains(":") || !domain.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("browser automation cookie domain must be a host name only");
        }
        return domain;
    }

    private String normalizedBrowserCookiePath(String value) {
        String path = hasText(value) ? value.trim() : "/";
        if (!path.startsWith("/") || containsControlCharacter(path)) {
            throw new IllegalArgumentException("browser automation cookie path must start with /");
        }
        return path;
    }

    private String normalizedBrowserCookieSameSite(String value) {
        if (!hasText(value)) {
            return "Lax";
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
    }

    private boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(ch -> ch < 0x20 || ch == 0x7f);
    }

    private String jsonForScript(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalArgumentException("browser automation script input could not be serialized", ex);
        }
    }

    @Override
    public SandboxSession closeSession(SandboxSession session) {
        SandboxSession safeSession = Objects.requireNonNull(session, "session must not be null");
        deleteWorkspace(safeSession.sessionId());
        return safeSession.closed(clock.instant());
    }

    @Override
    public SandboxRuntimeCleanupResult sweepOrphanedResources(Set<String> activeSessionIds) {
        Set<String> safeActiveSessionIds = normalizeActiveSessionIds(activeSessionIds);
        Set<String> activeContainerNames = normalizeActiveContainerNames(safeActiveSessionIds);
        Instant sweptAt = clock.instant();
        Instant cutoff = sweptAt.minus(properties.getOrphanWorkspaceMinAge());
        int inspected = 0;
        int skippedActive = 0;
        int skippedRecent = 0;
        int removed = 0;
        int failed = 0;
        List<String> removedNames = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();
        try (var paths = Files.list(workspaceRoot)) {
            List<Path> candidates = paths
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isManagedWorkspaceDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path candidate : candidates) {
                inspected++;
                String workspaceName = candidate.getFileName().toString();
                if (safeActiveSessionIds.contains(workspaceName)) {
                    skippedActive++;
                    continue;
                }
                if (isRecentWorkspace(candidate, cutoff)) {
                    skippedRecent++;
                    continue;
                }
                if (deleteWorkspacePath(candidate)) {
                    removed++;
                    removedNames.add(workspaceName);
                } else {
                    failed++;
                    failedNames.add(workspaceName);
                }
            }
        } catch (IOException ex) {
            ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
            return new SandboxRuntimeCleanupResult(
                    sweptAt,
                    safeActiveSessionIds.size(),
                    inspected,
                    skippedActive,
                    skippedRecent,
                    removed,
                    failed + 1,
                    removedNames,
                    failedNames,
                    containerSummary.inspectedCount(),
                    containerSummary.activeCount(),
                    containerSummary.orphanCount(),
                    containerSummary.failedInspectionCount(),
                    containerSummary.activeNames(),
                    containerSummary.orphanNames(),
                    containerSummary.failureMessages());
        }
        ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
        return new SandboxRuntimeCleanupResult(
                sweptAt,
                safeActiveSessionIds.size(),
                inspected,
                skippedActive,
                skippedRecent,
                removed,
                failed,
                removedNames,
                failedNames,
                containerSummary.inspectedCount(),
                containerSummary.activeCount(),
                containerSummary.orphanCount(),
                containerSummary.failedInspectionCount(),
                containerSummary.activeNames(),
                containerSummary.orphanNames(),
                containerSummary.failureMessages());
    }

    @Override
    public SandboxRuntimeHealth inspectHealth(Set<String> activeSessionIds) {
        Set<String> safeActiveSessionIds = normalizeActiveSessionIds(activeSessionIds);
        Set<String> activeContainerNames = normalizeActiveContainerNames(safeActiveSessionIds);
        ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
        boolean workspaceAvailable = Files.isDirectory(workspaceRoot, LinkOption.NOFOLLOW_LINKS)
                && Files.isWritable(workspaceRoot);
        List<String> failureMessages = new ArrayList<>(containerSummary.failureMessages());
        if (!workspaceAvailable) {
            failureMessages.add("sandbox workspace root is not available");
        }
        boolean engineAvailable = containerSummary.failedInspectionCount() == 0;
        CapacitySummary capacitySummary = capacitySummary(safeActiveSessionIds.size());
        return new SandboxRuntimeHealth(
                clock.instant(),
                "container",
                properties.getEngine(),
                healthStatus(
                        engineAvailable,
                        workspaceAvailable,
                        capacitySummary.available(),
                        containerSummary.orphanCount(),
                        failureMessages),
                engineAvailable,
                workspaceAvailable,
                safeActiveSessionIds.size(),
                capacitySummary.limit(),
                capacitySummary.remaining(),
                capacitySummary.available(),
                capacitySummary.status(),
                containerSummary.inspectedCount(),
                containerSummary.activeCount(),
                containerSummary.orphanCount(),
                containerSummary.failedInspectionCount(),
                containerSummary.activeNames(),
                containerSummary.orphanNames(),
                failureMessages);
    }

    @Override
    public SandboxRuntimeContainerReapResult reapOrphanedContainers(Set<String> activeSessionIds, boolean dryRun) {
        Set<String> safeActiveSessionIds = normalizeActiveSessionIds(activeSessionIds);
        Set<String> activeContainerNames = normalizeActiveContainerNames(safeActiveSessionIds);
        Instant reapedAt = clock.instant();
        ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
        List<String> reapedNames = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();
        List<String> failureMessages = new ArrayList<>(containerSummary.failureMessages());
        if (containerSummary.failedInspectionCount() == 0 && !dryRun) {
            for (String containerName : containerSummary.orphanNames()) {
                if (removeManagedContainer(containerName)) {
                    reapedNames.add(containerName);
                } else {
                    failedNames.add(containerName);
                    failureMessages.add("failed to remove sandbox container " + containerName);
                }
            }
        }
        return new SandboxRuntimeContainerReapResult(
                reapedAt,
                dryRun,
                safeActiveSessionIds.size(),
                containerSummary.inspectedCount(),
                containerSummary.activeCount(),
                containerSummary.orphanCount(),
                containerSummary.failedInspectionCount(),
                reapedNames.size(),
                failedNames.size(),
                containerSummary.activeNames(),
                containerSummary.orphanNames(),
                reapedNames,
                failedNames,
                failureMessages);
    }

    private ContainerCommand containerCommand(SandboxSession session, Path workspace, boolean networkRequested) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("run");
        commandLine.add("--rm");
        commandLine.add("--name");
        commandLine.add(containerName(session.sessionId()));
        if (networkRequested) {
            commandLine.add("--add-host");
            commandLine.add("host.docker.internal:host-gateway");
        } else {
            commandLine.add("--network");
            commandLine.add("none");
        }
        commandLine.add("--memory");
        commandLine.add(memoryForRuntime(session.runtimeType()));
        commandLine.add("--cpus");
        commandLine.add(properties.getCpus());
        commandLine.add("--pids-limit");
        commandLine.add(Long.toString(properties.getPidsLimit()));
        commandLine.add("-v");
        commandLine.add(mountSourceForSession(session.sessionId(), workspace) + ":" + CONTAINER_WORKSPACE + ":rw");
        commandLine.add("-w");
        commandLine.add(CONTAINER_WORKSPACE);
        commandLine.add(imageForRuntime(session.runtimeType()));
        commandLine.add("python");
        commandLine.add(CONTAINER_WORKSPACE + "/" + SCRIPT_NAME);
        return new ContainerCommand(
                commandLine,
                workspace,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private String imageForRuntime(SandboxRuntimeType runtimeType) {
        if (runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION) {
            return properties.getBrowserImage();
        }
        return properties.getPythonImage();
    }

    private String memoryForRuntime(SandboxRuntimeType runtimeType) {
        if (runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION) {
            return properties.getBrowserMemory();
        }
        return properties.getMemory();
    }

    private ContainerCommand containerInspectionCommand() {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("ps");
        commandLine.add("-a");
        commandLine.add("--filter");
        commandLine.add("name=" + CONTAINER_NAME_PREFIX);
        commandLine.add("--format");
        commandLine.add("{{.Names}}\t{{.Status}}");
        return new ContainerCommand(
                commandLine,
                workspaceRoot,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private ContainerCommand containerRemoveCommand(String containerName) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("rm");
        commandLine.add("-f");
        commandLine.add(containerName);
        return new ContainerCommand(
                commandLine,
                workspaceRoot,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private ContainerInspectionSummary inspectManagedContainers(Set<String> activeContainerNames) {
        Set<String> safeActiveContainerNames = activeContainerNames == null
                ? Set.of()
                : Set.copyOf(activeContainerNames);
        try {
            ContainerCommandResult result = commandRunner.run(containerInspectionCommand());
            if (result.timedOut()) {
                return ContainerInspectionSummary.failed("container inspection timed out");
            }
            if (result.exitCode() != 0) {
                return ContainerInspectionSummary.failed(
                        "container inspection exitCode=" + result.exitCode() + "; stderr="
                                + oneLinePreview(result.stderr()));
            }
            List<String> activeNames = new ArrayList<>();
            List<String> orphanNames = new ArrayList<>();
            int inspectedCount = 0;
            for (String line : result.stdout().lines().toList()) {
                String name = containerNameFromPsLine(line);
                if (!hasText(name) || !isManagedContainerName(name)) {
                    continue;
                }
                inspectedCount++;
                if (safeActiveContainerNames.contains(name)) {
                    activeNames.add(name);
                } else {
                    orphanNames.add(name);
                }
            }
            activeNames.sort(String::compareTo);
            orphanNames.sort(String::compareTo);
            return new ContainerInspectionSummary(
                    inspectedCount,
                    activeNames.size(),
                    orphanNames.size(),
                    0,
                    activeNames,
                    orphanNames,
                    List.of());
        } catch (IOException ex) {
            return ContainerInspectionSummary.failed(
                    "container inspection io failure: " + nullToEmpty(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ContainerInspectionSummary.failed("container inspection interrupted");
        } catch (RuntimeException ex) {
            return ContainerInspectionSummary.failed(
                    "container inspection failure: " + nullToEmpty(ex.getMessage()));
        }
    }

    private boolean removeManagedContainer(String containerName) {
        if (!isManagedContainerName(containerName)) {
            return false;
        }
        try {
            ContainerCommandResult result = commandRunner.run(containerRemoveCommand(containerName));
            return !result.timedOut() && result.exitCode() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private CapacitySummary capacitySummary(int activeSessionCount) {
        int limit = Math.max(properties.getMaxActiveSessions(), 0);
        if (limit == 0) {
            return new CapacitySummary(0, 0, true, SandboxRuntimeHealth.CAPACITY_UNBOUNDED);
        }
        int remaining = Math.max(limit - Math.max(activeSessionCount, 0), 0);
        if (remaining == 0) {
            return new CapacitySummary(limit, 0, false, SandboxRuntimeHealth.CAPACITY_SATURATED);
        }
        return new CapacitySummary(limit, remaining, true, SandboxRuntimeHealth.CAPACITY_AVAILABLE);
    }

    private String healthStatus(boolean engineAvailable,
                                boolean workspaceAvailable,
                                boolean capacityAvailable,
                                int orphanContainerCount,
                                List<String> failureMessages) {
        if (!engineAvailable || !workspaceAvailable) {
            return SandboxRuntimeHealth.STATUS_UNAVAILABLE;
        }
        if (!capacityAvailable || orphanContainerCount > 0 || !failureMessages.isEmpty()) {
            return SandboxRuntimeHealth.STATUS_DEGRADED;
        }
        return SandboxRuntimeHealth.STATUS_HEALTHY;
    }

    private List<SandboxArtifact> collectArtifacts(SandboxSession session,
                                                   String executionId,
                                                   Path workspace,
                                                   Instant createdAt,
                                                   Set<Path> excludedArtifacts) throws IOException {
        if (!Files.exists(workspace)) {
            return List.of();
        }
        Path safeWorkspace = workspace.toAbsolutePath().normalize();
        Set<Path> safeExcludedArtifacts = excludedArtifacts == null
                ? Set.of()
                : excludedArtifacts.stream()
                .filter(Objects::nonNull)
                .map(path -> path.toAbsolutePath().normalize())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        try (var paths = Files.walk(safeWorkspace)) {
            return paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> path.startsWith(safeWorkspace))
                    .filter(path -> !safeExcludedArtifacts.contains(path))
                    .sorted(Comparator.comparing(path -> safeWorkspace.relativize(path).toString()))
                    .map(path -> artifact(session, executionId, path, createdAt))
                    .toList();
        }
    }

    private SandboxArtifact artifact(SandboxSession session, String executionId, Path path, Instant createdAt) {
        return new SandboxArtifact(
                ARTIFACT_ID_PREFIX + SnowflakeIds.nextIdString(),
                session.sessionId(),
                executionId,
                path.toUri().toString(),
                mediaType(path),
                SandboxArtifactScanStatus.PENDING,
                artifactSensitivity(path),
                createdAt);
    }

    private ContextSensitivity artifactSensitivity(Path path) {
        String name = path.getFileName() == null
                ? ""
                : path.getFileName().toString();
        if (browserSessionStateName().equals(name)) {
            return ContextSensitivity.SECRET;
        }
        return ContextSensitivity.INTERNAL;
    }

    private String mediaType(Path path) {
        String known = knownMediaType(path);
        if (known != null) {
            return known;
        }
        try {
            String probed = Files.probeContentType(path);
            if (hasText(probed)) {
                return probed.trim();
            }
        } catch (IOException ignored) {
            // Fall through to a conservative binary type.
        }
        return "application/octet-stream";
    }

    private String knownMediaType(Path path) {
        String name = path.getFileName() == null
                ? ""
                : path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1) : "";
        return switch (extension) {
            case "txt", "log" -> "text/plain";
            case "md", "markdown" -> "text/markdown";
            case "csv" -> "text/csv";
            case "tsv" -> "text/tab-separated-values";
            case "html", "htm" -> "text/html";
            case "py" -> "text/x-python";
            case "yaml", "yml" -> "text/yaml";
            case "json" -> "application/json";
            case "har" -> "application/har+json";
            case "xml" -> "application/xml";
            case "pdf" -> "application/pdf";
            case "gif" -> "image/gif";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "webm" -> "video/webm";
            default -> null;
        };
    }

    private SandboxExecutionResult failedResult(String executionId,
                                                SandboxSession session,
                                                Instant startedAt,
                                                SandboxPolicyReasonCode reasonCode,
                                                String summary) {
        Instant finishedAt = clock.instant();
        SandboxExecution execution = new SandboxExecution(
                executionId,
                session.sessionId(),
                session.runtimeType(),
                SandboxExecutionStatus.FAILED,
                summary,
                reasonCode,
                startedAt,
                finishedAt);
        return SandboxExecutionResult.failed(execution, reasonCode);
    }

    private String summary(String prefix, ContainerCommandResult result) {
        return "%s; durationMs=%d; stdout=%s; stderr=%s".formatted(
                prefix,
                Math.max(0L, result.duration().toMillis()),
                oneLinePreview(result.stdout()),
                oneLinePreview(result.stderr()));
    }

    private String oneLinePreview(String value) {
        String preview = nullToEmpty(value)
                .replace('\r', '\n')
                .lines()
                .limit(8)
                .reduce((left, right) -> left + "\\n" + right)
                .orElse("");
        if (preview.length() <= 512) {
            return preview;
        }
        return preview.substring(0, 512);
    }

    private Path workspaceForSession(String sessionId) {
        String safeName = safeFilesystemName(sessionId);
        Path workspace = workspaceRoot.resolve(safeName).toAbsolutePath().normalize();
        if (!workspace.startsWith(workspaceRoot) || workspace.equals(workspaceRoot)) {
            throw new IllegalArgumentException("invalid sandbox session workspace");
        }
        return workspace;
    }

    private String mountSourceForSession(String sessionId, Path workspace) {
        if (workspaceMountSourceRoot == null) {
            return workspace.toAbsolutePath().normalize().toString();
        }
        return stripTrailingSeparators(workspaceMountSourceRoot) + "/" + safeFilesystemName(sessionId);
    }

    private void deleteWorkspace(String sessionId) {
        Path workspace = workspaceForSession(sessionId);
        if (!Files.exists(workspace)) {
            return;
        }
        deleteWorkspacePath(workspace);
    }

    private boolean deleteWorkspacePath(Path workspace) {
        Path safeWorkspace = workspace.toAbsolutePath().normalize();
        if (!safeWorkspace.startsWith(workspaceRoot) || safeWorkspace.equals(workspaceRoot)) {
            return false;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                Path safePath = path.toAbsolutePath().normalize();
                if (!safePath.startsWith(workspaceRoot) || safePath.equals(workspaceRoot)) {
                    return;
                }
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup; session close still records a terminal state.
                }
            });
            return !Files.exists(safeWorkspace);
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isManagedWorkspaceDirectory(Path path) {
        Path safePath = path.toAbsolutePath().normalize();
        if (!safePath.startsWith(workspaceRoot) || safePath.equals(workspaceRoot)) {
            return false;
        }
        Path filename = safePath.getFileName();
        return filename != null && filename.toString().startsWith(SESSION_ID_PREFIX);
    }

    private boolean isManagedContainerName(String value) {
        return hasText(value) && value.startsWith(CONTAINER_NAME_PREFIX);
    }

    private String containerNameFromPsLine(String line) {
        if (!hasText(line)) {
            return "";
        }
        String[] parts = line.split("\\t", 2);
        return parts[0].trim();
    }

    private boolean isRecentWorkspace(Path workspace, Instant cutoff) {
        try {
            FileTime modifiedTime = Files.getLastModifiedTime(workspace, LinkOption.NOFOLLOW_LINKS);
            return modifiedTime.toInstant().isAfter(cutoff);
        } catch (IOException ex) {
            return true;
        }
    }

    private Set<String> normalizeActiveSessionIds(Set<String> activeSessionIds) {
        if (activeSessionIds == null || activeSessionIds.isEmpty()) {
            return Set.of();
        }
        Set<String> safeNames = new HashSet<>();
        for (String sessionId : activeSessionIds) {
            if (hasText(sessionId)) {
                safeNames.add(safeFilesystemName(sessionId.trim()));
            }
        }
        return Set.copyOf(safeNames);
    }

    private Set<String> normalizeActiveContainerNames(Set<String> activeSessionIds) {
        if (activeSessionIds == null || activeSessionIds.isEmpty()) {
            return Set.of();
        }
        Set<String> safeNames = new HashSet<>();
        for (String sessionId : activeSessionIds) {
            if (hasText(sessionId)) {
                safeNames.add(containerName(sessionId));
            }
        }
        return Set.copyOf(safeNames);
    }

    private String containerName(String sessionId) {
        String base = CONTAINER_NAME_PREFIX + safeFilesystemName(sessionId).toLowerCase(Locale.ROOT);
        if (base.length() <= 96) {
            return base;
        }
        return base.substring(0, 96);
    }

    private String safeFilesystemName(String value) {
        String safe = nullToEmpty(value).replaceAll("[^A-Za-z0-9_.-]", "_");
        if (safe.isBlank()) {
            throw new IllegalArgumentException("sandbox session id must not be blank");
        }
        return safe;
    }

    private Path resolveWorkspaceRoot(String configuredRoot) {
        try {
            Path root = hasText(configuredRoot)
                    ? Path.of(configuredRoot)
                    : Path.of(System.getProperty("java.io.tmpdir"), "seahorse-sandbox-container");
            Path normalized = root.toAbsolutePath().normalize();
            Files.createDirectories(normalized);
            return normalized;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create sandbox workspace root", ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalizedFormat(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "md".equals(normalized) ? MARKDOWN_FORMAT : normalized;
    }

    private static String normalizedContentEncoding(String value) {
        if (value == null || value.trim().isEmpty()) {
            return PLAIN_ENCODING;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return BASE64_ENCODING.equals(normalized) ? BASE64_ENCODING : PLAIN_ENCODING;
    }

    private static String normalizedBrowserAction(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static byte[] decodeBase64Content(String value) {
        try {
            return Base64.getDecoder().decode(nullToEmpty(value).trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("file conversion content is not valid base64", ex);
        }
    }

    private static String stripTrailingSeparators(String value) {
        String result = Objects.requireNonNull(value, "value must not be null").trim();
        while (result.endsWith("/") || result.endsWith("\\")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("workspaceMountSourceRoot must not be empty");
        }
        return result;
    }

    private record FileConversionRequest(String sourceFormat,
                                         String targetFormat,
                                         String contentEncoding,
                                         String content) {}

    private record BrowserAutomationRequest(String action,
                                            String html,
                                            String url,
                                            List<String> allowedHosts,
                                            List<BrowserCookie> cookies,
                                            int viewportWidth,
                                            int viewportHeight,
                                            boolean screenshot,
                                            boolean har,
                                            boolean video,
                                            boolean captureSessionState,
                                            String sessionStateJson) {}

    private record BrowserCookie(String name,
                                 String value,
                                 String domain,
                                 String path,
                                 boolean httpOnly,
                                 boolean secure,
                                 String sameSite) {}

    private static final class UnsupportedFileConversionException extends RuntimeException {

        private UnsupportedFileConversionException(String message) {
            super(message);
        }
    }

    private static final class UnsupportedBrowserAutomationException extends RuntimeException {

        private UnsupportedBrowserAutomationException(String message) {
            super(message);
        }
    }

    private record ContainerInspectionSummary(int inspectedCount,
                                              int activeCount,
                                              int orphanCount,
                                              int failedInspectionCount,
                                              List<String> activeNames,
                                              List<String> orphanNames,
                                              List<String> failureMessages) {

        private ContainerInspectionSummary {
            activeNames = activeNames == null ? List.of() : List.copyOf(activeNames);
            orphanNames = orphanNames == null ? List.of() : List.copyOf(orphanNames);
            failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
        }

        private static ContainerInspectionSummary failed(String message) {
            return new ContainerInspectionSummary(
                    0,
                    0,
                    0,
                    1,
                    List.of(),
                    List.of(),
                    List.of(nullToEmpty(message)));
        }
    }

    private record CapacitySummary(int limit,
                                   int remaining,
                                   boolean available,
                                   String status) {}
}
