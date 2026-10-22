# SJP Pulse — Native Login, Smart Job Matcher, In-App Apply & Android 16 Compatibility Plan

This plan upgrades **SJP Pulse** with **100% Native In-App Login (CNIC & Password)** powered by a **Headless Background Browser & API Engine**, a **Smart Job Recommendation Engine** matched to your education and eligibility, **In-App Job Application Submission** with an **Eligibility Match Breakdown Sheet**, and **full Android 7.0 – Android 16 (API 24–36) startup & backwards compatibility hardening**.

## User Review & Critical Decisions

> [!IMPORTANT]
> This revised plan adds dedicated **Android 16 (API 36) & Backwards Compatibility** fixes to resolve the launch failure reported on Android 16 devices. Click **Proceed** to execute all changes.

- **Confirmed Decision — Android 16 (API 36) & Backwards Compatibility Hardening**:
  1. **Defer & Isolate `WebView` / `CookieManager` to Main Thread After First Frame**: Calling `CookieManager.getInstance()` on a background thread (`Dispatchers.IO`) during cold `ViewModel.init` can crash or deadlock Chromium initialization on Android 15/16. All `CookieManager` and headless `WebView` calls will strictly run on `Dispatchers.Main` after the first Compose frame renders, wrapped in `runCatching` with a pure-`OkHttp` fallback if the device has a restricted or missing WebView provider.
  2. **Release APK Packaging in GitHub Actions (`assembleRelease`)**: Android 16 (and OEM security layers like Samsung Auto Blocker / Google Advanced Protection) can block or terminate sideloaded APKs built with `android:debuggable="true"` (`app-debug.apk`). Our GitHub workflow will now automatically sign and publish a true **production Release APK** (`assembleRelease`, `android:debuggable="false"`) in addition to the debug APK.
  3. **Android 16 Predictive Back & Manifest Compatibility**: Enables `android:enableOnBackInvokedCallback="true"` in `AndroidManifest.xml` and ensures safe edge-to-edge window insets across API 24 through API 36.
- **Confirmed Decision — Direct Native CNIC & Password Login (Background Browser Engine)**: Sign in via a native Compose form using your **CNIC** and **Password**. An invisible background browser controller (`0.dp` off-screen `WebView` + direct `OkHttp` API bridge) authenticates silently against `sjp.gos.pk` / `usr-svc.sjp.gos.pk` and persists your session.
- **Confirmed Decision — Auto-Fetched Education & Eligibility Profile + Manual Overrides**: Automatically fetches your candidate profile, education level, degree major, domicile district, and age from SJP APIs (with editable filter overrides) to score and recommend open jobs.
- **Confirmed Decision — Eligibility Match Breakdown & Confirmation Sheet Before Applying**: Displays a requirement-by-requirement eligibility checklist (Education, Major, Domicile, Age, BPS Scale) before submitting your application to `usr-svc.sjp.gos.pk`.

---

## 1. Overview & Core Concept

- **What It Does**:
  1. **Universal Android 7–16 Compatibility**: Launches reliably into a native Material 3 interface on every Android version from Android 7.0 (API 24) to Android 16 (API 36) without cold-start WebView deadlocks or `debuggable` APK launch blocks.
  2. **Native Login & Headless Session Persistence**: Sign in once with CNIC + Password on a native Compose screen; the background engine handles portal authentication, token/cookie extraction, and automatic session renewal.
  3. **Hidden Status Radar & 30-Minute Poller**: Polls `https://usr-svc.sjp.gos.pk/api/v2/candidates/application/getAllAppliedJobsByCandidate` across all pages (`1..totalPages`) and sends push notifications on status transitions.
  4. **Smart Job Recommendations (`Jobs For You` Tab)**: Syncs open SJP job listings from `usr-svc.sjp.gos.pk`, evaluates each vacancy against your candidate education, domicile, and age profile, and highlights **Best Match** and **Eligible** jobs.
  5. **In-App Job Application**: Lets you review a detailed **Eligibility Match Breakdown** and submit your job application directly from the app, then immediately tracks the new application in your Status Radar.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Native CNIC & Password Login Flow (`Account` Tab & Dashboard Banner)**:
   - Enter **CNIC** (auto-formatted `XXXXX-XXXXXXX-X` or 13 digits) and **Password** in native Material 3 text fields, with a **Remember Me & Auto-Relogin** toggle.
   - Tapping **Sign In to SJP** runs the dual REST + Headless Background Browser authentication flow and displays live progress steps (*Authenticating CNIC & Password* → *Capturing Session Token* → *Syncing Applied Jobs & Candidate Profile*).
2. **Candidate Eligibility Profile & Smart Job Recommendations (`Jobs For You` Tab)**:
   - **Top Eligibility Profile Card**: Shows your synced/configured **Highest Qualification** (e.g., Master's 16-Year, Bachelor's 16-Year, Bachelor's 14-Year, Intermediate, Matric), **Major Field**, **Domicile District & Quota** (`Urban` / `Rural`), and **Age**, with an **Edit Eligibility Profile** button for manual overrides.
   - **Recommended Jobs Feed**: Displays active SJP vacancies with a color-coded **Eligibility Match Badge** (`98% Match • Eligible`, `Already Applied`, or `Qualification Mismatch`), BPS scale, department, closing date, and required education.
3. **Eligibility Breakdown & One-Tap Apply Confirmation Sheet**:
   - Selecting **Review & Apply** on any open job opens a confirmation sheet displaying:
     - **Eligibility Checklist**: Side-by-side comparison of *Job Requirement* vs *Your Profile* for Qualification, Major/Subject, Domicile Quota, and Age Limit.
     - **Confirm & Submit Application** button: Submits the application to `usr-svc.sjp.gos.pk` via your authenticated session, records the newly created application, and refreshes your **Applications** status radar.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Main-Thread-Safe Deferred Headless Engine + Pure OkHttp Fallback (Android 16 Fix)**
  - *Chosen Approach*: Never invoke `CookieManager.getInstance()` or instantiate `WebView` on a background thread (`Dispatchers.IO`) or during synchronous `Activity.onCreate()`. Instead, initialize cookies on `Dispatchers.Main` after the UI is active, and wrap all WebView operations in `runCatching` so devices without a working WebView provider still function seamlessly via direct `OkHttp` calls to `usr-svc.sjp.gos.pk`.
  - *Why*: Eliminates cold-start crashes on Android 15 and Android 16 where Chromium's `WebViewFactory` enforces strict thread and process data-directory checks.
- **Decision 2: Production Release APK Default in GitHub Actions (`assembleRelease`)**
  - *Chosen Approach*: In `.github/workflows/release-beta.yml`, automatically generate a valid release signing keystore if custom secrets are not provided so that `gradle :app:assembleRelease` always runs and produces a non-debuggable, zipaligned **Release APK** (`SJP-Pulse-beta-0.X.apk`).
  - *Why*: Android 16's Advanced Protection Mode and OEM security blockers often refuse to launch sideloaded `debug` APKs (`android:debuggable="true"`). Shipping a signed `release` APK ensures smooth installation and startup on Android 16.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

### Architecture & Component Diagram

```
┌────────────────────────────────────────────────────────────────────────────┐
│             NATIVE COMPOSE UI LAYER (API 24 – API 36 SAFE)                 │
│                                                                            │
│  ┌─────────────────┐ ┌──────────────────┐ ┌──────────────┐ ┌────────────┐  │
│  │  Applications   │ │   Jobs For You   │ │ Status Diffs │ │  Account   │  │
│  │  Status Radar   │ │ (Smart Matcher & │ │   & History  │ │  & Native  │  │
│  │  (& Analytics)  │ │  In-App Apply)   │ │   Timeline   │ │ SJP Login  │  │
│  └────────┬────────┘ └────────┬─────────┘ └──────┬───────┘ └─────┬──────┘  │
│           │                   │                  │               │         │
│  ┌────────┴───────────────────┴──────────────────┴───────────────┴──────┐  │
│  │ • Eligibility Breakdown & One-Tap Apply Confirmation Modal Sheet     │  │
│  │ • Job Detail, Status History & Raw JSON Inspector Modal Sheet        │  │
│  └───────────────────────────────────┬──────────────────────────────────┘  │
└──────────────────────────────────────┼─────────────────────────────────────┘
                                       ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   VIEWMODEL & ELIGIBILITY MATCHING ENGINE                  │
│                                                                            │
│  • SjpTrackerViewModel (Main-Thread Safe Lifecycle & StateFlows)           │
│  • EligibilityMatcher (Scores Open SJP Jobs vs Candidate Education/Quota)  │
└───────────────────┬────────────────────────────────────┬───────────────────┘
                    ▼                                    ▼
┌──────────────────────────────────────┐ ┌───────────────────────────────────┐
│   HEADLESS BACKGROUND SJP ENGINE     │ │     LOCAL PERSISTENCE LAYER       │
│                                      │ │                                   │
│ • OkHttp Direct SJP API v2 Client    │ │ • AppliedJobEntity (Room DB v3)   │
│   - Direct Login / Profile / Jobs /  │ │ • OpenJobEntity (Room DB v3)      │
│     Apply REST endpoints             │ │ • StatusHistoryEntity (Room DB)   │
│ • Deferred 0dp Background WebView    │ │ • CandidateProfile & Credentials  │
│   - Main-thread safe DOM auto-login  │ │   (DataStore Preferences)         │
│   - Multi-page fetch & apply bridge  │ │                                   │
└──────────────────────────────────────┘ └───────────────────────────────────┘
```

### Data Model & State Additions

1. **`CandidateProfile` (Persisted in DataStore)**:
   - `cnic`, `savedPassword`, `rememberCredentials`
   - `fullName`, `highestDegreeLevel` (`Matric`, `Intermediate`, `Bachelor (14 Years)`, `Bachelor (16 Years)`, `Master / MS (16+ Years)`), `majorField` (`Computer Science / IT`, `Commerce / Finance`, `Administration / Management`, `Engineering`, `Education`, `Economics / Statistics`, `General / Arts`), `domicileDistrict`, `domicileQuota` (`Urban`, `Rural`, `All Sindh`), `ageYears`, `experienceYears`.
2. **`OpenJobEntity` (Room Table `open_sjp_jobs`)**:
   - `jobId` (Primary Key), `jobTitle`, `departmentName`, `bpsScale`, `requiredDegreeLevel`, `requiredMajor`, `domicileQuota`, `minAge`, `maxAge`, `experienceRequiredYears`, `totalPositions`, `closingDate`, `advertisementNo`, `rawJsonPayload`.
3. **`EligibilityResult` (Computed Evaluation)**:
   - `matchScore` (`0..100`), `isEligible`, `isAlreadyApplied`
   - `checks`: List of `RequirementCheck(label, requiredValue, candidateValue, passed)`.
