# Contributing to openflags

Thanks for your interest in contributing. This document covers the basics so
your first PR lands smoothly.

## Development setup

- JDK 21 (Temurin recommended)
- Maven 3.9+
- Git with GPG signing configured (commits to this repo must be signed)

```bash
git clone https://github.com/eliasss3990/openflags.git
cd openflags
mvn clean verify
```

To run the no-Micrometer smoke test profile (verifies the core works without
Micrometer on the classpath):

```bash
mvn -pl openflags-core -am -P no-micrometer test
```

## Workflow

1. Open an issue first for non-trivial changes so we can align on scope.
2. Fork the repo, create a feature branch off `main`:
   - `feat/<short-description>` for new features
   - `fix/<short-description>` for bug fixes
   - `chore/<short-description>` for tooling and infra
3. Make focused commits. One logical change per commit.
4. Open a PR against `main`. Fill in the PR template.
5. CI must be green. Code review is required before merge.
6. Maintainer merges using `--merge` (no squash) to preserve history.

## Commit message format

Conventional Commits, body in bullet form:

```
feat(core): add support for nested feature flag rollouts

- introduce RolloutEvaluator with percentage bucketing
- update EvaluationEvent javadoc to document the new resolved values
- add tests for cases where rollout config is missing
```

Allowed types: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`, `build`, `ci`.

Scope is the module name when applicable: `core`, `provider-remote`, `starter`,
etc. Omit the scope for repo-wide changes.

## Code conventions

- **Public Javadoc, code comments, log messages, and exception messages must be
  in English.** This is the language of the API users see.
- Conversation in PRs and issues can be in English or Spanish — pick one and
  stick to it within a thread.
- Format: 4-space indentation, LF line endings, UTF-8. The `.editorconfig`
  enforces this.
- No `// TODO` without an associated issue link.
- No commented-out code.

## Testing

- Unit tests live alongside the code they test (`src/test/java`).
- Tests should fail when the path they exercise is broken. If a correct test
  passes against a buggy implementation, the test is wrong — fix it.
- Coverage is reported by JaCoCo. We do not enforce a fixed coverage gate,
  but the trend should not regress.

## Releases

Maintainer-only. See `RELEASING.md`. Releases are tag-driven; pushing a `v*`
tag triggers signing and publishing to Maven Central.

## Reporting bugs and requesting features

Use the issue templates under "New issue". Security vulnerabilities go through
the private disclosure flow described in `SECURITY.md` — never as a public
issue.
