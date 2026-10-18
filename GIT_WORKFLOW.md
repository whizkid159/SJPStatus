# Git Workflow & Automated Beta Release Guide (`beta 0.1`, `beta 0.2`, …)

This document explains how **SJP Pulse** automatically builds the Android application APK, archives the complete source code, creates an annotated Git tag (`beta-0.1`, `beta-0.2`, `beta-0.3`, …), and publishes a full GitHub Release with **Tag**, **Title**, **Description**, **App APK**, and **Source Code** on every push to your private repository.

---

## 1. Why the Previous Run Failed & How It Is Fixed

1. **Gradle 9.3.1 & JDK 21 Alignment**: The project uses Android Gradle Plugin `9.1.1` and `compileSdk 36.1`, which require **Gradle 9.3.1** and **JDK 21** (updated from Gradle 8.11.1 / JDK 17) plus automatic Android SDK license acceptance (`yes | sdkmanager --licenses`).
2. **First-Run Tag Detection (`pipefail` Fix)**: On a repository with zero existing `beta-0.*` tags, `grep` in a piped command returns exit code `1`, which caused Bash `-o pipefail` to abort before creating `beta-0.1`. Replaced with a pure Bash tag parser loop that safely starts at `beta-0.1` and increments to `beta-0.2`, `beta-0.3`, etc.
3. **Complete Tags, Titles, Descriptions & Release Assets**:
   - **Annotated Git Tag**: `beta-0.1`, `beta-0.2`, etc., pushed directly to origin with full release notes embedded in the tag description.
   - **Release Title**: `beta 0.1 — SJP Pulse (App APK + Source Code)`
   - **Release Description (`release-notes.md`)**: Auto-generated Markdown containing build metadata, package ID, deliverables list, feature overview, and the Git commit changelog since the previous beta tag.
   - **Attached Application & Source Code Files**:
     - `SJP-Pulse-beta-0.X.apk` (Compiled Android application ready to install)
     - `SJP-Pulse-beta-0.X-source.zip` (Complete repository source code in `.zip` format)
     - `SJP-Pulse-beta-0.X-source.tar.gz` (Complete repository source code in `.tar.gz` format)

---

## 2. Required Private Repository Permission (One-Time Check)

For GitHub Actions to push tags (`beta-0.1`, `beta-0.2`) and create releases in a private repository, ensure `GITHUB_TOKEN` has write access:

1. Open your private repository on GitHub and go to **Settings → Actions → General**.
2. Scroll down to **Workflow permissions**.
3. Select **Read and write permissions** and click **Save**.

---

## 3. How the Automated Release Flow Works

Every time you push to `main` or `master` (or click **Run workflow** under the **Actions** tab):

```
┌──────────────────────────────────────────────────────────────────────┐
│  git push origin main                                                │
└──────────────────────────────────┬───────────────────────────────────┘
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│  1. Inspect existing `beta-0.*` tags in repository                   │
│     • None found  ──► Next Tag: `beta-0.1`                           │
│     • `beta-0.1`  ──► Next Tag: `beta-0.2`                           │
└──────────────────────────────────┬───────────────────────────────────┘
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│  2. Generate Release Title & Markdown Description (`release-notes`)  │
│     • Title: "beta 0.X — SJP Pulse (App APK + Source Code)"          │
│     • Description: Metadata + Feature Summary + Commit Changelog     │
└──────────────────────────────────┬───────────────────────────────────┘
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│  3. Build & Package Deliverables                                     │
│     • Compile Android App: `gradle :app:assembleDebug`               │
│     • Package APK: `SJP-Pulse-beta-0.X.apk`                          │
│     • Package Source Code: `SJP-Pulse-beta-0.X-source.zip` & `.tgz`  │
└──────────────────────────────────┬───────────────────────────────────┘
                                   ▼
┌──────────────────────────────────────────────────────────────────────┐
│  4. Push Annotated Git Tag & Publish GitHub Release                  │
│     • Pushes annotated tag `beta-0.X` with description               │
│     • Publishes GitHub Release with APK + Source Code attached       │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 4. Optional: Signed Production Release APK

By default, the workflow compiles and attaches an installable APK (`SJP-Pulse-beta-0.X.apk`) using a CI-generated keystore so zero manual setup is required. If you also want a custom-signed production APK (`SJP-Pulse-beta-0.X-signed-release.apk`) attached to each release, add these optional secrets in **Settings → Secrets and variables → Actions**:

| Secret Name | Description |
| :--- | :--- |
| `KEYSTORE_BASE64` | Base64-encoded `.jks` keystore (`base64 -w 0 my-upload-key.jks`) |
| `STORE_PASSWORD` | Keystore password |
| `KEY_PASSWORD` | Key password for the `upload` alias |
