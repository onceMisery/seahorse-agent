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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 浏览器自动化请求解析、脚本生成与浏览器状态/代理规范化的协作者（从 {@link ContainerSandboxRuntimeAdapter} 提取）。
 * 按 §7 收敛原则外提：浏览器自动化请求解析、脚本生成与浏览器状态/代理规范化的协作者。
 */
final class ContainerBrowserAutomationSupport {

    private static final String BROWSER_ACTION_SNAPSHOT = "snapshot";
    private static final String BROWSER_ACTION_EXTRACT_TEXT = "extract_text";
    private static final int MAX_BROWSER_HTML_CHARS = 256 * 1024;
    private static final int MAX_BROWSER_URL_CHARS = 2048;
    private static final int MAX_BROWSER_URL_QUERY_CHARS = 512;
    private static final int MAX_BROWSER_ALLOWED_HOSTS = 16;
    private static final int MAX_BROWSER_COOKIES = 16;
    private static final int MAX_BROWSER_COOKIE_NAME_CHARS = 128;
    private static final int MAX_BROWSER_COOKIE_VALUE_CHARS = 4096;
    private static final int MAX_BROWSER_PROXY_SERVERS = 8;
    private static final int MAX_BROWSER_SESSION_STATE_CHARS = 128 * 1024;
    private static final int MAX_BROWSER_SESSION_STATE_COOKIES = 32;
    private static final int MAX_BROWSER_SESSION_STATE_ORIGINS = 16;
    private static final int MAX_BROWSER_SESSION_STATE_LOCAL_STORAGE_ITEMS = 128;
    private static final int MAX_BROWSER_SESSION_STATE_NAME_CHARS = 256;
    private static final int MAX_BROWSER_SESSION_STATE_VALUE_CHARS = 8192;
    private static final Set<String> BROWSER_SESSION_STATE_KEYS = Set.of("cookies", "origins");
    private static final Set<String> BROWSER_SESSION_STATE_COOKIE_KEYS = Set.of(
            "name", "value", "domain", "path", "expires", "httpOnly", "secure", "sameSite");
    private static final Set<String> BROWSER_SESSION_STATE_ORIGIN_KEYS = Set.of("origin", "localStorage");
    private static final Set<String> BROWSER_SESSION_STATE_LOCAL_STORAGE_KEYS = Set.of("name", "value");
    private static final int DEFAULT_BROWSER_VIEWPORT_WIDTH = 1280;
    private static final int DEFAULT_BROWSER_VIEWPORT_HEIGHT = 720;
    private static final int MIN_BROWSER_VIEWPORT_SIZE = 320;
    private static final int MAX_BROWSER_VIEWPORT_SIZE = 2400;
    private static final Set<String> SENSITIVE_BROWSER_QUERY_PARAMETER_NAMES = Set.of(
            "accesstoken",
            "apikey",
            "authorization",
            "authtoken",
            "bearer",
            "bearertoken",
            "clientsecret",
            "credential",
            "credentials",
            "idtoken",
            "oauthtoken",
            "password",
            "refreshtoken",
            "secret",
            "session",
            "sessionid",
            "sessiontoken",
            "token");

    private final ContainerSandboxAdapterProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicInteger browserProxyCursor;
    private final ContainerNetworkBoundarySupport networkBoundarySupport;

    ContainerBrowserAutomationSupport(ContainerSandboxAdapterProperties properties,
                                      ObjectMapper objectMapper,
                                      AtomicInteger browserProxyCursor,
                                      ContainerNetworkBoundarySupport networkBoundarySupport) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.browserProxyCursor = Objects.requireNonNull(browserProxyCursor, "browserProxyCursor must not be null");
        this.networkBoundarySupport = Objects.requireNonNull(networkBoundarySupport,
                "networkBoundarySupport must not be null");
    }

    BrowserAutomationRequest parseBrowserAutomationRequest(String input,
                                                                   List<String> browserPrivateNetworkAllowedHosts)
            throws IOException {
        JsonNode root = objectMapper.readTree(ContainerSandboxTextSupport.nullToEmpty(input));
        String action = normalizedBrowserAction(root.path("action").asText(BROWSER_ACTION_SNAPSHOT));
        if (!isSupportedBrowserAction(action)) {
            throw new UnsupportedBrowserAutomationException(
                    "container browser automation supports snapshot and extract_text actions only");
        }
        String html = root.path("html").asText("");
        String url = normalizedBrowserUrl(root.path("url").asText(""));
        String urlHost = ContainerSandboxTextSupport.hasText(url) ? networkBoundarySupport.browserUrlHost(url) : "";
        String urlOrigin = ContainerSandboxTextSupport.hasText(url) ? networkBoundarySupport.browserUrlOrigin(url, "url") : "";
        List<String> allowedHosts = normalizedBrowserAllowedHosts(root.get("allowedHosts"));
        List<BrowserCookie> cookies = normalizedBrowserCookies(
                root.get("cookies"),
                allowedHosts,
                urlHost);
        if (!ContainerSandboxTextSupport.hasText(url) && !ContainerSandboxTextSupport.hasText(html)) {
            throw new IllegalArgumentException("browser automation html or url is required");
        }
        if (ContainerSandboxTextSupport.hasText(url) && allowedHosts.isEmpty()) {
            throw new IllegalArgumentException("browser automation allowedHosts is required for url mode");
        }
        if (ContainerSandboxTextSupport.hasText(url) && !allowedHosts.contains(urlHost)) {
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
        String sessionStateJson = normalizedBrowserSessionState(root.get("sessionState"), allowedHosts, urlHost, urlOrigin, ContainerSandboxTextSupport.hasText(url));
        if (captureSessionState && !ContainerSandboxTextSupport.hasText(url)) {
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
                sessionStateJson,
                effectiveBrowserPrivateNetworkAllowedHosts(browserPrivateNetworkAllowedHosts));
    }
    String browserAutomationScript(BrowserAutomationRequest request) {
        List<String> browserProxyServers = normalizedBrowserProxyServers();
        String browserProxyServer = selectBrowserProxyServer(browserProxyServers);
        BrowserProxyCredentials proxyCredentials = browserProxyCredentials(browserProxyServers);
        return """
                import json
                import ipaddress
                import socket
                from datetime import datetime, timezone
                from pathlib import Path
                from urllib.parse import unquote_plus, urlparse
                from playwright.sync_api import sync_playwright

                action = "%s"
                target_url = %s
                browser_proxy_server = %s
                browser_proxy_username = %s
                browser_proxy_password = %s
                browser_proxy_pool_size = %d
                allowed_hosts = set(%s)
                private_network_allowed_hosts = set(%s)
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
                max_session_state_bytes = %d

                def compact_text(value, limit=12000):
                    normalized = "\\n".join(line.strip() for line in value.replace("\\r", "\\n").split("\\n") if line.strip())
                    return normalized[:limit]

                def utc_now():
                    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")

                sensitive_query_parameter_names = {
                    "accesstoken",
                    "apikey",
                    "authorization",
                    "authtoken",
                    "bearer",
                    "bearertoken",
                    "clientsecret",
                    "credential",
                    "credentials",
                    "idtoken",
                    "oauthtoken",
                    "password",
                    "refreshtoken",
                    "secret",
                    "session",
                    "sessionid",
                    "sessiontoken",
                    "token",
                }

                def normalized_query_parameter_name(value):
                    name = unquote_plus(value).lower()
                    bracket_index = name.find("[")
                    if bracket_index > 0:
                        name = name[:bracket_index]
                    return "".join(ch for ch in name if ch.isalnum())

                def has_credential_url_parts(url):
                    parsed = urlparse(url)
                    if parsed.username or parsed.password or parsed.fragment:
                        return True
                    for parameter in parsed.query.replace(";", "&").split("&"):
                        if not parameter:
                            continue
                        name = parameter.split("=", 1)[0]
                        if normalized_query_parameter_name(name) in sensitive_query_parameter_names:
                            return True
                    return False

                host_resolution_cache = {}

                def is_private_network_address(value):
                    try:
                        return not ipaddress.ip_address(value).is_global
                    except ValueError:
                        return True

                def resolved_host_decision(host):
                    cached = host_resolution_cache.get(host)
                    if cached is not None:
                        return cached
                    private_network_allowed = host in private_network_allowed_hosts
                    try:
                        infos = socket.getaddrinfo(host, None, type=socket.SOCK_STREAM)
                    except OSError:
                        decision = (False, "dns_resolution_failed")
                        host_resolution_cache[host] = decision
                        return decision
                    addresses = sorted({item[4][0] for item in infos if len(item) >= 5 and item[4]})
                    if not addresses:
                        decision = (False, "dns_resolution_failed")
                    elif any(is_private_network_address(address) for address in addresses):
                        decision = (
                            True,
                            "private_network_host_allowlisted",
                        ) if private_network_allowed else (
                            False,
                            "resolved_private_ip",
                        )
                    else:
                        decision = (True, "allowlisted_host")
                    host_resolution_cache[host] = decision
                    return decision

                def redacted_har_url(url):
                    if url.startswith("data:"):
                        return "data:<redacted>"
                    if url.startswith("blob:"):
                        return "blob:<redacted>"
                    if not has_credential_url_parts(url):
                        return url
                    parsed = urlparse(url)
                    scheme = parsed.scheme.lower()
                    host = (parsed.hostname or "").lower()
                    if scheme not in ("http", "https") or not host:
                        return "<redacted-url>"
                    try:
                        port = parsed.port
                    except ValueError:
                        port = None
                    authority = host if port is None else f"{host}:{port}"
                    redacted = f"{scheme}://{authority}{parsed.path or ''}"
                    if parsed.username or parsed.password:
                        redacted += "?<redacted-userinfo>"
                    if parsed.query:
                        redacted += ("&" if "?" in redacted else "?") + "<redacted-query>"
                    if parsed.fragment:
                        redacted += "#<redacted-fragment>"
                    return redacted

                def egress_decision(url):
                    if url.startswith(("about:", "blob:", "data:")):
                        return True, "internal_scheme"
                    if has_credential_url_parts(url):
                        return False, "credential_url"
                    parsed = urlparse(url)
                    scheme = parsed.scheme.lower()
                    host = (parsed.hostname or "").lower()
                    if scheme in ("http", "https") and host:
                        if host in allowed_hosts:
                            return resolved_host_decision(host)
                        return False, "host_not_allowlisted"
                    return False, "unsupported_url"

                def allowed_url(url):
                    allowed, _ = egress_decision(url)
                    return allowed

                def request_host(url):
                    parsed = urlparse(url)
                    scheme = parsed.scheme.lower()
                    host = (parsed.hostname or "").lower()
                    if scheme in ("http", "https") and host:
                        return host
                    return None

                def increment(counter, key):
                    safe_key = key or "unknown"
                    counter[safe_key] = counter.get(safe_key, 0) + 1

                def blocked_navigation_reason(events):
                    for event in events:
                        if event.get("blocked"):
                            return event.get("blockedReason") or "blocked"
                    return None

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
                            "_blockedReason": event.get("blockedReason"),
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

                def build_egress_summary(events):
                    allowed_count = 0
                    blocked_count = 0
                    allowed_host_counts = {}
                    resource_type_counts = {}
                    blocked_resource_type_counts = {}
                    blocked_reason_counts = {}
                    for event in events:
                        resource_type = event.get("resourceType") or "unknown"
                        increment(resource_type_counts, resource_type)
                        if event.get("blocked"):
                            blocked_count += 1
                            increment(blocked_resource_type_counts, resource_type)
                            increment(blocked_reason_counts, event.get("blockedReason"))
                        else:
                            allowed_count += 1
                            host = event.get("host")
                            if host in allowed_hosts:
                                increment(allowed_host_counts, host)
                    return {
                        "mode": "url" if target_url else "html",
                        "networkRequested": bool(target_url),
                        "policy": "ALLOWLISTED" if target_url else "DENY_ALL",
                        "allowedHostCount": len(allowed_hosts),
                        "requestCount": len(events),
                        "continuedRequestCount": allowed_count,
                        "blockedRequestCount": blocked_count,
                        "blockedReasonCounts": blocked_reason_counts,
                        "resourceTypeCounts": resource_type_counts,
                        "blockedResourceTypeCounts": blocked_resource_type_counts,
                        "allowedHostRequestCounts": [
                            {"host": host, "requestCount": count}
                            for host, count in sorted(allowed_host_counts.items())
                        ],
                        "proxy": {
                            "enabled": bool(target_url and browser_proxy_server),
                            "authenticated": bool(target_url and browser_proxy_server and browser_proxy_username and browser_proxy_password),
                            "poolSize": browser_proxy_pool_size,
                            "rotationEnabled": bool(browser_proxy_pool_size > 1),
                        },
                    }

                def build_session_summary(state, current_url):
                    cookies = state.get("cookies") or []
                    origins = state.get("origins") or []
                    return {
                        "source": "url" if target_url else "html",
                        "targetUrl": target_url or None,
                        "url": redacted_har_url(current_url),
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
                        if target_url and browser_proxy_server:
                            proxy_options = {"server": browser_proxy_server}
                            if browser_proxy_username and browser_proxy_password:
                                proxy_options["username"] = browser_proxy_username
                                proxy_options["password"] = browser_proxy_password
                            context_options["proxy"] = proxy_options
                        if video_enabled:
                            video_dir.mkdir(parents=True, exist_ok=True)
                            context_options["record_video_dir"] = str(video_dir)
                            context_options["record_video_size"] = {"width": viewport_width, "height": viewport_height}
                        context = browser.new_context(**context_options)
                        if browser_cookies:
                            context.add_cookies(browser_cookies)
                        page = context.new_page()

                        def on_request(request):
                            allowed, decision_reason = egress_decision(request.url)
                            blocked = not allowed
                            event = {
                                "startedDateTime": utc_now(),
                                "method": request.method,
                                "url": redacted_har_url(request.url),
                                "host": request_host(request.url) if allowed else None,
                                "resourceType": request.resource_type,
                                "status": 0,
                                "statusText": "",
                                "failure": None,
                                "blocked": blocked,
                                "blockedReason": None if allowed else decision_reason,
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
                            try:
                                page.goto(target_url, wait_until="load", timeout=10000)
                            except Exception:
                                blocked_reason = blocked_navigation_reason(network_events)
                                if blocked_reason:
                                    blocked_count = sum(1 for event in network_events if event.get("blocked"))
                                    raise RuntimeError(
                                        f"browser navigation blocked by egress policy; blockedReason={blocked_reason}; egressRequests={len(network_events)}; egressBlocked={blocked_count}"
                                    ) from None
                                raise RuntimeError("browser navigation failed") from None
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
                            if session_state_path.stat().st_size > max_session_state_bytes:
                                session_state_path.unlink(missing_ok=True)
                                session_summary_path.unlink(missing_ok=True)
                                raise RuntimeError("browser session state capture exceeds storage budget")
                            session_summary_path.write_text(
                                json.dumps(build_session_summary(state, page.url), ensure_ascii=False, indent=2),
                                encoding="utf-8",
                            )
                            session_summary_file = session_summary_path.name
                            session_state_file = session_state_path.name
                        egress_summary = build_egress_summary(network_events)
                        result = {
                            "action": action,
                            "source": "url" if target_url else "html",
                            "title": title,
                            "url": redacted_har_url(page.url),
                            "targetUrl": target_url or None,
                            "allowedHosts": sorted(allowed_hosts),
                            "proxy": {
                                "enabled": bool(target_url and browser_proxy_server),
                                "authenticated": bool(target_url and browser_proxy_server and browser_proxy_username and browser_proxy_password),
                                "poolSize": browser_proxy_pool_size,
                                "rotationEnabled": bool(browser_proxy_pool_size > 1),
                            },
                            "egress": egress_summary,
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

                print(f"browser {action} completed; textLength={len(body_text)}; screenshot={screenshot_enabled}; har={har_enabled}; video={video_enabled}; cookies={len(browser_cookies)}; sessionStateReplay={browser_session_state is not None}; sessionStateCapture={capture_session_state}; egressRequests={egress_summary['requestCount']}; egressContinued={egress_summary['continuedRequestCount']}; egressBlocked={egress_summary['blockedRequestCount']}; proxyEnabled={egress_summary['proxy']['enabled']}; proxyAuthenticated={egress_summary['proxy']['authenticated']}; proxyPoolSize={egress_summary['proxy']['poolSize']}; proxyRotation={egress_summary['proxy']['rotationEnabled']}")
                """.formatted(
                request.action(),
                jsonForScript(request.url()),
                jsonForScript(browserProxyServer),
                jsonForScript(proxyCredentials.username()),
                jsonForScript(proxyCredentials.password()),
                browserProxyServers.size(),
                jsonForScript(request.allowedHosts()),
                jsonForScript(request.browserPrivateNetworkAllowedHosts()),
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
                browserSessionSummaryName(),
                MAX_BROWSER_SESSION_STATE_CHARS);
    }
    static String normalizedBrowserAction(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
    boolean isSupportedBrowserAction(String action) {
        return BROWSER_ACTION_SNAPSHOT.equals(action) || BROWSER_ACTION_EXTRACT_TEXT.equals(action);
    }
    String browserInputName() {
        return "browser-input.html";
    }
    String browserCookiesName() {
        return "browser-cookies.json";
    }
    String browserSessionStateInputName() {
        return "browser-session-state-input.json";
    }
    String browserResultName() {
        return "browser-result.json";
    }
    String browserScreenshotName() {
        return "screenshot.png";
    }
    String browserHarName() {
        return "browser-network.har";
    }
    String browserVideoName() {
        return "browser-video.webm";
    }
    String browserSessionStateName() {
        return "browser-session-state.json";
    }
    String browserSessionSummaryName() {
        return "browser-session-summary.json";
    }
    int boundedInt(JsonNode root, String name, int defaultValue, int min, int max) {
        int parsed = defaultValue;
        JsonNode value = root.path(name);
        if (value.isNumber()) {
            parsed = value.asInt(defaultValue);
        } else if (value.isTextual() && ContainerSandboxTextSupport.hasText(value.asText())) {
            try {
                parsed = Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException ignored) {
                parsed = defaultValue;
            }
        }
        return Math.max(min, Math.min(max, parsed));
    }
    String normalizedBrowserUrl(String value) {
        if (!ContainerSandboxTextSupport.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_BROWSER_URL_CHARS) {
            throw new IllegalArgumentException("browser automation url exceeds " + MAX_BROWSER_URL_CHARS + " chars");
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || !ContainerSandboxTextSupport.hasText(uri.getHost())) {
                throw new IllegalArgumentException("browser automation url must be an HTTP/HTTPS URL with a host");
            }
            if (ContainerSandboxTextSupport.hasText(uri.getUserInfo())) {
                throw new IllegalArgumentException("browser automation url must not include userinfo credentials");
            }
            if (ContainerSandboxTextSupport.hasText(uri.getRawFragment())) {
                throw new IllegalArgumentException("browser automation url must not include fragment identifiers");
            }
            validateBrowserUrlQuery(uri);
            return uri.normalize().toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browser automation url is not valid", ex);
        }
    }
    void validateBrowserUrlQuery(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (!ContainerSandboxTextSupport.hasText(rawQuery)) {
            return;
        }
        if (rawQuery.length() > MAX_BROWSER_URL_QUERY_CHARS) {
            throw new IllegalArgumentException(
                    "browser automation url query exceeds " + MAX_BROWSER_URL_QUERY_CHARS + " chars");
        }
        for (String parameter : rawQuery.split("[&;]")) {
            if (!ContainerSandboxTextSupport.hasText(parameter)) {
                continue;
            }
            String rawName = parameter.split("=", 2)[0];
            String normalizedName = normalizedBrowserQueryParameterName(rawName);
            if (SENSITIVE_BROWSER_QUERY_PARAMETER_NAMES.contains(normalizedName)) {
                throw new IllegalArgumentException(
                        "browser automation url query must not include credential parameters");
            }
        }
    }
    String normalizedBrowserQueryParameterName(String value) {
        String decodedName = decodedBrowserQueryParameterName(value).toLowerCase(Locale.ROOT);
        int bracketIndex = decodedName.indexOf('[');
        if (bracketIndex > 0) {
            decodedName = decodedName.substring(0, bracketIndex);
        }
        return decodedName.replaceAll("[^a-z0-9]", "");
    }
    String decodedBrowserQueryParameterName(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }
    List<String> normalizedBrowserAllowedHosts(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        if (value.isArray()) {
            value.forEach(item -> networkBoundarySupport.addNormalizedBrowserHost(hosts, item.asText("")));
        } else if (value.isTextual()) {
            for (String item : value.asText("").split(",")) {
                networkBoundarySupport.addNormalizedBrowserHost(hosts, item);
            }
        } else {
            networkBoundarySupport.addNormalizedBrowserHost(hosts, value.asText(""));
        }
        if (hosts.size() > MAX_BROWSER_ALLOWED_HOSTS) {
            throw new IllegalArgumentException(
                    "browser automation allowedHosts exceeds " + MAX_BROWSER_ALLOWED_HOSTS + " hosts");
        }
        return new ArrayList<>(hosts);
    }
    List<String> normalizedBrowserPrivateNetworkAllowedHosts() {
        String value = ContainerSandboxTextSupport.trimToNull(properties.getBrowserPrivateNetworkAllowedHosts());
        if (value == null) {
            return List.of();
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        for (String item : value.split(",")) {
            networkBoundarySupport.addNormalizedBrowserHost(hosts, item, "browserPrivateNetworkAllowedHosts");
        }
        if (hosts.size() > MAX_BROWSER_ALLOWED_HOSTS) {
            throw new IllegalArgumentException(
                    "browserPrivateNetworkAllowedHosts must contain at most "
                            + MAX_BROWSER_ALLOWED_HOSTS
                            + " hosts");
        }
        return new ArrayList<>(hosts);
    }
    List<String> effectiveBrowserPrivateNetworkAllowedHosts(List<String> runtimeRequestHosts) {
        if (runtimeRequestHosts == null) {
            return normalizedBrowserPrivateNetworkAllowedHosts();
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        for (String item : runtimeRequestHosts) {
            networkBoundarySupport.addNormalizedBrowserHost(hosts, item, "browserPrivateNetworkAllowedHosts");
        }
        if (hosts.size() > MAX_BROWSER_ALLOWED_HOSTS) {
            throw new IllegalArgumentException(
                    "browserPrivateNetworkAllowedHosts must contain at most "
                            + MAX_BROWSER_ALLOWED_HOSTS
                            + " hosts");
        }
        return new ArrayList<>(hosts);
    }
    String normalizedBrowserSessionState(JsonNode value,
                                                 List<String> allowedHosts,
                                                 String urlHost,
                                                 String urlOrigin,
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
        validateKnownBrowserSessionStateKeys(state, BROWSER_SESSION_STATE_KEYS, "sessionState");
        validateBrowserSessionStateCookies(state.get("cookies"), allowedHosts, urlHost);
        validateBrowserSessionStateOrigins(state.get("origins"), allowedHosts, urlHost, urlOrigin);
        String serialized = objectMapper.writeValueAsString(state);
        if (serialized.length() > MAX_BROWSER_SESSION_STATE_CHARS) {
            throw new IllegalArgumentException("browser automation sessionState exceeds "
                    + MAX_BROWSER_SESSION_STATE_CHARS + " chars");
        }
        return serialized;
    }
    void validateBrowserSessionStateCookies(JsonNode value, List<String> allowedHosts, String urlHost) {
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
            validateKnownBrowserSessionStateKeys(cookieNode, BROWSER_SESSION_STATE_COOKIE_KEYS, "sessionState cookie");
            normalizedBrowserCookieName(cookieNode.path("name").asText(""));
            normalizedBrowserCookieValue(cookieNode.path("value").asText(""));
            String domain = normalizedBrowserCookieDomain(cookieNode.path("domain").asText(""));
            String domainHost = networkBoundarySupport.browserCookieDomainHost(domain);
            if (!allowedHosts.contains(domainHost)) {
                throw new IllegalArgumentException(
                        "browser automation sessionState cookie domain must be included in allowedHosts");
            }
            if (!domainHost.equals(urlHost)) {
                throw new IllegalArgumentException(
                        "browser automation sessionState cookie domain must match the target URL host");
            }
            normalizedBrowserCookiePath(cookieNode.path("path").asText("/"));
        }
    }
    void validateBrowserSessionStateOrigins(JsonNode value,
                                                    List<String> allowedHosts,
                                                    String urlHost,
                                                    String urlOrigin) {
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
            validateKnownBrowserSessionStateKeys(originNode, BROWSER_SESSION_STATE_ORIGIN_KEYS, "sessionState origin");
            String originValue = originNode.path("origin").asText("");
            String host = networkBoundarySupport.browserSessionStateOriginHost(originValue);
            if (!allowedHosts.contains(host)) {
                throw new IllegalArgumentException(
                        "browser automation sessionState origin host must be included in allowedHosts");
            }
            if (!host.equals(urlHost)) {
                throw new IllegalArgumentException(
                        "browser automation sessionState origin host must match the target URL host");
            }
            if (!networkBoundarySupport.browserUrlOrigin(originValue, "sessionState origin").equals(urlOrigin)) {
                throw new IllegalArgumentException(
                        "browser automation sessionState origin must match the target URL origin");
            }
            validateBrowserSessionStateLocalStorage(originNode.get("localStorage"));
        }
    }
    void validateBrowserSessionStateLocalStorage(JsonNode value) {
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
            validateKnownBrowserSessionStateKeys(
                    item,
                    BROWSER_SESSION_STATE_LOCAL_STORAGE_KEYS,
                    "sessionState localStorage item");
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
    String boundedBrowserSessionStateText(String value, String label, int maxChars, boolean required) {
        String text = value == null ? "" : value;
        if ((required && !ContainerSandboxTextSupport.hasText(text)) || text.length() > maxChars || containsControlCharacter(text)) {
            throw new IllegalArgumentException("browser automation " + label + " is invalid");
        }
        return text;
    }
    void validateKnownBrowserSessionStateKeys(JsonNode value, Set<String> allowedKeys, String label) {
        value.fieldNames().forEachRemaining(key -> {
            if (!allowedKeys.contains(key)) {
                throw new IllegalArgumentException("browser automation " + label + " contains unsupported fields");
            }
        });
    }
    List<BrowserCookie> normalizedBrowserCookies(JsonNode value,
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
        if (!ContainerSandboxTextSupport.hasText(urlHost)) {
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
            if (!domain.equals(urlHost)) {
                throw new IllegalArgumentException("browser automation cookie domain must match the target URL host");
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
    String normalizedBrowserCookieName(String value) {
        String name = value == null ? "" : value.trim();
        if (!ContainerSandboxTextSupport.hasText(name)) {
            throw new IllegalArgumentException("browser automation cookie name is required");
        }
        if (name.length() > MAX_BROWSER_COOKIE_NAME_CHARS
                || name.matches(".*[\\s;,=].*")
                || containsControlCharacter(name)) {
            throw new IllegalArgumentException("browser automation cookie name is invalid");
        }
        return name;
    }
    String normalizedBrowserCookieValue(String value) {
        String cookieValue = value == null ? "" : value;
        if (cookieValue.length() > MAX_BROWSER_COOKIE_VALUE_CHARS || containsControlCharacter(cookieValue)) {
            throw new IllegalArgumentException("browser automation cookie value is invalid");
        }
        return cookieValue;
    }
    String normalizedBrowserCookieDomain(String value) {
        String domain = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!ContainerSandboxTextSupport.hasText(domain)
                || domain.startsWith(".")
                || domain.contains("/")
                || domain.contains(":")
                || !domain.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("browser automation cookie domain must be a host name only");
        }
        networkBoundarySupport.validatePublicBrowserHost(domain, "cookie domain");
        return domain;
    }
    String normalizedBrowserCookiePath(String value) {
        String path = ContainerSandboxTextSupport.hasText(value) ? value.trim() : "/";
        if (!path.startsWith("/") || containsControlCharacter(path)) {
            throw new IllegalArgumentException("browser automation cookie path must start with /");
        }
        return path;
    }
    String normalizedBrowserCookieSameSite(String value) {
        if (!ContainerSandboxTextSupport.hasText(value)) {
            return "Lax";
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
    }
    boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(ch -> ch < 0x20 || ch == 0x7f);
    }
    String jsonForScript(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalArgumentException("browser automation script input could not be serialized", ex);
        }
    }
    List<String> browserProxyHosts() {
        List<String> proxyServers = normalizedBrowserProxyServers();
        if (proxyServers.isEmpty()) {
            return List.of();
        }
        return proxyServers.stream()
                .map(proxyServer -> URI.create(proxyServer).getHost().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }
    String selectBrowserProxyServer(List<String> proxyServers) {
        if (proxyServers.isEmpty()) {
            return "";
        }
        if (proxyServers.size() == 1) {
            return proxyServers.get(0);
        }
        int index = Math.floorMod(browserProxyCursor.getAndIncrement(), proxyServers.size());
        return proxyServers.get(index);
    }
    List<String> normalizedBrowserProxyServers() {
        String singleProxyServer = ContainerSandboxTextSupport.trimToNull(properties.getBrowserProxyServer());
        String proxyServerList = ContainerSandboxTextSupport.trimToNull(properties.getBrowserProxyServers());
        if (singleProxyServer != null && proxyServerList != null) {
            throw new IllegalArgumentException(
                    "browserProxyServer and browserProxyServers must not be configured together");
        }
        if (proxyServerList == null) {
            String normalized = normalizeBrowserProxyServer(singleProxyServer);
            return ContainerSandboxTextSupport.hasText(normalized) ? List.of(normalized) : List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String candidate : proxyServerList.split(",")) {
            String proxyServer = normalizeBrowserProxyServer(candidate);
            if (ContainerSandboxTextSupport.hasText(proxyServer)) {
                normalized.add(proxyServer);
            }
            if (normalized.size() > MAX_BROWSER_PROXY_SERVERS) {
                throw new IllegalArgumentException(
                        "browserProxyServers must contain at most " + MAX_BROWSER_PROXY_SERVERS + " entries");
            }
        }
        return List.copyOf(normalized);
    }
    String normalizeBrowserProxyServer(String value) {
        String trimmed = ContainerSandboxTextSupport.trimToNull(value);
        if (trimmed == null) {
            return "";
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = ContainerSandboxTextSupport.nullToEmpty(uri.getScheme()).toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (!Set.of("http", "https").contains(scheme)
                    || !ContainerSandboxTextSupport.hasText(host)
                    || ContainerSandboxTextSupport.hasText(uri.getUserInfo())
                    || ContainerSandboxTextSupport.hasText(uri.getQuery())
                    || ContainerSandboxTextSupport.hasText(uri.getFragment())
                    || (ContainerSandboxTextSupport.hasText(uri.getPath()) && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("browserProxyServer must be an HTTP/HTTPS origin without credentials");
            }
            return new URI(
                    scheme,
                    null,
                    host.toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    null,
                    null,
                    null).toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("browserProxyServer must be a valid HTTP/HTTPS origin", ex);
        }
    }
    BrowserProxyCredentials browserProxyCredentials(List<String> proxyServers) {
        String username = ContainerSandboxTextSupport.trimToNull(properties.getBrowserProxyUsername());
        String password = ContainerSandboxTextSupport.hasText(properties.getBrowserProxyPassword())
                ? properties.getBrowserProxyPassword()
                : null;
        if (username == null && password == null) {
            return BrowserProxyCredentials.none();
        }
        if (username == null || password == null) {
            throw new IllegalArgumentException(
                    "browserProxyUsername and browserProxyPassword must be configured together");
        }
        if (proxyServers.isEmpty()) {
            throw new IllegalArgumentException(
                    "browserProxyUsername/browserProxyPassword require browserProxyServer or browserProxyServers");
        }
        return new BrowserProxyCredentials(username, password);
    }
}
