# Security Policy

## Reporting a vulnerability

Please **do not** open a public issue for security vulnerabilities. Instead, use
GitHub's private vulnerability reporting (the "Report a vulnerability" button under
the repository's **Security** tab), or open a minimal private channel with the
maintainers.

Because SkipperKit holds an accessibility service (broad read access to on-screen
content for its scoped apps) and can dispatch taps, the areas of most interest are:

- anything that could widen the service's package scope without explicit user action,
- the remote-config fetch path (HTTPS-only, bounded, falls back to bundled),
- any path that could cause an unintended tap (the engine clicks; discovery and
  Teach Mode only *propose* and require user confirmation),
- the one-tap contribution path — both the on-device side (`ContributionPort`
  builds a payload of skip plus taught custom buttons; `ContributionSender` POSTs
  over HTTPS only; nothing about what the user watches leaves the device) and the
  ingestion side (validation, risky-word filter, custom buttons land in PRs
  disabled-by-default, human merge required to publish).

## Scope reminder

SkipperKit reads only the accessibility node tree of its scoped apps (the built-ins
plus any app the user explicitly adds) and never captures the screen, runs OCR, or
analyzes media. Reports that depend on disabling these guarantees are out of scope.

We aim to acknowledge reports within a few days.
