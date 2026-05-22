# MCPaimon - Minecraft AI Plugin

MCPaimon is an advanced AI integration plugin for Minecraft servers (PaperMC). It allows players to seamlessly interact with Large Language Models (LLMs) directly in-game. With a powerful built-in tool execution system, developers can easily expand the AI's capabilities to interact with the Minecraft world.

## Features

- **In-Game AI Chat**: Toggle an interactive AI chat session directly in the Minecraft chat seamlessly.
- **Universal AI API Support**: Connects to any AI provider that supports the standard OpenAI JSON schema (e.g., DeepSeek, OpenAI, etc.).
- **Multiple Database Backends**: Robust asynchronous data storage supporting **SQLite**, **MySQL**, and **PostgreSQL** for storing accounts, active sessions, and chat logs.
- **Dynamic Tool Calling**: Empowers the AI to execute multi-tool parallel calls to interact with server-side mechanics.
- **Extension System**: Modular design allowing external plugins to register custom AI tools effortlessly.

## Commands

- `/ai chat` - Toggle your personal AI chat mode on or off.
- `/ai active set <platform> <model>` - Set the active AI model for your current session.
- `/ai token set <platform> <token>` - Securely register your API token for a specific platform.
- `/ai help` - Display the command help menu.

## Configuration

The plugin generates a `config.yml` where you can set up databases and define your AI platforms:

```yaml
enable: true

database:
  type: "sqlite" # Options: sqlite, postgresql, mysql
  sqlite:
    file: "mcai.db"

platforms:
  # Format: name,url,model1,model2...
  - deepseek,[https://api.deepseek.com](https://api.deepseek.com),deepseek-chat
```

## Creating Extensions (Custom AI Tools)

Developers can extend MCPaimon by creating custom extensions.  
By implementing the `AITool` interface, you can register new functions that the AI can call automatically during conversations (e.g., fetching player UUIDs, managing server economy, or spawning entities).

An example extension template is available at: [https://github.com/MCPaimon-Extension/template](https://github.com/MCPaimon-Extension/template)

## Architecture

- `api`: Contains all the core interfaces (IAIDatabase, AITool) and data models.
- `common`: Platform-independent logic, HTTP clients (MCAIAPIClient), and database implementations.
- `bukkit`: Core event structures for Bukkit-based servers.
- `platforms/papermc`: PaperMC specific implementations, listeners, and command executors.

## License

Copyright (c) 2026 MCPaimon. All rights reserved.
Please see the [LICENSE](https://github.com/MCPaimon/plugin/blob/master/LICENSE) file for more details regarding usage and distribution.







