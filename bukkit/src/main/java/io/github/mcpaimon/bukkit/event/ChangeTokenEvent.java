package io.github.mcpaimon.bukkit.event;

import io.github.mcpaimon.api.model.AIAccount;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when an AI account attempts to change an API token.
 * This event is fired asynchronously.
 */
public class ChangeTokenEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private String cancelReason = "Action blocked by server policy or another plugin.";
    private final AIAccount executorAccount;
    private final String targetAccountType;
    private final String targetAccountUuid;
    private final String platformName;
    private final String newToken;

    /**
     * Constructs a new ChangeTokenEvent.
     *
     * @param executorAccount   The account executing the tool.
     * @param targetAccountType The account type being modified.
     * @param targetAccountUuid The UUID of the account being modified.
     * @param platformName      The name of the platform, or null if changing the active session's token.
     * @param newToken          The new token being set.
     */
    public ChangeTokenEvent(AIAccount executorAccount, String targetAccountType, String targetAccountUuid, String platformName, String newToken) {
        super(true);
        this.executorAccount = executorAccount;
        this.targetAccountType = targetAccountType;
        this.targetAccountUuid = targetAccountUuid;
        this.platformName = platformName;
        this.newToken = newToken;
    }

    /**
     * Gets the account attempting to change the token.
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
