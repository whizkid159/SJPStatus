# Git Workflow & Automated Beta Release Guide (`beta 0.1`, `beta 0.2`, …)

This document explains how **SJP Pulse** automatically compiles the Android APK, packages the source code, increments the beta tag (`beta-0.1`, `beta-0.2`, `beta-0.3`, …), and publishes a new release whenever you push to your private Git repository.

---

## 1. Included Workflow File

The automated CI/CD pipeline is pre-configured at:

- **`.github/workflows/release-beta.yml`**

Whenever commits are pushed to the `main` or `master` branch (or triggered manually via the **Actions** tab), the workflow performs the following steps:

1. **Full Tag History Fetch**: Clones the private repository with `fetch-depth: 0` and inspects existing `beta-0.*` tags.
2. **Automatic Version Increment**:
   - If no `beta-0.*` tag exists yet, it assigns tag **`beta-0.1`** and release title **`beta 0.1`**.
   - On subsequent pushes, it automatically increments the sequence (`beta 0.2` / `beta-0.2`, `beta 0.3` / `beta-0.3`, and so on).
3. **Environment & Keystore Provisioning**:
   - Automatically generates `debug.keystore` if not present in the CI runner and copies `.env.example` to `.env`.
   - If optional release signing secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) are configured in the repository, it also builds the signed production release APK (`assembleRelease`).
4. **Artifact Packaging**:
   - `SJP-Pulse-beta-0.X-debug.apk` (Ready-to-install Android APK)
   - `SJP-Pulse-beta-0.X-release.apk` (Included automatically when signing secrets are set)
   - `SJP-Pulse-beta-0.X-source.zip` (Complete source code snapshot of the pushed commit)
   - GitHub's native `Source code (zip)` and `Source code (tar.gz)` archives.
5. **Tag & Release Publication**:
   - Pushes the annotated tag (`beta-0.X`) to your private repository and publishes a GitHub Pre-Release titled **`beta 0.X`** with all compiled APKs and source archives attached.

---

## 2. Quick Setup for Your Private Repository

### Step 1: Enable Workflow Write Permissions
In your private GitHub repository:
1. Go to **Settings → Actions → General**.
2. Scroll down to **Workflow permissions**.
3. Select **Read and write permissions** and click **Save** (this allows `GITHUB_TOKEN` to push tags `beta-0.1`, `beta-0.2`, etc., and upload release APK assets).

### Step 2: Push Your Code
Push your repository to `main` (or `master`):

```bash
git remote add origin git@github.com:<your-username>/<your-private-repo>.git
git branch -M main
git push -u origin main
```

Every subsequent `git push` to `main` will automatically build and publish the next sequential release (`beta 0.1` → `beta 0.2` → `beta 0.3`).

---

## 3. Optional: Signed Release APK Secrets

Out of the box, the workflow builds and attaches an installable `debug` APK without requiring any secrets. If you also want `SJP-Pulse-beta-0.X-release.apk` signed with your own upload key, add these three secrets under **Settings → Secrets and variables → Actions**:

| Secret Name | Value Description |
| :--- | :--- |
| `KEYSTORE_BASE64` | Base64-encoded string of your `.jks` keystore (`base64 -w 0 my-upload-key.jks`) |
| `STORE_PASSWORD` | Your keystore password |
| `KEY_PASSWORD` | Your key password for alias `upload` |

---

## 4. Skipping a Release on Minor Doc Edits (Optional)

If you push a commit and do **not** want the workflow to trigger a new `beta 0.X` tag, include `[skip ci]` in your commit message:

```bash
git commit -m "chore: update notes [skip ci]"
git push
```
