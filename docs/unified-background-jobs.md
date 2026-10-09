# Unified Background Jobs

## Status

- Branch: `feature/unified-background-jobs`
- Implementation status: core migration complete; dormant legacy service methods retained for source compatibility
- Scope: durable background-job control plane, scan eligibility, per-account visibility and control, restart recovery, deduplication, retry, and deleted-target handling.

This document is the source of truth for the migration. Update the implementation checklist and decisions whenever behavior changes.

## Goals

1. Every user-visible background operation has a durable job record.
2. A normal user can list, pause, resume, cancel, and retry only jobs owned by that account.
3. A super administrator can inspect and control all jobs, one account, or the whole platform.
4. Manual post-processing accepts only photos whose scan pipeline is complete; unscanned photos are not queued.
5. A process restart may repeat the currently claimed item, but must not lose the job or repeat completed items.
6. One broken item does not stop a batch. Provider-wide failures such as exhausted tokens block the affected lane instead of failing every remaining item.
7. Duplicate submissions reuse completed or active work unless the caller explicitly requests a forced rerun.

## Model

### BackgroundJob

A job represents one user request or one automatic operation.

Required fields:

- `id`, `type`, `resourceLane`, `status`
- `ownerUserId`: whose data is processed
- `requestedByUserId`: who created the request
- `priority`, `forceReprocess`, `pipelineVersion`
- progress counters and timestamps
- pause/cancel flags, blocking reason, failure group, and error summary
- optional source job and scope metadata

### BackgroundJobItem

An item is the independently recoverable unit, normally one photo. Directory discovery jobs may use a path item until a photo record exists.

The stable deduplication key is conceptually:

```text
(ownerUserId, targetType, targetId, stage, pipelineVersion, parametersHash)
```

Forced processing creates a new attempt. Normal submission skips a successful current-version item and attaches to active equivalent work. Parameters are durable and inherited by retries. For example, `preserveBindings=true` and `preserveBindings=false` are distinct face-rebuild operations.

Each job stores a canonical JSON parameter object and its SHA-256 hash. Handlers read options from the persisted job, never transient request state. For face rebuild, `preserveBindings=true` reuses matching prior face/person bindings where possible; `false` rebuilds detections without carrying those bindings forward. Future batch operations must add operation-specific options to this object and cover them in handler and deduplication tests.

Face rebuild is a forced, per-photo batch operation even when initiated across all accounts. The requester's chosen `preserveBindings` value is stored on each owner-specific job, shown in job details, and reused unchanged on retries. Missing source files fail their items rather than being recorded as successful rebuilds. "Complete rebuild" replaces face detections and their bindings for the selected photos; it does not delete the person catalog or reset unrelated photo metadata.

For the face-recognition model, `forceRebuild=true` with `preserveBindings=true` recomputes embeddings on existing face records without changing person bindings; `preserveBindings=false` redetects faces and discards prior bindings (detection also computes new embeddings). The latter only applies when force rebuilding existing faces. When `includeMissingItems=true`, photos without faces are detected first in either mode.

Single-photo rebuild entries use a shared cancellable mode dialog. The super-admin API tool exposes the same mode for `/admin/faces/rebuild-all`. Parameter persistence/deduplication and handler option forwarding/error handling are covered by `BackgroundJobServiceTest`; frontend production build and whitespace checks pass. The full migration is still in progress as described in the remaining scope below.

### Attempts and events

Attempts preserve retry history. Events record control actions and important transitions. Initial implementation may store attempt counts and recent error data on the item while retaining old terminal jobs; a separate attempt/event table is added before migrating destructive or externally billed operations.

## States

Jobs and items use the same canonical vocabulary where applicable:

```text
QUEUED
WAITING_DEPENDENCY
RUNNING
PAUSED
BLOCKED
SUCCEEDED
PARTIAL_SUCCESS
FAILED
SKIPPED
CANCELED
```

`RUNNING` records found during startup are returned to `QUEUED`. Work is claimed in small recoverable batches. The current batch may run twice after abrupt power loss, so every handler must be idempotent.

## Scan eligibility

Manual post-processing jobs only include live photo records whose scan pipeline is already `COMPLETED`. Unscanned photos are reported as not queued; they receive configured default processing as their scan pipeline advances. This avoids maintaining a waiting queue for the only current prerequisite.
The dispatcher rechecks ownership and scan completion before executing each queued photo item; a deleted, transferred, or newly unscanned target is skipped rather than processed with stale eligibility.

Album atmosphere rebuild is an album-metadata operation: its implementation reads album name/tags, not photo colors or scan results. It can therefore run before photos complete scanning. If the analyzer begins consuming photo-derived data, this exception must be removed and a scan gate added before dispatch.

Deleting a photo after submission changes its pending item to `SKIPPED` with `TARGET_DELETED`. A missing source for a live record is `FAILED` with `SOURCE_MISSING` and remains retryable after storage recovery.

## Scan pipeline

Initial ingestion remains one top-level scan job with observable child stages:

```text
BASIC_INFO -> EXIF -> THUMBNAILS -> COLOR_QUALITY -> FACES
           -> SUBJECT -> TAGS -> AI_SCORING -> BACKGROUND_REMOVAL
```

These stages are not separate top-level jobs during initial ingestion. Maintenance operations such as rebuilding all faces or recalculating all colors are independent jobs that reuse the same stage handlers.

Optional or billed processing, especially remote visual analysis, is a downstream job. Automatic creation is controlled by configuration and remains visible to the owner.

Scene recognition, emotion analysis, and image classification keep remote-AI and local results separate. Completed remote visual analysis is preferred for execution and display. Local ONNX/rule results remain available as an offline fallback and are not overwritten by an AI retry. Scene and emotion are no longer presented as downloadable local models; their disabled local services are compatibility fallbacks only. Image classification remains a managed local model because it is used when remote AI is disabled, unavailable, or has not produced usable `visualTags`.

## Scheduling

The control plane is shared; execution capacity is separated by resource lane:

- `SCAN_IO`
- `LOCAL_CPU_AI`
- `LOCAL_GPU_AI`
- `REMOTE_AI`
- `MAINTENANCE`
- `SYSTEM`

Within a lane, dispatch selects the highest effective priority and then rotates across owners. A claim is limited by item count or time budget, after which unfinished work returns to the owner's queue. Priority aging prevents permanent starvation.

Single-user mode uses the same algorithm and naturally becomes priority FIFO.

## Pause and recovery

Pause scopes:

- job
- owner account
- resource lane
- whole platform

Pausing prevents new claims immediately. Running handlers stop at the next item/stage checkpoint. A single model invocation or file write is allowed to finish. Output is written to a temporary file and atomically moved before database success is committed.

The persisted control state survives restart. Global pause must not be represented only by an in-memory flag.

## Errors and retry

Errors have stable codes and a `retryable` flag. Examples:

- `TARGET_DELETED`: skipped, not retryable by default
- `SOURCE_MISSING`: retryable after storage recovery
- `TRANSIENT_NETWORK`: automatic bounded retry with backoff
- `QUOTA_EXHAUSTED`: block the remote lane and remaining matching items
- `MODEL_UNAVAILABLE`: block the relevant local-AI lane
- `UNSUPPORTED_FORMAT`: permanent item failure

A provider-wide failure creates a `failureGroupId`. Recovery retries only unresolved items in that group by default. Historical failures are excluded unless explicitly selected. A retry creates a new job/attempt and never erases the original result.

The super-admin default bulk retry picks the most recent unresolved failure group **per owner and job type** across the full history (not only the newest 200 displayed jobs). Older failures require explicit selection. This prevents a recent failure in one account from hiding another account's latest recoverable failure.

## API shape

```text
GET  /api/admin/background-jobs
GET  /api/admin/background-jobs/{id}
POST /api/admin/background-jobs/{id}/pause
POST /api/admin/background-jobs/{id}/resume
POST /api/admin/background-jobs/{id}/cancel
POST /api/admin/background-jobs/{id}/retry

POST /api/admin/background-jobs/control/pause-all       (super admin)
POST /api/admin/background-jobs/control/resume-all      (super admin)
POST /api/admin/background-jobs/control/users/{id}/pause
POST /api/admin/background-jobs/control/users/{id}/resume
```

All list/detail/control operations enforce `ownerUserId`, except super administrators who may view and control every account.

## Migration map

| Existing mechanism | Target |
|---|---|
| `scan_task` | Adapter first; migrate to unified job after dispatcher compatibility is proven |
| `photo_visual_analysis_job` | Legacy unfinished items migrate to unified remote lane; old worker removed, old records remain readable |
| `PhotoScanService.tasks` | Migrate face/color/EXIF/photo-time/background maintenance operations |
| `BackgroundRemovalService` model calls | Unified `BACKGROUND_REMOVAL` photo items; scan-time calls remain inside the scan pipeline |
| `ModelManagementService` rebuild executor | Per-owner, per-photo unified model-rebuild jobs (migrated) |
| raw AI-scoring threads | Remove and replace with durable jobs |

## Implementation checklist

- [x] Create feature branch and architecture record.
- [x] Add durable job, item, and control-state entities/repositories.
- [x] Add ownership-aware query and control API.
- [x] Add restart recovery and scan eligibility checks.
- [x] Add fair owner rotation and bounded claims.
- [x] Add idempotent deduplication and deleted-target handling.
- [x] Add failure classification, failure groups, and batch retry.
- [x] Integrate visual-analysis jobs through the unified control plane.
- [x] Migrate unfinished legacy visual-analysis items and remove their independent worker.
- [x] Integrate scan visibility/control without breaking checkpoints.
- [x] Preserve the last completed scan path on pause; yield to another same-priority account after approximately 20 paths; check persisted global, owner and `SCAN_IO` pause before claiming and at scan checkpoints.
- [x] Move hash backfill and background-cache cleanup entry points to per-photo maintenance jobs.
- [x] Move album-atmosphere rebuild endpoints to owner-scoped album items with deletion checks and retry history.
- [x] Move model-management rebuild off its in-memory executor into per-owner photo jobs; persist all rebuild options and expose durable model history.
- [x] Migrate all user-triggerable maintenance/background entry points to durable jobs; retain unreferenced legacy service methods only for source compatibility.
- [x] Add account and super-admin task views.
- [x] Run targeted backend tests and frontend production build; repair regressions.

Validation (2026-09-28): `mvn -DskipTests compile`, `mvn -Dtest=BackgroundJobServiceTest,AdminControllerTest test`, `npx vite build`, and `git diff --check` pass. The targeted suite covers album retry/deletion, target type, scan-state revalidation, and quota blocking. The six failures and two errors found by that day's full run were subsequently resolved on 2026-09-30; see the final verification below.

Additional targeted verification (2026-09-28): `mvn -q -Dtest=ScanTaskServiceTest,BackgroundJobServiceTest,SuperAdminControllerTest test`, `npx vite build`, and `git diff --check` pass. The scan suite includes a direct persisted global-pause claim/resume check; the controller suite covers explicit `preserveBindings=false` and default `true` on the legacy model-rebuild endpoint.

Model migration validation (2026-09-28): `mvn -q -Dtest=ModelManagementServiceTest,BackgroundJobServiceTest,SuperAdminControllerTest test`, `mvn -q -DskipTests compile`, and `npx vite build` pass. Tests verify account splitting, scan eligibility, parameter forwarding to the single-photo handler, and durable task lookup by ID.

Face-rebuild option verification (2026-09-28): `mvn -q -Dtest=ModelManagementServiceTest,BackgroundJobServiceTest test` passes (16 tests). Coverage includes full face-recognition rebuild without bindings and embedding-only rebuild with bindings preserved. Redetection avoids a duplicate embedding pass. `npx vite build` and `git diff --check` also pass. An intermediate test run used a stale class produced while sources changed during compilation; recompiling the final source resolved that mismatch.

Latest full `mvn -q test` (2026-09-28): 525 tests, six failures and two errors in the same previously failing suites (`FaceServiceTest`, `SmsSenderServiceTest`, `StorageProviderServiceTest`, `UserVipServiceTest`, `PhotoScanServiceTest`, `VipOrderAdminServiceTest`). The new model-rebuild tests pass. Spring Boot also started against the configured MySQL instance with `--spring.redis.host=disabled`; the temporary backend process was stopped after verifying it served HTTP on port 6060.

Completion verification (2026-09-30): the focused suite `BackgroundJobServiceTest,PhotoScanCompletionListenerTest,ScanTaskServiceTest,AdminControllerTest` passes. It covers scan eligibility, persisted options and deduplication, face-binding options, deleted or newly ineligible targets, quota blocking, fair owner rotation, pause/resume requeue, bounded network retry, scan-completion visual-analysis creation, and legacy task-route compatibility. A full `mvn -q test` ran 545 tests and reported the same six failures and two errors in the same unrelated historical suites listed above; no unified-job test failed. The project compiler source and target remain Java 11, and the new code avoids records, pattern-matching `instanceof`, and `Stream.toList()`.

The user-facing maintenance migration is complete: face, color, EXIF, photo-time, scoring, background-removal, visual-analysis, hash backfill, cache cleanup, album-atmosphere rebuild, model rebuild, and the legacy AI update-all endpoint now enter the unified queue. Scan records and legacy history remain readable. Unreferenced `PhotoScanService` asynchronous methods are retained only for source compatibility and have no controller, scheduler, startup, or in-repository service call path. The scheduled scan method is intentionally asynchronous only long enough to persist a `scan_task`; it never performs scan work on that executor. Hash backfill selects scanned photos missing a content hash; cache cleanup selects scanned photos with a stored cache path. Each cache item removes its file and only then clears its own path, leaving failed items retryable. Concurrent cache-clear requests deduplicate against active work, but not historical success, because a cache can be generated again. A `BLOCKED` task is never dispatched automatically; it requires an explicit retry after the provider/model issue is recovered.

Handlers returning result maps must raise returned errors so the item becomes `FAILED` instead of `SUCCEEDED`; this includes background removal, embedding rebuild, and photo-time rebuild. Direct on-demand background-removal requests now enter unified dispatch; only background removal performed within an active scan remains a scan substage. The legacy visual-analysis worker has been removed: startup and periodic migration compare original photo IDs with completed/skipped legacy items, move unfinished IDs into owner-scoped `VISUAL_ANALYSIS` jobs, and mark the old record `MIGRATED` only after enqueue succeeds. This recovers partially written legacy item tables. Already completed old items are not repeated; non-forced unified items avoid another provider call when analysis is already complete.

Each bounded dispatcher claim aggregates job progress using database counts per status, avoiding repeated hydration of every item in large batches. Startup recovery and explicit retry still visit item records; these are separate one-time operations.

Dispatcher follow-up verification (2026-09-28): `mvn -q -Dtest=BackgroundJobServiceTest test` passes (14 tests), covering returned handler errors and count-based progress; `git diff --check` passes. The earlier unrelated full-suite failures remain open.

Visual-analysis migration verification (2026-09-28): `mvn -q -Dtest=PhotoVisualAnalysisJobServiceTest,BackgroundJobServiceTest test` passes (16 tests; no errors or failures). Migration tests cover a partially written legacy item table; dispatch tests confirm non-forced analysis avoids an extra provider request for completed photos. Old records remain visible as migrated history, while unfinished work moves to the unified per-owner remote lane.

Model-management rebuild is now submitted as a separate `MODEL_REBUILD_<KEY>` job for each owner with eligible scanned photos. `includeMissingItems`, `forceRebuild` and `preserveBindings` are persisted together in each job and included in item deduplication identity. The per-photo handler reads those options on execution and retry. The model page obtains its recent history from durable jobs; its legacy task ID route resolves `model-job-<id>` across restarts. The submission response includes all owner jobs, counts unscanned/unowned photos, and retains the first task snapshot for the existing model-page polling UI. Model downloads and reloads are immediate administrative actions, not rebuild jobs. Legacy pre-migration in-memory task IDs cannot be recovered after restart.

## Compatibility decisions

- Existing scan and visual-analysis endpoints remain available during migration.
- Existing database records remain readable; migration does not retroactively retry historical failures.
- No user-edited face/person binding, manual tag, or moderation state may be overwritten by a retry unless the operation explicitly requests that behavior.
- Manual post-processing only queues scanned photos; scan-time follow-up remains in the scan pipeline and does not create a second queued job for each scan substage.
- Task-specific options are persisted and inherited by retries. An option change creates a distinct deduplication identity.

## On-demand background removal

The public photo-image GET and single-photo async POST enqueue `BACKGROUND_REMOVAL` under the photo owner's account after scan completion; an unscanned GET returns 202 without enqueuing, and the POST reports 409. The public GET keeps its existing anonymous trigger behavior, so deployment-level rate limits remain important. The request's `outputMaxSize` (480, 720, or 1080) is persisted in job parameters and retries retain it. Each size uses its own cache file (`bg_removed_<id>_<size>.png`), so one quality never masquerades as another. Repeated requests deduplicate against active work only, allowing a missing cache to be regenerated after an earlier success. Older generic jobs continue writing `bg_removed_<id>.png`.

The legacy paginated batch endpoints now submit owner-grouped persistent jobs instead of processing synchronously; callers must poll their returned job IDs, and `processed` reflects zero work at submission time. The unpaged admin batch endpoint already used the unified queue. The old in-memory background-removal executor and its misleading monitoring group have been removed; scan-internal background removal still runs inside the scan pipeline. The legacy batch-status endpoint now requires authentication, shows the current account's active jobs (all accounts for super-admin), and resolves numeric unified job IDs using the same ownership check as `/api/admin/background-jobs/{jobId}`. Pre-migration transient `bg-remove-*` IDs are no longer queryable through this endpoint.

Latest migration verification: clean `mvn -q clean -Dtest=PhotoControllerTest,BackgroundJobServiceTest,SuperAdminServiceTest test` passed; `npx vite build` passed; `git diff --check` passed. Controller tests cover the scan gate, owner/quality routing, authenticated status lookup, and rejection of transient legacy IDs; handler tests cover persisted quality and active-only deduplication. The known full-suite failures listed above remain outside this focused verification and still require separate repair.

## Real-model verification (2026-09-29)

- The deployed files successfully created ONNX sessions for face detection, face recognition, saliency detection, and BriaAI background removal. Face detection returned the expected Retina-style tensors (`bbox [1,16800,4]`, `confidence [1,16800,2]`, `landmark [1,16800,10]`).
- A real `FACE_RESCAN` job on photo 1 detected and stored one face and completed as `SUCCEEDED`, including face-embedding inference.
- `image_classification.onnx` is not currently present; only ImageNet label files are installed. Image classification therefore prefers completed remote visual analysis and otherwise falls back to local rules until that ONNX file is added.
- Remote and local analysis stay separate. The effective photo DTO prefers completed remote `scenes`, `moods`, and `visualTags`; photos 90 and 92 returned AI-sourced scene, emotion, and classification data from `gpt-5.4`, while local fields remained independently available.
- Historical `/data/photos/...` database paths are now resolved against the configured project photo root when the filesystem-root path does not exist. This repaired real background and face rebuild jobs that previously failed with `SOURCE_MISSING`. `UserPathServiceTest` covers the compatibility mapping.
- Background job 4 was interrupted during full-resolution post-processing, recovered after restart with attempt count 2, and completed as `SUCCEEDED`. Its output is a valid 720×480 RGBA PNG. Background removal now downsizes to the requested output dimensions before erosion and blur and no longer wraps the complete model invocation in a database transaction.
- A real single-photo AI scoring request completed successfully in 2.42 seconds. Final checks: targeted preference/path tests pass, backend compilation passes, frontend production build passes, and `git diff --check` passes.

## Remote-AI preference and source separation

Remote visual analysis and local inference are independent sources. They must never overwrite each other's raw fields. The effective value returned for display follows the same rule for scene, emotion, classification, and photography scoring: use a valid remote-AI value first and use the local value only when that remote field is absent or invalid.

Photography analysis requests four numeric scores from 0 to 100: `technicalScore`, `compositionScore`, `appealScore`, and `qualityScore`. Each component falls back independently. A malformed or missing AI composition score, for example, does not discard a valid AI technical score. Local saliency remains useful for focus maps, cropping, and the local composition-score fallback even when remote scoring exists.

The photo DTO exposes three layers for scoring:

- `visualAi*Score`: raw scores from the remote visual-analysis record.
- `local*Score`: raw local scoring record.
- existing `ai*Score`: backward-compatible effective display values, selected AI-first per field; `scoreSource` and the per-component source fields explain the selection.

For the effective overall value, a valid remote `qualityScore` wins. Otherwise it is the weighted result of the effective component scores (40% technical, 35% composition, 25% appeal). Old completed visual analyses without photography scores remain valid for their scene/tag/emotion data and simply fall back to local photography scores; they are not automatically re-billed or historically retried.

Real scoring verification (2026-09-29): the configured `gpt-5.4` visual model analyzed a deployed photo and returned technical 79, composition/saliency 86, appeal 89, and overall quality 84 together with evidence-based composition, lighting, color, focus, and improvement details. `PhotoAnalysisPreferenceServiceTest` (3 tests) and `PhotoServiceTest` (10 tests) pass, including a direct assertion that an AI composition score of 92 is displayed while the independently stored local score of 52 remains available. Backend compilation, frontend production build, and `git diff --check` pass.

## Dashboard task overview

Retry lifecycle: successful submission of a background-job retry changes the original task to terminal `RETRIED` (已重试), retaining its original counters and errors. The new task carries `sourceJobId` and independently succeeds or fails; retry a failed child rather than submitting the original again. Single and batch retry share this path. A pessimistic database row lock serializes requests for the same source, and repeated requests return the existing child instead of creating additional jobs. The frontend disables mutation buttons until submission and refresh complete. Startup repairs older FAILED/PARTIAL_SUCCESS/BLOCKED sources that already have a persisted retry child. Retried sources no longer count as exceptions or enter batch retries.

Retry verification (2026-09-30): 51 focused Java 11 tests and the frontend production build pass. The backend restarted successfully against MySQL and repaired existing retry-source states. Five parallel requests to already-retried source #2 all returned child #5; the combined record count remained 19 before and after, and the source remained RETRIED. No photo processing was resubmitted by this check.

All scan and background-job records now share one aligned, polling (5-second) table, without current/history tabs or task cards. The framed panel has a maximum height of 52rem; only the table body scrolls. Pagination is outside that scrolling area and offers 20, 50, or 100 rows per page.

`GET /api/admin/background-jobs/records` paginates the union in MySQL with deterministic creation/source/ID ordering. Its default `currentAccount=true` scopes the normal dashboard to the logged-in account even for a super-admin. The normal dashboard has no account selector or account column. The super-admin overview passes `currentAccount=false`, supports all-account, individual-account, and system filters, and shows an account column. Regular-account visibility remains enforced server-side.

Failed and partially successful background jobs, and failed scans, can be explicitly ignored. Ignore persists the terminal `IGNORED` status, displayed as 已忽略, without deleting original errors, counters, or item audit records. The operation is ownership-checked and idempotent; active work cannot be ignored. Ignored records remain in the table but are excluded from exception counts and batch retries and cannot be directly retried. Create a new task if processing is needed again.

Verification (2026-09-30): 49 focused Java 11 tests pass, including ignore ownership/idempotence/retry protection and all-record pagination without a history cutoff. Frontend production build passes. Existing historical records were not modified for verification.

Live browser verification: the normal overview loads 18 real records with no account selector/column or split tabs. The super-admin overview loads its account selector and account column. Adding 100 rendered rows leaves the panel at its 832px maximum with internal scrolling, while pagination stays outside the scrolling element. Desktop and 390px mobile screenshots confirm the table layout and mobile horizontal scrolling. Scan retry/pause/cancel controls follow the existing super-admin-only API permissions; regular accounts retain owner-level pause/resume and can ignore their own failed scans.

## Final Java 11 verification (2026-09-30)

- A clean build and `BackgroundJobServiceTest,PhotoScanCompletionListenerTest,ScanTaskServiceTest,AdminControllerTest` ran under OpenJDK 11.0.30: 44 tests passed, no failures or errors.
- The backend started under OpenJDK 11.0.30 against MySQL and listened on 6060; the BriaAI ONNX model loaded successfully. Development hot restart was disabled for this run to avoid inspecting a partially rewritten class directory during compilation.
- Direct backend and Vite-proxied anonymous task-list requests both return 401, not 500. Admin login and authenticated task listing return 200; four existing durable tasks were readable.
- Missing `Authorization` headers previously reached the catch-all exception handler and became 500. The handler now returns 401 for a missing authentication header and 400 for another missing required header; a controller regression test covers the task-list request.
- The earlier failures were audited rather than blindly changing production behavior. Real defects were fixed in scoped face counts, unsupported-SMS reporting, preferred storage selection, and scan capability for local paths outside the scan root. Tests were updated where the product contract had intentionally changed to independently stackable VIP capacity packages, live Alipay refund execution, and the current remote-scan handler signature.
- The final Java 11 full run passes: 546 tests, zero failures, zero errors. Frontend production build passed (255 modules), and `git diff --check` passed.

## Live abnormal-condition verification (2026-09-30)

These checks used the running Java 11 backend, real MySQL, HTTP requests, and Chrome, with two isolated `codex_e2e_*` accounts. Persisted fault fixtures contained missing photo IDs and nonexistent `/codex-e2e/` scan roots; no real photos were removed or changed. This is distinct from unit tests and from crashing an actual model inference.

- Authentication and account isolation: missing/invalid tokens return 401; cross-account records, details, ignore, and owner controls return 403; a regular account cannot pause all accounts. Chrome confirms the regular dashboard has no account selector and does not expose the second account's records.
- Eight simultaneous initial retries of the same failed background job create exactly one child; the source becomes RETRIED. Subsequent source retries return that child. Ignored records cannot be retried, active records cannot be ignored, and repeated ignore is idempotent.
- Real defects found and fixed: illegal task-state actions returned 500 rather than 409, another account's scan ignore returned 500 rather than 403, and malformed pagination/lane/JSON requests returned 500 rather than 400. Live requests were repeated after restarting the repaired backend.
- A nonexistent scan directory unnecessarily ran whole-database EXIF/filter maintenance and could remain RUNNING for over 150 seconds, then repeatedly recover/requeue. Invalid roots now skip that maintenance, and the worker independently persists FAILED if progress notification did not settle it. Fresh HTTP-polled scans settled as FAILED in 609ms and 812ms, retaining the directory error.
- Queued scan pause was exposed in the table but returned 500. It now immediately persists PAUSED/MANUAL; repeated pause returns 200 without another mutation. Live checks confirm cancel is idempotent and pausing a canceled scan returns 409.
- Process recovery: after injecting interrupted RUNNING job/item and scan checkpoint state, the actual backend JVM was terminated with `kill -9` and restarted. The incomplete item returned to QUEUED, the paused owner's job remained PAUSED, and the scan recovered as QUEUED/RESUME_SCAN retaining 3/10 progress. Owner resume allowed processing; missing photo targets settled as SKIPPED, and the invalid resumed scan settled as FAILED after the fix above.
- Chrome network emulation: offline refresh displays an error while retaining loaded table rows; reconnect plus refresh clears the error and restores loading/polling. Completed/skipped jobs clear obsolete interruption errors when finalized, and the table gives success/skip summaries rather than an unexplained blank result.
- Java 11 full suite: 559 tests, zero failures/errors/skips. After the final failure-counter adjustment, ScanTaskServiceTest passed again. Frontend production build (257 modules) and `git diff --check` passed.

Temporary accounts, their tasks/items/owner controls, and their login/operation records were removed after all test work was terminal. The services remain available on frontend 3030 and backend 6060.

Not covered by this run: real remote-provider quota exhaustion, provider outage/timeouts, physical power loss during ONNX inference, database/disk exhaustion, and sustained high-volume multi-user load. The process-kill check verifies durable recovery with fault-injected state, not all of those failure modes. Detailed face-detection output counts are also separate from the task's successful-photo count.

## Compact task console (2026-10-09)

- Keep one table and one combined count/filter strip: all, needs attention, running, waiting, and ended. Counts cover the entire authorized scope, not the current page; filtering precedes database pagination.
- Move creation time, configuration, retry source, and expandable technical errors into a task-name detail dialog. Keep progress, result, and necessary actions in the table. Normal account overview still has no account selector; cross-account inspection stays in the super-admin page.
- Missing files, quota, authentication, rate limiting, network errors, and unavailable models have short summaries and corrective actions. Background-job error codes are derived from the existing summary classifier, with textual fallback for older records; this is not a new persisted error schema.
- Confirm task cancellation and global pause/resume, retain mutation debounce, and disable batch retry when there are no exceptional records or when viewing system tasks. Batch retry still uses the existing recent-failure policy, not all historical failures.
- Show last successful refresh or connection failure. Same-scope refresh failures retain loaded records; changing scope/filter/page clears old rows so a failed request cannot mislabel another scope's results.
- Real MySQL testing found incompatible collations between scan and background-job status columns. UNION queries now explicitly normalize these columns to utf8mb4_unicode_ci without changing stored data or schema.

Verification: OpenJDK 11.0.30 runs 28 focused history/job tests with zero failures/errors; the backend starts on Java 11 with development hot restart disabled. Real authenticated HTTP/Chrome checks pass for scope-wide counts independent of page size, server-side failure filtering, task detail and Escape close, ordinary overview account-selector absence, external pagination, 390px mobile document width, offline data retention/reconnection, and super-admin all-account/individual-account/system scopes. Existing records only were read; no jobs were retried or photo data changed. Frontend production build and diff whitespace checks pass.

Limits: current real records are terminal, so this run does not reproduce simultaneous active model jobs, remote quota depletion, or sustained load. Full retry-impact preview, item-level failure drilldown, and a persisted structured user-message schema are not added by this compact-console change.
