package io.github.mcpaimon.papermc.tools.utils;

import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.tools.AITool;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Tool to get the current real-world date and time.
 */
public class GetCurrentDateTool implements AITool {
    @Override
    public String getName() { return "get_current_date"; }

    @Override
    public String getDescription() { return "Gets the current real-world date and time."; }

    @Override
    public String getParametersJsonSchema() {
        return "{ \"type\": \"object\", \"properties\": {} }";
    }

    @Override
    public CompletableFuture<String> execute(Map<String, Object> arguments, AIAccount account) {
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        return CompletableFuture.completedFuture("The current real-world time is: " + currentTime);
    }
}
