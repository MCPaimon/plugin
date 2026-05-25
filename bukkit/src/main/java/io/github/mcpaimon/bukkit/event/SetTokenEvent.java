package io.github.mcpaimon.bukkit.event;

import io.github.mcpaimon.api.model.AIAccount;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when an AI account attempts to set an API token.
 * This event is fired asynchronously.
 */
public class SetTokenEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final AIAccount executorAccount;
    private final String targetAccountType;
    private final String targetAccountUuid;
    private final String platformName;
    private final String token;

    /**
     * Constructs a new SetTokenEvent.
     *
     * @param executorAccount   The account executing the tool.
     * @param targetAccountType The account type being modified.
     * @param targetAccountUuid The UUID of the account being modified.
     * @param platformName      The name of the platform, or null if setting the active session's token.
     * @param token             The token being set.
     */
    public SetTokenEvent(AIAccount executorAccount, String targetAccountType, String targetAccountUuid, String platformName, String token) {
        super(true);
        this.executorAccount = executorAccount;
        this.targetAccountType = targetAccountType;
        this.targetAccountUuid = targetAccountUuid;
        this.platformName = platformName;
        this.token = token;
    }

    /**
     * Gets the account attempting to set the token.
     *
     * @return The AIAccount instance of the executor.
     */
    public AIAccount getExecutorAccount() {
        return executorAccount;
    }

    /**
     * Gets the target account type being modified (e.g., player, clan).
     *
     * @return The target account type.
     */
    public String getTargetAccountType() {
        return targetAccountType;
    }

    /**
     * Gets the target account UUID being modified.
     *
     * @return The target account UUID.
     */
    public String getTargetAccountUuid() {
        return targetAccountUuid;
    }

    /**
     * Gets the name of the platform for which the token is being set.
     *
     * @return The platform name, or null if applying to the active session.
     */
    public String getPlatformName() {
        return platformName;
    }

    /**
     * Gets the token that will be set.
     *
     * @return The API token.
     */
    public String getToken() {
        return token;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
