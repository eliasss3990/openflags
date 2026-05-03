# Upgrade Guide

This document captures the steps required to upgrade between minor and major
versions of openflags. For the full change history see [CHANGELOG.md](../CHANGELOG.md).

## Versioning policy

openflags follows [Semantic Versioning](https://semver.org/):

- **Major** (`x.0.0`): breaking changes to public API, public metric/tag names,
  MDC keys, or default behavior that requires user action.
- **Minor** (`1.x.0`): backwards-compatible additions — new APIs, new providers,
  new metrics, new properties with safe defaults.
- **Patch** (`1.0.x`): bug fixes only. No API or behavior change.

Anything under a package or class annotated `@Internal` is excluded from
this contract.

## Public surface covered by SemVer

- Types in the documented public packages of:
  - `com.openflags.core` (excluding `*.internal`)
  - `com.openflags.provider.file`
  - `com.openflags.provider.remote`
  - `com.openflags.provider.hybrid`
  - `com.openflags.spring`
  - `com.openflags.testing`
- Names and tag keys in `OpenFlagsMetrics`
- MDC keys in `OpenFlagsMdc`
- Spring Boot configuration properties under `openflags.*`
- Defaults documented in the README and the autoconfig metadata

## From `0.x` to `1.0.0`

`0.x` releases were unstable previews and are no longer supported. Direct
upgrades are not supported; treat 1.0.0 as a fresh adoption.

The Maven coordinates changed from `com.openflags` to
`io.github.eliasss3990` at 1.0.0:

```xml
<!-- before, 0.x -->
<dependency>
  <groupId>com.openflags</groupId>
  <artifactId>openflags-core</artifactId>
  <version>0.5.0</version>
</dependency>

<!-- after, 1.0.0 -->
<dependency>
  <groupId>io.github.eliasss3990</groupId>
  <artifactId>openflags-core</artifactId>
  <version>1.0.0</version>
</dependency>
```

Java package names (`com.openflags.*`) did **not** change — your imports stay
the same.

## Future releases

When upgrading between published 1.x versions, follow this order:

1. Read the relevant section in [CHANGELOG.md](../CHANGELOG.md) — every entry
   under `Breaking changes` lists the migration step.
2. Bump the version in your `pom.xml` (use the BOM at `openflags-bom` to keep
   modules aligned).
3. Run `mvn dependency:tree` to confirm transitive versions resolved as expected.
4. Re-run your test suite. The metric and MDC names are stable, so dashboards
   and alerts should not need changes within a major.
5. If you customize providers, check whether new defaults (e.g. timeouts,
   poll intervals) changed in this release — these are documented under
   `Changed` in the CHANGELOG.

## Reporting upgrade issues

If a "non-breaking" upgrade breaks your code, that is a bug — open an issue
with the previous and new version numbers and the failure mode.
