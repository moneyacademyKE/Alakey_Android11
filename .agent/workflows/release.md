---
description: Build and publish a signed Android release from CI
---

# Workflow: Signed GitHub Release

1. Update `versionName` and monotonically increment `versionCode` in `app/build.gradle.kts`.
2. Verify locally with the gitignored `keystore.properties`:

```bash
./gradlew testDebugUnitTest lintRelease assembleRelease
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

3. Merge through a PR. Create and push a `v<versionName>` tag only after CI is green.
4. GitHub Actions decodes the secret-backed keystore, checks tag/version equality, builds and verifies the signed APK, and publishes the APK plus SHA-256 file.

Never use `git add .`, push directly to `master`, publish a debug APK, or commit signing material.
