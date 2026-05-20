# Git & Branching Workflow

- **Branch Isolation (CRITICAL)**: NEVER work directly in the `master` branch. If the current branch is `master`, checkout a new branch immediately before making modifications.
- **Task Scoping**: If assigned a task unrelated to the current branch, create a new branch specific to that task.
- **Branch Naming (STRICT)**: Must strictly follow the `{type}/{primary-noun}` or `{type}/{primary-noun}-{secondary-noun}` format. Do not use verbs or Jira IDs.
  - **Allowed Types**:
    - `feat/`
    - `fix/`
    - `docs/`
    - `style/`
    - `refactor/`
    - `perf/`
    - `test/`
    - `build/`
    - `ci/`
    - `chore/`
    - `revert/`
- **Commit Frequency & Verification**: Commit each change or group related commits. Do not wait for the entire session to finish. Always check the diff before creating a commit.
- **Pull Requests (PR)**: PRs must be opened sequentially in the correct order. Always ask the user for permission before creating a PR.
