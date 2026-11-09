# Google Play preparation

The Google Play application uses `io.github.papajon123.meshcentral`. Preserve the existing public
app-signing identity when enrolling in Play App Signing; the upload key and app-signing key are
different roles. No signing key is kept here. `app-details.json` contains the owner's public
contact, `papajon1234@gmail.com`, and the default language. Listing text is in the two locale JSON files.

Upload-ready graphics are in `assets/`: shared icon/feature graphic and four native screenshots per language, with accessible descriptions. See `assets/README.md` for their capture and format checks.

## Build and verify

`direct` retains GitHub update checks and the optional support link. `play` disables both, including
when preferences were retained from a direct installation. The Play policy is available offline,
before sign-in and in Settings. It is generated from the same HTML used for the public policy.

```text
python ci/prepare_play_assets.py
python ci/check_translations.py
./gradlew testDirectDebugUnitTest testPlayDebugUnitTest lintDirectDebug lintPlayDebug assembleDirectDebug assemblePlayDebug bundlePlayRelease
python ci/download_bundletool.py
python ci/verify_play_bundle.py app/build/outputs/bundle/playRelease/app-play-release.aab --bundletool .tools/bundletool-all-1.18.3.jar --version 0.12.6 --code 48 --output verification/play-bundle-report.json
```

Use `gradlew.bat` on Windows and create the `verification` directory first. Local bundles without
release credentials are unsigned; they are review artifacts, not upload-ready signed releases.
The protected release workflow signs both distributions, verifies the bundle with the expected
public certificate, and retains the AAB and its report as the `google-play-bundle` CI artifact.
The verifier checks package/version, permissions, backup/debug flags, signing, and 16 KiB native
ELF alignment. Debug-signing test fixtures must never be uploaded as releases.

## Console and policy hosting

The free MeshPilot app exists in Play Console under the public package with Play App Signing, and closed
testing (Alpha) has been active since 2026-11-07. A production release needs 12 testers who stay opted in
for 14 days ([how testers join](../../docs/PLAY_TESTING.md)). Keep the content declarations in line with
`data-safety-draft.md` and the actual signed artifact.

For the public policy, enable GitHub Pages with **GitHub Actions** as its source, then manually run
**Publish privacy policy** on `main`. It is deliberately not published from a feature branch or
automatically during development. The published URL is
`https://papajon123.github.io/meshpilot-android/privacy.html` (Play Console updated on 2026-11-09).
GitHub Pages does not redirect after a repository rename: rename → republish → verify the anonymous
HTTPS response → update Play Console in the same step.

`reviewer-instructions.md` lists the isolated access required for Google review. Never supply an
operator's personal computers or credentials. Public demo hosting and its credentials must be
configured and verified before submission. Tester recruitment runs through the `meshpilot-testers`
Google group and [docs/PLAY_TESTING.md](../../docs/PLAY_TESTING.md); these scripts perform no outreach.
