<!-- Public pull request. Keep credentials, server addresses and personal data out. -->
## Issue

Closes #

## Behaviour

<!-- What changes for the user or the protocol, and why. Note affected distributions (direct/Play). -->

## Checks

- [ ] `./gradlew testDirectDebugUnitTest testPlayDebugUnitTest lintDirectDebug lintPlayDebug assembleDirectDebug assemblePlayDebug bundlePlayRelease`
- [ ] Required `Android checks` are green for the pull request and for `main`
- [ ] UI change: `connectedDirectDebugAndroidTest`; Play contract: `connectedPlayDebugAndroidTest` on a dedicated emulator
- [ ] Evidence is recorded in the linked issue (commands, results, artifacts)

## Delivery

<!-- Version bump, unchanged application ID and signer, release notes, Play bundle, and
     remaining acceptance. Delete this section when the change is not a release. -->
