# Architecture & Technical Standards

## Project Structure

Maintain the sub-module architecture:

- `api`
- `common`
- `bukkit`
- `platforms`

Additional rules:

- `api` and `common` must ALWAYS be pure Java and use `CompletableFuture`.
- `api` submodule must contain ONLY interfaces.
- `common` submodule is strictly for getting and setting data from the Database.
- `bukkit` submodule is dedicated to handling core event structures and API interactions specific to the Bukkit platform.
- `platforms` submodule is dedicated to handling platform-specific implementations and integrations.
