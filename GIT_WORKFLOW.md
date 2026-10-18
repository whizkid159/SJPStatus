# Git Workflow & Automated Beta Release Guide (`beta 0.1`, `beta 0.2`, …)

This document explains how **SJP Pulse** automatically builds the Android application APK, archives the complete source code, creates an annotated Git tag (`beta-0.1`, `beta-0.2`, `beta-0.3`, …), and publishes a full GitHub Release with **Tag**, **Title**, **Description**, **App APK**, and **Source Code** on every push to your private repository.

---

## 1. Root Cause of the Step 4 Failure & How It Was Resolved

In your screenshot and logs (`4_Setup Android SDK & Accept All SDK Licenses.txt`), the run failed at `android-actions/setup-android@v3` after 10 seconds because that third-party action attempts to re-install legacy command-line tools on GitHub's `ubuntu-latest` runner image.

To make the workflow fast and self-contained without fragile third-party setup actions:
1. **Removed `android-actions/setup-android@v3`**: Replaced with a native Bash step (`Configure Android SDK & Write License Hashes`) that uses the pre-installed Android SDK on `ubuntu-latest` (`/usr/local/lib/android/sdk`) and directly writes all official Android SDK license hashes (`android-sdk-license`, `android-sdk-preview-license`, etc.) so Gradle can automatically provision `compileSdk 36.1`.
2. **Direct Gradle 9.3.1 Binary Setup**: Downloads `gradle-9.3.1-bin.zip` directly from `services.gradle.org` to match `gradle-wrapper.properties` and AGP `9.1.1`.
3. **Complete Release Metadata & Assets via Native GitHub CLI (`gh release create`)**:
   - **Tag**: `beta-0.1`, `beta-0.2`, `beta-0.3`, … (annotated Git tag pushed to origin).
   - **Title**: `beta 0.1 — SJP Pulse (App APK + Source Code)`
   - **Description**: Detailed Markdown release notes (`release-notes.md`) including build timestamp, commit SHA, package ID, feature summary, and commit changelog.
   - **Application & Source Code Attachments**:
     - `SJP-Pulse-beta-0.X.apk` (Compiled Android application)
     - `SJP-Pulse-beta-0.X-source.zip` (Complete repository source code ZIP)
     - `SJP-Pulse-beta-0.X-source.tar.gz` (Complete repository source code TAR.GZ)

---

## 2. Required Private Repository Permission

Make sure `GITHUB_TOKEN` has permission to push tags and create releases:
1. In your private GitHub repository, navigate to **Settings → Actions → General**.
2. Scroll down to **Workflow permissions**.
3. Select **Read and write permissions** and click **Save**.
