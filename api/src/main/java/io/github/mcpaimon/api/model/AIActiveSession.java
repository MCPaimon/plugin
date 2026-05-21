package io.github.mcpaimon.api.model;

import java.time.Instant;

/**
 * Represents the ai_account_active_session table.
 */
public record AIActiveSession(
    String accountType,
    String accountUuid,
    int platformId,
    String modelId,
    Instant createdAt,
    Instant updatedAt
) {}
