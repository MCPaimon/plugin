# MCPaimon - Minecraft AI Framework

MCPaimon is a powerful AI integration framework for Minecraft servers (PaperMC). It provides a robust backend for connecting Large Language Models (LLMs) to the Minecraft environment, allowing developers to create advanced AI-driven experiences through a modular extension system.

## Features

- **Universal AI API Support**: Seamlessly connects to any AI provider supporting the standard OpenAI JSON schema (e.g., DeepSeek, OpenAI, etc.).
- **Robust Database Backends**: Support for **SQLite**, **MySQL**, and **PostgreSQL** for persistent storage of AI configurations and data.
- **Dynamic Tool Calling**: Core logic for multi-tool parallel execution, enabling AI models to interact with server mechanics.
- **Modular Extension System**: A flexible architecture that allows external plugins to register custom AI tools and handle AI interactions.

## Architecture

- `api`: Core interfaces and data models (`IAIDatabase`, `AITool`).
- `common`: Platform-independent logic, including database implementations and the core `MCAIManager`.
- `platforms/papermc`: The PaperMC implementation that hosts the AI provider and manages extensions.

## Getting Started

MCPaimon acts as a service provider. To interact with the AI in-game, you should install extensions that implement specific features such as chat listeners or custom commands.

### Configuration

The plugin generates a `config.yml` to define your AI platforms and database settings:

```yaml
enable: true

database:
  type: "sqlite" # Options: sqlite, postgresql, mysql
  sqlite:
    file: "mcai.db"

platforms:
  # Format: name,url,model1,model2...
  - deepseek,https://api.deepseek.com,deepseek-chat
```

## Creating Extensions

Developers can extend MCPaimon by creating custom extensions. By implementing the `AITool` interface, you can register new functions that the AI can call automatically.

An example extension template is available at: [https://github.com/MCPaimon-Extension/template](https://github.com/MCPaimon-Extension/template)

## License

Copyright (c) 2026 MCPaimon. All rights reserved.
Please see the [LICENSE](https://github.com/MCPaimon/plugin/blob/master/LICENSE) file for more details regarding usage and distribution.
