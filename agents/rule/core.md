# Core Directives & Local State Management

- **Mandatory Instruction Retrieval**: Every session or major task MUST start by retrieving and reading the latest instructions from this file and related files inside the repository. Do not rely on internal training data or hardcoded assumptions.
- **Local State Directory (`.agents/`)**: Store all active work, session context, and task summaries in the local `.agents/` directory.
  - **Gitignore Verification**: Verify that `.agents/` is present in the root `.gitignore` file. If not, write it immediately to prevent accidental commits.
  - **Central Index**: Maintain `.agents/README.md` as the routing hub with brief descriptions of all specific files. Always read this file first to locate the context of a new session.
  - **File Allocation**: Create specific `.md` files for related topics (e.g., `.agents/task-database.md`, `.agents/bug-reports.md`). Do not dump all information into a single file. Read only the specific files relevant to the current objective.
