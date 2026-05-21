package io.github.mcpaimon.api.model;

import java.time.Instant;

/**
 * Represents the ai_platforms table.
 */
public record AIPlatform(
    int id,
    String displayName,
    String url,
    Instant createdAt,
    Instant updatedAt
) {}
