# GitLab reserve

GitHub is the only development and release place for MeshPilot. GitLab stores automatic
reserve copies of the repositories and is not a source of changes.

## Current policy

- Canonical: `papajon123/meshpilot-android` for the public app. The private build and internal
  work are tracked in the private project tracker. Issues, pull requests, required checks,
  tags and releases live on GitHub.
- Reserve: GitLab is updated automatically from GitHub. Treat it as read-only for normal
  work and as a recovery copy, not as a second development line.
- Do not open merge requests, branches, tags or releases in GitLab. Do not develop, review
  or merge there.
- Do not copy GitLab backup history, credentials or mirror configuration into a project
  repository. The public repository never contains private build details.

## If GitHub is unavailable

The maintainer decides whether the reserve is needed and how to use it. Until that
decision, wait or continue locally; do not start a parallel GitLab development line.

## History

Before GitHub became primary, the project developed in a private GitLab repository and
published reviewed source snapshots to GitHub. That bridge is retired. Old tags and
release notes remain historical records, not the current process.
