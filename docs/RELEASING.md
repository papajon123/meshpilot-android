# Maintainer release procedure

The public application is `io.github.papajon123.meshcentral`. Debug builds use `.debug`.
The signing certificate is pinned in `ci/release-identity.json`; it is public information.
The encrypted private keystore and its password must remain outside this repository.
The private MeshPilot build has a different application ID and signing key; never reuse
either here.

1. Merge the reviewed change into `main` through a pull request with green required
   `Android checks`. Increase `versionCode` and `versionName` in `app/build.gradle.kts`,
   run the checks in [VALIDATION.md](VALIDATION.md), and review the changes. The tag must
   point to a commit that is an ancestor of `main`.
2. Update `.github/RELEASE_NOTES.md` and create a `vX.Y.Z` tag whose version matches the build.
3. The tag workflow reruns the JVM, lint and device checks, then builds and verifies the
   signed APK. The GitHub `release` environment is restricted to `v*` tags and contains
   `ANDROID_RELEASE_KEYSTORE_B64` and `ANDROID_RELEASE_PASSWORD`. Fork PRs do not receive them.
4. `gh release create --verify-tag --prerelease` publishes the verified APK, `SHA256SUMS`
   and `release-info.json` **directly as a prerelease**. There is no draft stage, so the
   workflow is the gate: do not replace the APK or edit release assets by hand.
5. Independently download the published APK and run
   `python ci/verify_apk.py path/to.apk --version X.Y.Z`. Compare its SHA-256 with
   `SHA256SUMS` and `release-info.json`, and install it on a dedicated test device. Record
   the evidence in the release issue.
6. Keep previews marked as prereleases until the documented compatibility checks pass.
   Mark a release as a full release only after that evidence exists.

The workflow checks that the tag belongs to `main` and rejects an unexpected package, version,
signing certificate, missing v2 signature or debug/test flags. A successful build alone is not
permission to change a signing identity or to claim unperformed device tests.

Keep at least one independent, encrypted recovery copy of the keystore **and** its password.
GitHub secrets are not a downloadable backup; Windows DPAPI files are tied to their Windows account.
Never commit recovery files, base64 keystores, passwords, full authentication logs or real device screenshots.
