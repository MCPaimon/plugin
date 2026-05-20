# Commit Message Conventions (STRICT)

Commits MUST follow the Conventional Commits specification.

## Structure

```text
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

## Types

- `fix:` — Patches a bug (PATCH).
- `feat:` — Introduces a new feature (MINOR).
- `BREAKING CHANGE:` — Introduces a breaking API change (MAJOR).
- Other supported types:
  - `build:`
  - `chore:`
  - `ci:`
  - `docs:`
  - `style:`
  - `refactor:`
  - `perf:`
  - `test:`

## Examples

```text
feat(api)!: send an email to the customer when a product is shipped
```

```text
fix: prevent racing of requests
```
