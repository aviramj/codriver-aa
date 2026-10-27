# Automated releases to Google Play

New builds publish to the **Internal testing** track automatically via GitHub
Actions (`.github/workflows/release.yml`). After a **one-time setup**, a release
is just a tag push — no manual AAB upload.

## One-time setup (you do this once)

Needs your Google credentials, so only you can do it.

### 1. Create a Play service account (lets CI upload on your behalf)
1. Google Play Console → **Setup ▸ API access**.
2. Follow **Create new service account** → it sends you to Google Cloud.
3. In Google Cloud: create the service account, then create a **JSON key** and download it.
4. Back in Play Console ▸ **Users & permissions ▸ Invite new users**, invite the
   service account's email, and grant it (app `com.aviramj.codriver`):
   **Release to testing tracks** (and **View app information**). Save.

   Docs: <https://developers.google.com/android-publisher/getting_started>

### 2. Base64 your upload keystore (for CI signing)
```bash
# Linux:
base64 -w0 upload-keystore.jks
# macOS:
base64 -i upload-keystore.jks | tr -d '\n'
```
Copy the one-line output.

### 3. Add the repo secrets
GitHub → repo **Settings ▸ Secrets and variables ▸ Actions ▸ New repository secret**:

| Secret | Value |
|--------|-------|
| `PLAY_SERVICE_ACCOUNT_JSON` | the full contents of the service-account JSON file |
| `KEYSTORE_BASE64` | the base64 string from step 2 |
| `KEYSTORE_PASSWORD` | your upload-keystore password |
| `KEY_ALIAS` | `upload` |
| `KEY_PASSWORD` | your upload-keystore password |

## Cutting a release (every time after that)
Either:
- **Push a tag:** `git tag v1.3 && git push origin v1.3`, or
- **Manual:** GitHub → **Actions ▸ Release to Google Play (internal) ▸ Run workflow**.

CI then builds the signed AAB and uploads it to **Internal testing**. On your
phone, open the Play listing and tap **Update**.

- **versionCode** auto-increments (GitHub run number + 1000), so you never hit a
  "version already used" error. If you ever upload manually again, use a code
  above the last CI one.
- **versionName** comes from the tag (`v1.3` → `1.3`) or the manual input.

## Notes / troubleshooting
- First automated run: if Play reports "changes not sent for review," open
  Publishing overview once and send them; subsequent internal-testing uploads
  publish without that.
- The service account only needs testing-track permission — it can't touch
  production.
- Keep `upload-keystore.jks` + its password backed up; the secrets above are
  derived from them.

---

## The workflow file (reference)

If the `.github/workflows/release.yml` file isn't in the repo (a GitHub App may
be blocked from pushing workflow files), create it yourself with this content:

```yaml
name: Release to Google Play (internal)

on:
  workflow_dispatch:
    inputs:
      versionName:
        description: "Version name (e.g. 1.3). Optional."
        required: false
        default: ""
  push:
    tags:
      - "v*"

permissions:
  contents: read

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
      - uses: android-actions/setup-android@v3
      - name: Install SDK packages
        run: yes | sdkmanager "platforms;android-36" "build-tools;36.0.0" >/dev/null || true
      - name: Decode upload keystore
        run: echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > upload-keystore.jks
      - name: Write keystore.properties
        run: |
          {
            echo "storeFile=upload-keystore.jks"
            echo "storePassword=${{ secrets.KEYSTORE_PASSWORD }}"
            echo "keyAlias=${{ secrets.KEY_ALIAS }}"
            echo "keyPassword=${{ secrets.KEY_PASSWORD }}"
          } > keystore.properties
      - name: Resolve version name
        id: ver
        run: |
          NAME="${{ github.event.inputs.versionName }}"
          if [ -z "$NAME" ] && [ "$GITHUB_REF_TYPE" = "tag" ]; then
            NAME="${GITHUB_REF_NAME#v}"
          fi
          [ -z "$NAME" ] && NAME="1.2"
          echo "name=$NAME" >> "$GITHUB_OUTPUT"
      - name: Build signed AAB
        run: ./gradlew bundleRelease --no-daemon
        env:
          VERSION_CODE: ${{ github.run_number + 1000 }}
          VERSION_NAME: ${{ steps.ver.outputs.name }}
      - name: Upload to Play (internal testing)
        uses: r0adkll/upload-google-play@v1
        with:
          serviceAccountJsonPlainText: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}
          packageName: com.aviramj.codriver
          releaseFiles: app/build/outputs/bundle/release/app-release.aab
          track: internal
          status: completed
```
