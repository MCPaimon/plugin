package io.github.mcpaimon.api.model;

import java.time.Instant;

/**
 * Represents the ai_log table.
 */
public record AILog(
    long id,
    String accountType,
    String accountUuid,
    int platformId,
    String modelId,
    String chatHistory,
    int tokenTotalUsage,
    Instant createdAt
) {}
