# Releasing openflags

Releases are **tag-driven**. Pushing a tag matching `v*` triggers
`.github/workflows/release.yml`, which signs artifacts with GPG and publishes
to Maven Central via `central-publishing-maven-plugin`.

`main` is **not** a release branch — every push to `main` runs the full CI
matrix plus a signing dry-run, but **does not publish**. Between releases,
`main` carries a `-SNAPSHOT` version (e.g. `1.1.0-SNAPSHOT`); the only
commits where the POM holds a non-snapshot version are the release-prep
merge and the immediately following snapshot-bump merge.

## Required GitHub repository configuration

### Secrets (Settings → Secrets and variables → Actions)

| Name                       | Purpose                                                  |
|----------------------------|----------------------------------------------------------|
| `MAVEN_GPG_PRIVATE_KEY`    | ASCII-armored private key used to sign artifacts          |
| `MAVEN_GPG_PASSPHRASE`     | Passphrase for the GPG key                                |
| `CENTRAL_USERNAME`         | Sonatype Central portal username (or token name)          |
| `CENTRAL_PASSWORD`         | Sonatype Central portal password (or token value)         |

The signing public key must be published to `keys.openpgp.org` (or another
key server reachable from Maven Central's verification step).

SonarCloud analysis is run via the SonarCloud GitHub App (not a workflow),
so no `SONAR_TOKEN` secret is required in GitHub. The token lives on
SonarCloud's side; ensure the project is linked to this repo in
sonarcloud.io.

### Branch protection (Settings → Branches → main)

Configure `main` with:

- Require a pull request before merging
  - Require approvals: 1
  - Restrict who can dismiss reviews / push: only repo admin
- Require status checks to pass before merging
  - **Required checks:**
    - `Build & Test (Java 21)`
    - `No-Micrometer smoke (core)`
    - `Javadoc`
    - `All checks passed`
    - `SonarCloud Code Analysis`
- Require branches to be up to date before merging
- Do not allow bypassing the above settings
- Restrict pushes that create matching branches: only repo admin

`SonarCloud Code Analysis` is reported via the SonarCloud GitHub App, not via
a workflow file, so it must be added to required checks **after** at least one
PR has surfaced it.

## Cutting a release

1. Open a PR from a branch (typically `release/prep-x.y.z`) that:
   - Bumps version with `mvn versions:set -DnewVersion=x.y.z -DgenerateBackupPoms=false -DprocessAllModules=true`
   - Promotes the `[Unreleased]` section in `CHANGELOG.md` to `[x.y.z] - YYYY-MM-DD`
   - Updates the link references at the bottom of `CHANGELOG.md`
2. Run code-reviewer + ensure CI green.
3. Merge the PR with `--merge` (never `--squash`) and `--delete-branch`.
4. Tag the merge commit on `main`:

   ```
   git checkout main && git pull
   git tag -a vx.y.z -m "Release x.y.z"
   git push origin vx.y.z
   ```

5. The `Release` workflow runs: signs, publishes to Central, and creates a
   GitHub Release with auto-generated notes.
6. Verify the artifacts appear on Maven Central
   (<https://repo1.maven.org/maven2/com/openflags/>) — propagation takes
   30 min to 4 h.
7. Open a follow-up PR bumping the POM to the next `-SNAPSHOT`
   (e.g. `x.y.(z+1)-SNAPSHOT`) and adding a fresh `[Unreleased]` section
   to `CHANGELOG.md`.
