package io.github.mcpaimon.api.model;

import java.time.Instant;

/**
 * Represents the ai_models table.
 */
public record AIModel(
    int platformId,
    String modelId,
    Instant createdAt,
    Instant updatedAt
) {}
