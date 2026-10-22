# SJP Pulse

If you’ve ever applied for government jobs on the **Sindh Job Portal (`sjp.gos.pk`)**, you already know the frustration: the website’s dashboard barely tells you anything about where your application actually stands. 

Even when a department updates your status in the system—marking you as **Shortlisted**, **Temporarily Shortlisted**, **Waiting**, **Offered**, or **Rejected** (often with a specific reason attached)—the front-end webpage doesn't show most of those details. For a long time, the only way to see what was really happening was to log into Firefox, run a custom JavaScript bookmarklet, and dig through the portal's backend responses manually.

**SJP Pulse** was built to turn that workaround into a clean, effortless Android app. You log into your SJP account once, and the app quietly keeps an eye on all your job applications, shows you the hidden status details in plain sight, and sends you a notification the moment anything changes.

---

## What the App Does

### 1. Reveals Your Real Application Status
Once you sign in, SJP Pulse fetches all pages of your applied jobs directly from your SJP account and organizes them on a single dashboard. For every job you've applied to, you can immediately see:
- **Current Status**: Whether you are *Applied*, *Shortlisted*, *Temporarily Shortlisted*, *Interview*, *Waiting*, *Offered*, *Temporarily Offered*, *Joined*, or *Rejected*.
- **Rejection Reasons & Remarks**: If an application was rejected during scrutiny, the app surfaces the exact reason returned by the portal instead of leaving you guessing.
- **Offer & Interview Flags**: Quickly see whether an interview has been marked or if an offer record exists for your application.
- **Fast Search & Filtering**: Filter your applications by status, department, unread updates, or search by job title and application ID.

### 2. Checks for Updates Automatically & Notifies You
You don't have to keep opening the portal every day to check if a department has finished scrutiny:
- The app automatically checks your SJP applications in the background (every 30 minutes by default, or whatever interval you choose).
- Whenever you want an instant update, just pull down to refresh or tap the sync button.
- The moment a status changes—for example, moving from *Applied* to *Shortlisted*—you get a push notification on your phone telling you which job and department just updated.

### 3. Tracks Your Full Status History
Every application goes through multiple stages over weeks or months. The **History** tab keeps a running timeline of every status change across all your applications, complete with timestamps and before-and-after comparisons, so you always know *when* an update happened.

### 4. Personal Notes, Interview Countdowns & Raw Data Inspector
Tapping any job card opens a detailed view with three handy tools:
- **Status Overview & History**: Every timestamped status update recorded by the SJP backend for that specific job.
- **Personal Notes & Interview Reminder**: Jot down your own notes (like test center details, roll numbers, or documents to bring) and set an interview date to see a live countdown badge right on your dashboard.
- **Raw JSON Inspector**: For power users who want to see the exact, untouched data packet coming from the SJP server, you can inspect and copy the raw JSON payload for any application.

### 5. Visual Breakdown of Your Applications
The **Analytics** tab gives you a bird’s-eye view of your job hunt:
- A visual **Recruitment Pipeline Funnel** showing what percentage of your applications are at each stage.
- A **Departments Breakdown** showing how many posts you’ve applied for across different Government of Sindh departments.

### 6. Privacy-First: Nothing Stays Behind When You Log Out
Your SJP account data belongs to you. 
- While you are logged in, your session token is locked using your phone’s hardware security.
- **The moment you log out** (either inside the SJP webpage or using the app’s logout button), **SJP Pulse immediately wipes all application records, status history, notes, and session tokens from the app.** Nothing is kept behind after you sign out.

---

## How to Use It

1. Open **SJP Pulse** and go to the **SJP Login** tab.
2. Log into `sjp.gos.pk` just like you normally would in a browser.
3. Tap **"Fetch My SJP Applications Now (All Pages)"** (or let the app automatically detect your login).
4. Switch to the **Applied** tab to browse your applications, filter by status, or tap any job to view its full history and hidden fields.

---

## Technical Details (Under the Hood)

This section is for developers or anyone curious about how SJP Pulse works internally.

### Architecture & Tech Stack
- **Language & UI**: 100% **Kotlin** and **Jetpack Compose** (Material Design 3 with dynamic dark/light theming and adaptive layouts for phones, foldables, and tablets).
- **Pattern**: **MVVM (Model-View-ViewModel)** with reactive Kotlin `Coroutines` and `StateFlow`.
- **Local Cache**: **Jetpack Room** (`AppliedJobEntity`, `StatusHistoryEntity`) used strictly as an active-session cache that is automatically purged on logout or `HTTP 401/403`.
- **Networking**: **OkHttp 4** combined with an authenticated WebView JS bridge (`AndroidSjpBridge`) to handle multi-page fetching seamlessly.

### SJP Backend API Integration
- **Endpoint**: `https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate?page={p}&limit=10&keyword=`
- **Multi-Page Pagination**: Automatically iterates from `page = 1` through `data.pagination.totalPages`, merging all `data.applications` arrays into a unified dataset.
- **Extracted Backend Fields**:
  - `applicationId`, `applicationStatus`, `jobOffered`, `Offer`, `reasonForRejected`
  - `Job.jobTitle`, `Job.bps`, `Job.jobInterviewStatus`, `Job.Department.departmentName`
  - `ApplicationStatuses[]` (`status`, `changedAt`) for constructing historical timelines and detecting diffs.

### Security & Session Encryption
- **Hardware-Backed Encryption (`TokenCryptoManager`)**: Session tokens (`Bearer` JWTs) and portal cookies are encrypted using **AES-256-GCM** with keys generated and stored inside the Android hardware-backed `AndroidKeyStore` (`sjp_pulse_session_aes_key_v1`) before being written to Jetpack `DataStore`.
- **Zero-Retention Logout Hook**: The embedded WebView injects listeners for DOM logout clicks, `Storage.prototype.clear`/`removeItem`, logout URLs, and `401/403` API responses. Any logout event triggers a complete wipe of Room tables, DataStore ciphertexts, and WebView cookies/storage.

### Android Version Compatibility
- **Supported Range**: **Android 7.0 (API 24)** up to **Android 16 (API 36)** and **Android 17 (API 36.1)**.
- **Android 16/17 WebView Threading Safety**: All `CookieManager.getInstance()` operations are marshaled onto `Dispatchers.Main` to prevent Chromium `WebViewFactory` initialization crashes on background coroutine workers.
- **Runtime Permissions**: Uses `POST_NOTIFICATIONS` runtime permission flow on Android 13+ (API 33+) with automatic channel fallback on older Android versions.

### Automated CI/CD Release Pipeline
- **Workflow**: `.github/workflows/release-beta.yml` (documented in [`GIT_WORKFLOW.md`](GIT_WORKFLOW.md)).
- Every push to `main` or `master` compiles both Release and Debug APKs on GitHub Actions (JDK 21 + Gradle 9.3.1), packages the source code archives (`.zip` and `.tar.gz`), cleans up any orphaned tags from deleted releases, and publishes a sequential GitHub Pre-Release (`beta 0.3`, `beta 0.4`, etc.).
