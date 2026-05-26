package io.github.mcpaimon.bukkit.event;

import io.github.mcpaimon.api.model.AIAccount;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired when an AI account attempts to register a new model.
 * This event is fired asynchronously.
 */
public class CreateModelEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private String cancelReason = "Action blocked by server policy or another plugin.";
    private final AIAccount account;
    private final String platformName;
    private final String modelId;

    /**
     * Constructs a new CreateModelEvent.
     *
     * @param account      The account attempting to create the model.
     * @param platformName The name of the platform the model belongs to.
     * @param modelId      The ID of the new model.
     */
    public CreateModelEvent(AIAccount account, String platformName, String modelId) {
        super(true);
        this.account = account;
        this.platformName = platformName;
        this.modelId = modelId;
    }

    /**
     * Gets the account attempting to create the model.
     *
     * @return The AIAccount instance.
     */
    public AIAccount getAccount() {
        return account;
    }

    /**
     * Gets the name of the platform.
     *
     * @return The platform name.
     */
    public String getPlatformName() {
        return platformName;
    }

    /**
     * Gets the ID of the model being created.
     *
     * @return The model ID.
     */
    public String getModelId() {
        return modelId;
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
