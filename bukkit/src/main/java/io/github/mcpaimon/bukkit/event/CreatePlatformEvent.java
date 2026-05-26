package io.github.mcpaimon.bukkit.event;

import io.github.mcpaimon.api.model.AIAccount;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when an AI account attempts to register a new platform.
 * This event is fired asynchronously.
 */
public class CreatePlatformEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private String cancelReason = "Action blocked by server policy or another plugin.";
    private final AIAccount account;
    private final String platformName;
    private final String url;

    /**
     * Constructs a new CreatePlatformEvent.
     *
     * @param account      The account attempting to create the platform.
     * @param platformName The display name of the new platform.
     * @param url          The base URL of the new platform.
     */
    public CreatePlatformEvent(AIAccount account, String platformName, String url) {
        super(true);
        this.account = account;
        this.platformName = platformName;
        this.url = url;
    }

    /**
     * Gets the account attempting to create the platform.
     *
     * @return The AIAccount instance.
     */
    public AIAccount getAccount() {
        return account;
    }

    /**
     * Gets the name of the platform being created.
     *
     * @return The platform name.
     */
    public String getPlatformName() {
        return platformName;
    }

    /**
     * Gets the URL of the platform being created.
     *
     * @return The platform URL.
     */
    public String getUrl() {
        return url;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /**
     * Gets the reason why this event was cancelled.
     *
     * @return The cancellation reason.
     */
    public String getCancelReason() {
        return cancelReason;
    }

    /**
     * Sets the reason why this event is cancelled.
     *
     * @param cancelReason The cancellation reason.
     */
    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
