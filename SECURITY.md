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
- any path that could cause an unintended tap (the engine clicks; discovery only
  *proposes* and requires user confirmation).

## Scope reminder

SkipperKit reads only the accessibility node tree of its scoped apps (the built-ins
plus any app the user explicitly adds) and never captures the screen, runs OCR, or
analyzes media. Reports that depend on disabling these guarantees are out of scope.

We aim to acknowledge reports within a few days.
