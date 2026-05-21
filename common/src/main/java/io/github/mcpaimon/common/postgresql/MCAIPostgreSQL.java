package io.github.mcpaimon.common.database.postgresql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.mcpaimon.api.database.IAIDatabase;
import io.github.mcpaimon.api.model.AIAccount;
import io.github.mcpaimon.api.model.AIActiveSession;
import io.github.mcpaimon.api.model.AILog;
import io.github.mcpaimon.api.model.AIModel;
import io.github.mcpaimon.api.model.AIPlatform;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * PostgreSQL Database implementation.
 */
public class MCAIPostgreSQL implements IAIDatabase {

    private final HikariDataSource dataSource;

    public MCAIPostgreSQL(String host, int port, String database, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        this.dataSource = new HikariDataSource(config);
    }

    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS ai_platforms (id SERIAL PRIMARY KEY, display_name TEXT NOT NULL, url TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
                stmt.execute("CREATE TABLE IF NOT EXISTS ai_models (platform_id INT NOT NULL, model_id TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (platform_id, model_id), FOREIGN KEY (platform_id) REFERENCES ai_platforms(id) ON DELETE CASCADE)");
                stmt.execute("CREATE TABLE IF NOT EXISTS ai_accounts (account_type VARCHAR(100) NOT NULL, account_uuid VARCHAR(36) NOT NULL, platform_id INT NOT NULL, token TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid, platform_id), FOREIGN KEY (platform_id) REFERENCES ai_platforms(id) ON DELETE CASCADE)");
                stmt.execute("CREATE TABLE IF NOT EXISTS ai_log (id BIGSERIAL PRIMARY KEY, account_type VARCHAR(100) NOT NULL, account_uuid VARCHAR(36) NOT NULL, platform_id INT NOT NULL, model_id TEXT NOT NULL, chat_history TEXT NOT NULL, token_total_usage INT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (account_type, account_uuid, platform_id) REFERENCES ai_accounts(account_type, account_uuid, platform_id) ON DELETE RESTRICT, FOREIGN KEY (platform_id, model_id) REFERENCES ai_models(platform_id, model_id) ON DELETE RESTRICT)");
                stmt.execute("CREATE TABLE IF NOT EXISTS ai_account_active_session (account_type VARCHAR(100) NOT NULL, account_uuid VARCHAR(36) NOT NULL, platform_id INT NOT NULL, model_id TEXT NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (account_type, account_uuid), FOREIGN KEY (platform_id, model_id) REFERENCES ai_models(platform_id, model_id) ON DELETE CASCADE)");
            } catch (SQLException e) { 
                throw new RuntimeException("Failed to initialize PostgreSQL schema", e); 
            }
        });
    }

    @Override 
    public CompletableFuture<Void> close() { 
        return CompletableFuture.runAsync(dataSource::close); 
    }

    @Override
    public CompletableFuture<AIPlatform> createPlatform(String displayName, String url) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO ai_platforms (display_name, url) VALUES (?, ?) RETURNING id, created_at, updated_at";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, displayName); 
                ps.setString(2, url);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new AIPlatform(rs.getInt("id"), displayName, url, rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
                    }
                    throw new SQLException("Creating platform failed, no ID obtained.");
                }
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
        });
    }

    @Override
    public CompletableFuture<Optional<AIPlatform>> getPlatform(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM ai_platforms WHERE id = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new AIPlatform(rs.getInt("id"), rs.getString("display_name"), rs.getString("url"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
        });
    }

    @Override
    public CompletableFuture<List<AIPlatform>> getAllPlatforms() {
        return CompletableFuture.supplyAsync(() -> {
            List<AIPlatform> platforms = new ArrayList<>();
            String sql = "SELECT * FROM ai_platforms";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    platforms.add(new AIPlatform(rs.getInt("id"), rs.getString("display_name"), rs.getString("url"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
                }
                return platforms;
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
        });
    }

    @Override
    public CompletableFuture<AIModel> createModel(int platformId, String modelId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO ai_models (platform_id, model_id) VALUES (?, ?) ON CONFLICT (platform_id, model_id) DO NOTHING RETURNING created_at, updated_at";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, platformId); 
                ps.setString(2, modelId); 
                ps.execute();
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
            return getModel(platformId, modelId).join().orElseThrow();
        });
    }

    @Override
    public CompletableFuture<List<AIModel>> getModelsByPlatform(int platformId) {
        return CompletableFuture.supplyAsync(() -> {
            List<AIModel> models = new ArrayList<>();
            String sql = "SELECT * FROM ai_models WHERE platform_id = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, platformId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        models.add(new AIModel(rs.getInt("platform_id"), rs.getString("model_id"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
                    }
                }
                return models;
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
        });
    }

    @Override
    public CompletableFuture<Optional<AIModel>> getModel(int platformId, String modelId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM ai_models WHERE platform_id = ? AND model_id = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, platformId); 
                ps.setString(2, modelId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new AIModel(rs.getInt("platform_id"), rs.getString("model_id"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
        });
    }

    @Override
    public CompletableFuture<AIAccount> createOrUpdateAccount(String accountType, String accountUuid, int platformId, String token) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO ai_accounts (account_type, account_uuid, platform_id, token) VALUES (?, ?, ?, ?) ON CONFLICT (account_type, account_uuid, platform_id) DO UPDATE SET token = EXCLUDED.token, updated_at = CURRENT_TIMESTAMP";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accountType); 
                ps.setString(2, accountUuid); 
                ps.setInt(3, platformId); 
                ps.setString(4, token); 
                ps.executeUpdate();
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
            return getAccount(accountType, accountUuid, platformId).join().orElseThrow();
        });
    }

    @Override
    public CompletableFuture<Optional<AIAccount>> getAccount(String accountType, String accountUuid, int platformId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM ai_accounts WHERE account_type = ? AND account_uuid = ? AND platform_id = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps =prepareStatement(sql)) {
                ps.setString(1, accountType); 
                ps.setString(2, accountUuid); 
                ps.setInt(3, platformId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new AIAccount(rs.getString("account_type"), rs.getString("account_uuid"), rs.getInt("platform_id"), rs.getString("token"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
        });
    }
    
    private PreparedStatement prepareStatement(String sql) throws SQLException {
        return dataSource.getConnection().prepareStatement(sql);
    }

    @Override
    public CompletableFuture<Void> deleteAccount(String accountType, String accountUuid) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM ai_accounts WHERE account_type = ? AND account_uuid = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accountType); 
                ps.setString(2, accountUuid); 
                ps.executeUpdate();
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
        });
    }

    @Override
    public CompletableFuture<Void> setActiveSession(String accountType, String accountUuid, int platformId, String modelId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO ai_account_active_session (account_type, account_uuid, platform_id, model_id) VALUES (?, ?, ?, ?) ON CONFLICT (account_type, account_uuid) DO UPDATE SET platform_id = EXCLUDED.platform_id, model_id = EXCLUDED.model_id, updated_at = CURRENT_TIMESTAMP";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accountType); 
                ps.setString(2, accountUuid); 
                ps.setInt(3, platformId); 
                ps.setString(4, modelId); 
                ps.executeUpdate();
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
        });
    }

    @Override
    public CompletableFuture<Optional<AIActiveSession>> getActiveSession(String accountType, String accountUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM ai_account_active_session WHERE account_type = ? AND account_uuid = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accountType); 
                ps.setString(2, accountUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new AIActiveSession(rs.getString("account_type"), rs.getString("account_uuid"), rs.getInt("platform_id"), rs.getString("model_id"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
                    }
                    return Optional.empty();
                }
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
        });
    }

    @Override
    public CompletableFuture<AILog> insertLog(String accountType, String accountUuid, int platformId, String modelId, String chatHistory, int tokenTotalUsage) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO ai_log (account_type, account_uuid, platform_id, model_id, chat_history, token_total_usage) VALUES (?, ?, ?, ?, ?, ?) RETURNING id, created_at";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accountType); 
                ps.setString(2, accountUuid); 
                ps.setInt(3, platformId); 
                ps.setString(4, modelId); 
                ps.setString(5, chatHistory); 
                ps.setInt(6, tokenTotalUsage);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new AILog(rs.getLong("id"), accountType, accountUuid, platformId, modelId, chatHistory, tokenTotalUsage, rs.getTimestamp("created_at").toInstant());
                    }
                    throw new SQLException("Inserting log failed.");
                }
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
        });
    }

    @Override
    public CompletableFuture<List<AILog>> getLogsByAccount(String accountType, String accountUuid, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<AILog> logs = new ArrayList<>();
            String sql = "SELECT * FROM ai_log WHERE account_type = ? AND account_uuid = ? ORDER BY created_at DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, accountType); 
                ps.setString(2, accountUuid); 
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        logs.add(new AILog(rs.getLong("id"), rs.getString("account_type"), rs.getString("account_uuid"), rs.getInt("platform_id"), rs.getString("model_id"), rs.getString("chat_history"), rs.getInt("token_total_usage"), rs.getTimestamp("created_at").toInstant()));
                    }
                }
                return logs;
            } catch (SQLException e) { 
                throw new RuntimeException(e); 
            }
        });
    }
}
