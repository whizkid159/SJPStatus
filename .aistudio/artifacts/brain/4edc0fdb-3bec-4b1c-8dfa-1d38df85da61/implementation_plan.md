# SJP Pulse — Sindh Job Portal Hidden Status Tracker

**SJP Pulse** is a specialized Android utility that authenticates with `sjp.gos.pk`, automatically persists your candidate session, and polls the hidden backend endpoint (`api/v2/candidates/application/getAllAppliedJobsByCandidate`) to reveal unmasked application statuses (`Shortlisted`, `Temporary Shortlisted`, `Offered`, `Temporary Offered`, `Rejected`, etc.), scrutiny remarks, and merit metrics—alerting you via instant notifications the moment any status changes.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following product and technical choices reflect your confirmed preferences and are ready for implementation upon your approval.

- **Confirmed Decision — Embedded WebView Login & Auto-Capture**: Uses a persistent Android `WebView` with DOM storage and `CookieManager` flushing, paired with an injected JavaScript XHR/Fetch & `localStorage` bridge that automatically captures your active `sjp.gos.pk` session cookies, Bearer token, and candidate profile without requiring re-login across app restarts.
- **Confirmed Decision — 30-Minute Background Polling + Pull-to-Refresh**: Polls `api/v2/candidates/application/getAllAppliedJobsByCandidate` automatically every 30 minutes in the background (and on-demand via pull-to-refresh or background WebView fetch fallback), comparing every field against the local snapshot to trigger rich Android notifications on status changes.
- **Confirmed Decision — Full Engagement, Analytics & Inspection Suite**:
  - **Status Change History Timeline** with before-and-after status badge diffs and timestamps.
  - **Raw JSON Field Inspector** to browse every hidden backend key/value returned by the API (scrutiny notes,BPS scale, test scores, internal flags).
  - **Visual Analytics Dashboard** featuring custom Canvas status funnel bars and department-wise distribution breakdowns.
  - **Per-Application Notes & Interview Countdowns** plus **Customizable Status Report Exports** (CSV and formatted summary shareable via Android Share Sheet).

---

## 1. Overview & Core Concept

- **What It Does**: Bridges the gap between the Sindh Job Portal (`sjp.gos.pk`) web frontend and its more detailed backend API (`api/v2/candidates/application/getAllAppliedJobsByCandidate`). While the web UI often masks intermediate states, SJP Pulse extracts the raw candidate payload, highlights hidden statuses (`Temporary Shortlisted`, `Shortlisted`, `Temporary Offered`, `Offered`, `Rejected`, `Under Scrutiny`), logs historical changes, and sends immediate system notifications.
- **Target Audience / Persona**: Candidates applying for Government of Sindh jobs (BPS 1–15 and portal-listed departmental posts) who previously relied on manual desktop Firefox JavaScript snippets to inspect their real application status.
- **Key Value**: Eliminates manual browser console inspection by turning the backend endpoint into a persistent, automated mobile command center with change alerts, historical audit trails, interview countdowns, and exportable reports.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Seamless Portal Session Capture & Persistence**:
   - Opening the **Portal Session** tab (or tapping *Connect SJP Account* from the dashboard banner) launches an integrated `sjp.gos.pk` WebView with persistent `CookieManager` and DOM storage.
   - As soon as the user logs in on `sjp.gos.pk`, an injected JavaScript bridge intercepts `localStorage`/`sessionStorage` tokens, HTTP cookies, and any in-page calls to `api/v2/candidates/application/getAllAppliedJobsByCandidate`.
   - Even when the app is closed and reopened, the persisted WebView cookies and captured headers keep the session alive, and the app can execute authenticated fetches both via OkHttp and via a headless WebView `fetch()` bridge (ensuring browser-bound cookies/CORS/CSRF headers always succeed identically to Firefox).
2. **Live Status Dashboard & Filtering**:
   - Displays a top sync bar showing session health, last poll timestamp, next scheduled 30-minute check, and a **Sync Now** / Pull-to-Refresh trigger.
   - Summary KPI strip at the top shows counts for **Total Applied**, **Shortlisted / Temp Shortlisted**, **Offered / Temp Offered**, and **Rejected**.
   - Search and filter chips let users filter jobs by hidden status, Government Department, BPS grade, or upcoming interview reminder.
3. **Application Detail, Raw JSON Inspector & Personal Workspace**:
   - Tapping any job card opens a bottom sheet / detail view with three tabs:
     - **Overview & Notes**: Full job title, department, BPS scale, advertisement date, hidden backend status badge, personal preparation notes, and an interactive **Interview / Test Countdown Reminder** date picker.
     - **Status History**: Chronological before-and-after diff log for this specific application.
     - **Raw JSON Inspector**: Interactive key-value explorer and syntax-highlighted JSON viewer showing the exact unmasked backend object from `getAllAppliedJobsByCandidate` with one-tap copy.
4. **Analytics, Audit Timeline & Custom Report Builder**:
   - **Timeline Tab**: Global feed of every detected status transition across all applications (e.g., `Applied` → `Temporary Shortlisted`) with exact date/time and quick filter for unread changes.
   - **Analytics & Reports Tab**: Interactive status funnel chart, department breakdown chart, and a **Custom Report Builder** that lets users filter by status or department and export/share a clean **CSV** or **Formatted Executive Text Report**.

### Visual Identity & Theme

- **Aesthetic Direction**: **Civic Precision & Editorial Utility** — inspired by official Sindh civic heritage paired with modern developer telemetry tools. Crisp structured cards, high-contrast status pills, and monospace data readouts.
- **Color Palette & Mood**:
  - **Primary (`#0B6E4F` — Sindh Emerald Green)**: Dominant brand color for headers, active navigation indicators, and primary actions.
  - **Secondary (`#0F172A` — Deep Slate Navy)**: High-authority dark surface accents for the sync telemetry header and raw JSON inspector.
  - **Semantic Status Tokens**:
    - `Offered` / `Temporary Offered`: `#059669` (Emerald) & `#10B981` (Mint Teal)
    - `Shortlisted` / `Temporary Shortlisted`: `#D97706` (Amber Gold) & `#2563EB` (Royal Blue)
    - `Rejected`: `#DC2626` (Crimson Red)
    - `Applied` / `Pending Scrutiny`: `#64748B` (Cool Slate)
  - **Surfaces**: Warm Alabaster (`#F8FAFC`) in Light Mode and Deep Obsidian (`#0B1120`) in Dark Mode.
- **Typography & Hierarchy**:
  - **Display / Headings**: `Space Grotesk` (bundled locally via `font-util`) for crisp, authoritative dashboard titles and KPI numerals.
  - **Body / UI Labels**: `Plus Jakarta Sans` for clean legibility across long department and post names.
  - **Telemetry & JSON Inspector**: `JetBrains Mono` for raw API keys, application IDs, CNIC masks, and timestamps.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dual-Engine API Polling (OkHttp + Headless WebView `fetch()` Bridge)**
  - *Chosen Approach*: Poll `https://sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate` using both an authenticated OkHttp client (synced with `CookieManager` and captured `Authorization` headers) and an automatic fallback to executing `window.fetch('/api/v2/candidates/application/getAllAppliedJobsByCandidate')` directly inside the persistent WebView context.
  - *Why*: The user noted they currently run JavaScript inside Firefox to read this endpoint. Executing `fetch()` inside the authenticated WebView context guarantees 100% parity with browser-based JavaScript execution (automatically attaching `SameSite` cookies, session storage tokens, and browser fingerprints) while OkHttp supports lightweight background checks.
  - *Alternatives Considered*: Pure Retrofit-only requests without WebView JS bridge (rejected because portal WAF/session cookies or token refresh scripts often rely on browser context).
- **Decision 2: Local Room SQLite Persistence + DataStore Session Vault**
  - *Chosen Approach*: Store all parsed job applications, raw JSON payloads, status change audit diffs, personal notes, and interview countdown dates in a local **Room Database**, with session tokens/polling preferences in preferences storage.
  - *Why*: Ensures instant offline startup, accurate field-by-field diff calculation during every 30-minute background poll, and full privacy for candidate credentials on the device.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         UI LAYER (Jetpack Compose)                        │
│                                                                           │
│  ┌──────────────────┐  ┌──────────────────┐  ┌─────────────────────────┐  │
│  │  Dashboard Tab   │  │  Status Diff     │  │ Analytics & Custom      │  │
│  │  (KPIs, Filters, │  │  Timeline Tab    │  │ Report Exporter Tab     │  │
│  │   Job List)      │  │  (Audit Log)     │  │ (Charts, CSV/Text)      │  │
│  └────────┬─────────┘  └────────┬─────────┘  └────────────┬────────────┘  │
│           │                     │                         │               │
│  ┌────────┴─────────────────────┴─────────────────────────┴────────────┐  │
│  │    Job Detail Sheet (Notes, Interview Countdown, Raw JSON Viewer)   │  │
│  └──────────────────────────────────┬──────────────────────────────────┘  │
│                                     │                                     │
│  ┌──────────────────────────────────┴──────────────────────────────────┐  │
│  │   SJP Portal Session WebView & JS Console (Auto Cookie/Token Sync)  │  │
│  └──────────────────────────────────┬──────────────────────────────────┘  │
└─────────────────────────────────────┼─────────────────────────────────────┘
                                      ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                     VIEWMODEL & DOMAIN SYNC ENGINE                        │
│                                                                           │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ SjpTrackerViewModel (StateFlow<UiState>, Filters, Report Generator) │  │
│  └──────────────────────────────────┬──────────────────────────────────┘  │
│                                     ▼                                     │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ SjpSyncRepository & 30-Min Background Polling Scheduler             │  │
│  │  • Diff Engine (Compares Previous Status vs New API Status)         │  │
│  │  • Notification Dispatcher (Fires High-Priority Status Alerts)      │  │
│  └───────────────┬─────────────────────────────────────┬───────────────┘  │
└──────────────────┼─────────────────────────────────────┼──────────────────┘
                   ▼                                     ▼
┌─────────────────────────────────────┐ ┌───────────────────────────────────┐
│   NETWORK & PORTAL BRIDGE LAYER     │ │   LOCAL PERSISTENCE (Room DB)     │
│                                     │ │                                   │
│ • SjpApiClient (OkHttp + Cookies)   │ │ • AppliedJobEntity (Status, Dept, │
│ • WebView JsBridge (`fetch()` hook) │ │   BPS, RawJson, Notes, Interview) │
│ • Endpoint: `/api/v2/candidates/    │ │ • StatusHistoryEntity (OldStatus, │
│   application/getAllAppliedJobs...` │ │   NewStatus, ChangedAt, Remarks)  │
└─────────────────────────────────────┘ └───────────────────────────────────┘
```

### Data Model & State

1. **`AppliedJobEntity` (Room Table `applied_jobs`)**:
   - `applicationId` (Primary Key, String)
   - `jobTitle`, `departmentName`, `bpsScale`, `advertisementNo`, `appliedDate`
   - `currentStatus` (Normalized enum/code: `SHORTLISTED`, `TEMP_SHORTLISTED`, `OFFERED`, `TEMP_OFFERED`, `REJECTED`, `UNDER_SCRUTINY`, `APPLIED`)
   - `rawStatusString` (Exact backend string from API)
   - `backendRemarks` (Hidden scrutiny remarks, test score, or rejection reason extracted from JSON)
   - `rawJsonPayload` (Full serialized JSON object for this job application)
   - `userNotes` (Candidate's personal preparation notes)
   - `interviewTimestamp` (Optional Long timestamp for countdown badge)
   - `lastUpdatedTimestamp` & `hasUnreadChange` (Boolean flag for badge highlight)
2. **`StatusHistoryEntity` (Room Table `status_history`)**:
   - `id` (Auto-generated Primary Key)
   - `applicationId`, `jobTitle`, `departmentName`
   - `previousStatus`, `newStatus`, `remarksSnapshot`, `detectedAtTimestamp`

### Interactive Component & State Mapping

- **Portal Session & JS Bridge**:
  - Loads `https://sjp.gos.pk` in an embedded `WebView` with `javaScriptEnabled = true` and `domStorageEnabled = true`.
  - Injects an interceptor script on page finish that hooks `XMLHttpRequest` and `window.fetch`, automatically capturing any `Authorization` header and forwarding the JSON response of `api/v2/candidates/application/getAllAppliedJobsByCandidate` directly to Kotlin via `@JavascriptInterface`.
  - Includes a **"Run API Fetch Now"** button inside the WebView toolbar that executes the exact `fetch('/api/v2/candidates/application/getAllAppliedJobsByCandidate', ...)` call inside the browser session and pipes the JSON into the Room database.
  - Also includes a **"Simulate / Test Payload"** & **"Paste Custom JSON / Token"** utility so the user can test status-change notifications immediately or import an existing Firefox JSON dump.
- **30-Minute Background Poller & Notification Engine**:
  - Uses a coroutine timer while active plus a periodic background alarm/scheduler set to 30-minute intervals.
  - Whenever `currentStatus` or `backendRemarks` differs from the stored `AppliedJobEntity`, inserts a `StatusHistoryEntity` record and dispatches a system notification via `NotificationCompat` (with runtime `POST_NOTIFICATIONS` permission flow on Android 13+).
- **Visual Analytics & Custom Reporting**:
  - Custom Compose `Canvas` charts render the **Application Status Funnel** and **Top Government Departments**.
  - Report generator compiles filtered jobs and status history into downloadable/shareable CSV or formatted text reports via Android `Intent.ACTION_SEND`.
