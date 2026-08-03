# Initial Baseline Snapshot

Date: 2026-06-27

## Project Structure

- Backend is a Maven multi-module Java 21 project.
- Kernel domain and application code live under `seahorse-agent-kernel`.
- HTTP controllers live under `seahorse-agent-adapter-web`.
- JDBC persistence adapters live under `seahorse-agent-adapter-repository-jdbc`.
- Spring Boot wiring lives under `seahorse-agent-spring-boot-autoconfigure`.
- Frontend is a Vite React application under `frontend`.

## Tech Stack

- Java 21, Spring Boot 3.5.x, Maven.
- React 18, TypeScript, Zustand, Vitest, Tailwind-style utility classes.
- JDBC repository adapters for memory governance records.
- SSE streaming through `StreamCallback` and frontend `useStreamResponse`.

## Ownership Mapping

- Chat orchestration: `KernelChatPipeline`, `KernelChatPreparationSupport`, `KernelChatResponseSupport`.
- Chat preparation dependency bundle: `ChatPreparationPorts`.
- Memory governance and conflict records: `MemoryConflictLogRepositoryPort`, `MemoryManagementInboundPort`, `KernelMemoryManagementService`.
- Memory HTTP API: `SeahorseMemoryController`.
- Chat UI state: `frontend/src/stores/chatStore.ts`.
- Chat message rendering: `frontend/src/components/chat/MessageItem.tsx`.
- Memory governance frontend API: `frontend/src/services/memoryGovernanceService.ts`.

## Contract Inventory

- `StreamCallback.onEvent(eventName, payload)` supports arbitrary SSE events.
- Frontend `useStreamResponse` dispatches custom SSE events to `onEvent`.
- Existing memory conflict API:
  - `GET /memories/conflicts`
  - `POST /memories/conflicts/{conflictId}/resolve`
- Existing conflict record table is represented by `MemoryConflictRecord`.

## Dependency Direction

- Kernel application depends on ports and domain objects.
- Adapters implement or expose ports.
- Autoconfigure wires adapters and kernel services.
- Frontend consumes HTTP and SSE contracts without importing backend code.

## Test System

- Backend unit and contract tests use JUnit 5, Spring MockMvc, Mockito.
- Frontend tests use Vitest and Testing Library.
- Existing E2E scripts cover broader Docker and browser smoke paths.

## Known Anti-Patterns To Avoid

- Do not duplicate memory conflict state in frontend-only stores without backend source of truth.
- Do not mutate memory records implicitly during chat until action semantics and rollback are tested.
- Do not add new database columns for cooldown/attempt tracking before the first interactive loop proves useful.
- Do not block chat generation when conflict lookup fails.

## Compatibility Boundaries

- Existing admin memory governance APIs must keep working.
- Existing chat streaming events must remain compatible.
- Absence of a memory conflict repository must degrade to no prompts.
- The first phase may mark conflicts resolved, but must not silently alter memory content.


