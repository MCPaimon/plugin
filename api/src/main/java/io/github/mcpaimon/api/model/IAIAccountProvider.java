package io.github.mcpaimon.api.provider;

import io.github.mcpaimon.api.model.AIAccount;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for providing AI account information from different sources (Player, Console, Clans, etc.).
 */
public interface IAIAccountProvider {
    /**
     * Retrieves the account associated with the sender.
     * @return A future containing the account information.
     */
    CompletableFuture<AIAccount> getAccount();

    /**
     * Gets the identifier string for this provider type (e.g., "player", "console").
     */
    String getProviderType();
}
