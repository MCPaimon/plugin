package io.github.mcpaimon.api.provider;

/**
 * Interface for providing AI account information dynamically from different sources (Player, Console, Clans, etc.).
 * Implement this in different plugins to easily interact with MCAI.
 */
public interface IAIAccountProvider {
    
    /**
     * Gets the account type (e.g., "player", "console", "clan").
     * * @return The account type string.
     */
    String getAccountType();

    /**
     * Gets the unique identifier for this account type.
     * * @return The account UUID string.
     */
    String getAccountUuid();
}
