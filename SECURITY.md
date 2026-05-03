# Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 1.x     | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

**Do not open a public issue for security vulnerabilities.**

Use GitHub's private vulnerability reporting:

1. Go to <https://github.com/eliasss3990/openflags/security/advisories/new>
2. Provide a clear description, reproduction steps, and any proof-of-concept code
3. Indicate the affected version(s) and the impact you observed

You will receive an acknowledgment within 72 hours. If the report is confirmed,
we coordinate a fix and a disclosure timeline with you (target: patch released
within 30 days for high/critical severity).

If GitHub private reporting is not available to you, email
`elias.gonzalez@ncoders.solutions` with `[openflags-security]` in the subject.

## Scope

In scope:

- Code in this repository (all `openflags-*` modules)
- Default configuration shipped by the Spring Boot starter
- Maven Central artifacts published under `io.github.eliasss3990`

Out of scope:

- Vulnerabilities in third-party dependencies (please report them upstream and
  we will pick up the fix on the next release cycle)
- Issues that require non-default configuration combined with attacker-controlled
  input the application willingly accepts
