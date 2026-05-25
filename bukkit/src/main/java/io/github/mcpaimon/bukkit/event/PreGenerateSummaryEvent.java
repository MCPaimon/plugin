package io.github.mcpaimon.bukkit.event;

import io.github.mcpaimon.api.model.AIAccount;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Event fired before the AI generates its final summary in the second round.
 * This allows other plugins to append custom context or messages to the tool results.
 * This event is fired asynchronously.
 */
public class PreGenerateSummaryEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final AIAccount account;
    private final String prompt;
    private String toolResults;

    /**
     * Constructs a new PreGenerateSummaryEvent.
     *
     * @param account     The AI account processing the prompt.
     * @param prompt      The original user prompt.
     * @param toolResults The raw string results from the executed tools.
     */
    public PreGenerateSummaryEvent(AIAccount account, String prompt, String toolResults) {
        super(true); // Is async
        this.account = account;
        this.prompt = prompt;
        this.toolResults = toolResults;
    }

    /**
     * Gets the account processing the prompt.
     *
     * @return The AIAccount instance.
     */
    public AIAccount getAccount() {
        return account;
    }

    /**
     * Gets the original prompt sent by the user.
     *
     * @return The user prompt.
     */
    public String getPrompt() {
        return prompt;
    }

    /**
     * Gets the current tool results that will be sent to the AI.
     *
     * @return The tool results string.
     */
    public String getToolResults() {
        return toolResults;
    }

    /**
     * Overrides the tool results entirely.
     *
     * @param toolResults The new tool results string.
     */
    public void setToolResults(String toolResults) {
        this.toolResults = toolResults;
    }

    /**
     * Appends additional context or messages to the current tool results.
     *
     * @param additionalContext The context to append.
     */
    public void appendToolResult(String additionalContext) {
        if (this.toolResults == null || this.toolResults.isEmpty()) {
            this.toolResults = additionalContext;
        } else {
            this.toolResults += "\n" + additionalContext;
        }
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
