package io.github.mcpaimon.bukkit.event;

import io.github.mcpaimon.api.model.AIAccount;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when an AI account attempts to change its API token.
 * This event is fired asynchronously.
 */
public class ChangeTokenEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final AIAccount account;
    private final String platformName;
    private final String newToken;

    /**
     * Constructs a new ChangeTokenEvent.
     *
     * @param account      The account attempting to change the token.
     * @param platformName The name of the platform, or null if changing the active session's token.
     * @param newToken     The new token being set.
     */
    public ChangeTokenEvent(AIAccount account, String platformName, String newToken) {
        super(true);
        this.account = account;
        this.platformName = platformName;
        this.newToken = newToken;
    }

    /**
     * Gets the account attempting to change the token.
     *
     * @return The AIAccount instance.
     */
    public AIAccount getAccount() {
        return account;
    }

    /**
     * Gets the name of the platform for which the token is being changed.
     *
     * @return The platform name, or null if applying to the active session.
     */
    public String getPlatformName() {
        return platformName;
    }

    /**
     * Gets the new token that will be set.
     *
     * @return The new API token.
     */
    public String getNewToken() {
        return newToken;
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
