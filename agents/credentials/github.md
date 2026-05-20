# Credentials & Security

The system resolves credentials using the following priority:
**GitHub Actions Runner** → **Agent Specific** → **User Specific**

### Credential Structure

#### Username
```groovy
USERNAME = System.getenv('GITHUB_ACTOR')       // Runner context
        ?: System.getenv('AGENT_GITHUB_NAME')  // Agent specific
        ?: System.getenv('USER_GITHUB_NAME')   // User specific
```

#### Password / Token
```groovy
PASSWORD = System.getenv('GITHUB_TOKEN')        // Runner context
        ?: System.getenv('AGENT_GITHUB_TOKEN')  // Agent specific
        ?: System.getenv('USER_GITHUB_TOKEN')   // User specific
```
