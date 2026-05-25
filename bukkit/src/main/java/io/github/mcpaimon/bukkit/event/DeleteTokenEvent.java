package io.github.mcpaimon.bukkit.event;

import io.github.mcpaimon.api.model.AIAccount;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when an AI account attempts to delete its API token.
 * This event is fired asynchronously.
 */
public class DeleteTokenEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final AIAccount account;
    private final String platformName;

    /**
     * Constructs a new DeleteTokenEvent.
     *
     * @param account      The account attempting to delete the token.
     * @param platformName The name of the platform, or null if deleting the active session's token.
     */
    public DeleteTokenEvent(AIAccount account, String platformName) {
        super(true);
        this.account = account;
        this.platformName = platformName;
    }

    /**
     * Gets the account attempting to delete the token.
     *
     * @return The AIAccount instance.
     */
    public AIAccount getAccount() {
        return account;
    }

    /**
     * Gets the name of the platform for which the token is being deleted.
     *
     * @return The platform name, or null if applying to the active session.
     */
    public String getPlatformName() {
        return platformName;
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
