package io.github.mcpaimon.api.model;

import java.time.Instant;

/**
 * Represents the ai_accounts table.
 */
public record AIAccount(
    String accountType,
    String accountUuid,
    int platformId,
    String token,
    Instant createdAt,
    Instant updatedAt
) {}
