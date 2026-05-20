# PaperMC Concurrency & Standards

- Utilize the standard Paper scheduling API for task management and scheduling.
- Ensure all heavy computations, API calls, and database interactions run asynchronously off the main server thread.
- ALWAYS use `Component.translatable()` for player messages via the Adventure API.
- Synchronize with the main thread only when interacting with thread-unsafe Bukkit/Paper API methods.
