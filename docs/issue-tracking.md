# Issue tracking

GitHub is the only tracker for MeshPilot.

- Public repository: `papajon123/meshpilot-android` — user-facing issues, pull requests,
  checks and releases.
- Internal work — the private build, signing, infrastructure and delivery details — is tracked
  privately and never appears in the public tracker. GitLab holds automatic reserve copies of
  the repositories for recovery; it is not a tracker.

## When an issue is required

Every independent task that changes or investigates the project — code, tests, docs, CI,
diagnostics or a release — starts with an issue. Search first: a continuation, clarification,
regression or repeated failure belongs to the existing issue. A question answered without work
does not need one.

## Public and private

A public issue is user-relevant and safe to read without an account: behaviour, versions,
synthetic reproduction steps and release notes. Keep out credentials, tokens, server
addresses, personal data, private repository links and internal delivery details.

Internal work, the private build, signing, infrastructure and anything that would expose
private details go to the private tracker.

## Lifecycle

1. Before work, find or create the issue and state the goal, scope and acceptance criteria.
2. Work on `mr/<issue>-<topic>`; open a pull request that links the issue.
3. Keep the required checks green; merge only after an independent review.
4. Before closing, add a substantive result: changes, PR/SHA, checks and evidence, release,
   remaining work and next step.
5. Close after verification. Close an application change only after delivery.

The maintainer authorizes merges, signing and publication; an authorized agent may perform
them on the maintainer's behalf after the review and green checks.
