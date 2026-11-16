# Project Rules

This repository is a native Android client for controlling MeshCentral from Android.

## Build

- Use PowerShell on Windows.
- Use JDK 17 or newer and Android SDK platform 37.0 with build-tools 36.0.0; APK verification also uses 35.0.0. Set `JAVA_HOME` and `ANDROID_HOME` to your installations.
- Validate both distributions with `./gradlew testDirectDebugUnitTest testPlayDebugUnitTest lintDirectDebug lintPlayDebug assembleDirectDebug assemblePlayDebug bundlePlayRelease`. Run `connectedDirectDebugAndroidTest` for UI changes and the Play distribution contract through `connectedPlayDebugAndroidTest` on a dedicated emulator.

## Workflow

- GitHub `papajon123/meshpilot-android` is canonical for development and release: issues, pull requests, required checks, tags and releases. GitLab is an automatic reserve with no new development.
- Work on `mr/<issue>-<topic>` and open a pull request that links its issue. The `Android checks` workflow must pass.
- The maintainer authorizes merges, signing and publication. An authorized agent may merge after an independent review and green checks, then sign and publish on the maintainer's behalf. Never merge on a red or missing check.

## Tasks

- Every independent development, diagnostics, documentation or release task starts with an issue in the project tracker. Search first, then create; clarifications, continuations and repeated failures go into the existing issue. A question answered without work needs no issue.
- Keep the goal and acceptance criteria in the issue, and add the substantive result with evidence before closing: changes, PR/SHA, checks, release, remaining work and next step. Close application changes only after delivery.
- Public issues contain user-relevant facts and synthetic data only: no credentials, server addresses, personal data or private build internals. Internal work is tracked privately. Never move another project's tracker to GitHub.
- Process details: [docs/issue-tracking.md](docs/issue-tracking.md).

## Release identity

- The public application ID is `io.github.papajon123.meshcentral`; its signing certificate is pinned in `ci/release-identity.json`. The private build has a different application ID and signing key. Never reuse, mix or publish private signing material here.
- A release is a `vX.Y.Z` tag on `main`; the tag workflow verifies it and publishes the signed APK as a prerelease directly, with no draft stage. See [docs/RELEASING.md](docs/RELEASING.md).

## Security

- Do not commit credentials, tokens, passwords, keystores, `local.properties`, APK/AAB outputs, or generated private files.
- Credentials and session material must be stored only through Android Keystore-backed storage.
- Do not log credentials, cookies, PINs, session IDs, tokens, or full WebView URLs that may contain secrets.

## MeshCentral Integration

- This app is a mobile control client, not a MeshCentral agent.
- Users provide their own HTTPS MeshCentral endpoint. Use reserved example domains and synthetic device data in tests and documentation.
- Do not invent MeshCentral APIs. Use WebView fallback unless a native API/WebSocket path has been audited.
- The public client does not administer or deploy MeshCentral servers.

## Language

- User-facing project docs can be English for future open-source readiness.
- Chat with the user in Russian unless the user explicitly asks otherwise.
