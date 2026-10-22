# Git Workflow & Automated Beta Release Guide (`beta 0.3` Onwards)

This document explains how **SJP Pulse** automatically builds the Android application APK, archives the complete source code, cleans up orphaned git tags from deleted releases, and publishes sequential GitHub Releases (`beta 0.3`, `beta 0.4`, …) with **Tag**, **Title**, **Description**, **App APK**, and **Source Code** on every push to your private repository.

---

## 1. How Version Numbering Works (`beta 0.3` Onwards)

When you delete a release in the GitHub UI, GitHub often leaves the underlying Git tag (e.g., `beta-0.3`, `beta-0.4`, `beta-0.5`, `beta-0.6`) in the repository. Previously, checking `git tag -l` caused the workflow to skip ahead to `beta 0.6`.

The workflow (`.github/workflows/release-beta.yml`) now:
1. Queries **only active published GitHub Releases** (`gh release list`) rather than raw Git tags, with a baseline floor of `beta-0.2`.
2. Automatically deletes any orphaned remote Git tags higher than the latest active GitHub Release (`beta-0.3`, `beta-0.4`, `beta-0.5`, `beta-0.6`, etc.).
3. Publishes your next push as **`beta 0.3`** (tag `beta-0.3`), and subsequent pushes as **`beta 0.4`**, **`beta 0.5`**, etc. If you ever delete a future release on GitHub, the next push will automatically reuse that deleted version number.

---

## 2. Release Deliverables

Every push to `main` or `master` produces a GitHub Pre-Release containing:
- **Tag**: `beta-0.3`, `beta-0.4`, …
- **Title**: `beta 0.3 — SJP Pulse (Android 7–17 Release APK + Source Code)`
- **Description**: Markdown release notes (`release-notes.md`) with build timestamp, commit SHA, feature summary, and commit changelog.
- **Attached Files**:
  - `SJP-Pulse-beta-0.X.apk` (Signed Production Release APK)
  - `SJP-Pulse-beta-0.X-debug.apk` (Debug APK)
  - `SJP-Pulse-beta-0.X-source.zip` (Complete source code ZIP)
  - `SJP-Pulse-beta-0.X-source.tar.gz` (Complete source code TAR.GZ)

---

## 3. Required Private Repository Permission

Ensure `GITHUB_TOKEN` has permission to push tags and create releases:
1. In your private GitHub repository, navigate to **Settings → Actions → General**.
2. Scroll down to **Workflow permissions**.
3. Select **Read and write permissions** and click **Save**.
