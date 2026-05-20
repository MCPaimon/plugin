# Versioning Specification & CI/CD

## Release & CI Versioning

### Format Architecture

- **CI Builds**:
  ```text
  2026.m.m-build.${build number}
  ```

- **Stable Tests**:
  ```text
  2026.m.m-{iteration 1...n}
  ```

- **Releases**:
  ```text
  2026.m.m
  ```

### Timeline

Releases update iteratively per month (e.g., `2026.1.0` in Oct, `2026.1.1` in Nov). In December, prepare the New Year format.

### Iteration Workflow

The first commit after creating a branch MUST bump the `project-iteration` by `1` using the formula:

```text
{current iteration} + 1
```

in `gradle.properties`.
