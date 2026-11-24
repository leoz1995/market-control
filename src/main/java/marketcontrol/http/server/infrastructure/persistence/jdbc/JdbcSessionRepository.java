package marketcontrol.http.server.infrastructure.persistence.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.enums.Role;
import marketcontrol.domain.repository.SessionRepository;
import marketcontrol.exception.PersistenceException;
import marketcontrol.http.server.infrastructure.database.DatabaseConnection;

public final class JdbcSessionRepository implements SessionRepository {
    @Override
    public void pruneExpired() {
        String sql = "DELETE FROM user_sessions WHERE expires_at <= UTC_TIMESTAMP() "
                + "OR (revoked_at IS NOT NULL AND revoked_at < UTC_TIMESTAMP() - INTERVAL 1 DAY)";
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.executeUpdate();
        } catch (SQLException e) { throw new PersistenceException("Não foi possível limpar sessões encerradas.", e); }
    }

    @Override
    public void create(UUID id, UUID userId, UUID accessTokenId, String refreshTokenHash, LocalDateTime expiresAt) {
        String sql = "INSERT INTO user_sessions (id, user_id, access_token_id, refresh_token_hash, expires_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, id.toString()); p.setString(2, userId.toString()); p.setString(3, accessTokenId.toString());
            p.setString(4, refreshTokenHash); p.setObject(5, expiresAt); p.executeUpdate();
        } catch (SQLException e) { throw new PersistenceException("Não foi possível criar a sessão.", e); }
    }

    @Override
    public Optional<ActiveSession> findActive(UUID sessionId, UUID accessTokenId) {
        String sql = "SELECT s.id, s.user_id, s.access_token_id, u.role FROM user_sessions s "
                + "JOIN users u ON u.id = s.user_id WHERE s.id = ? AND s.access_token_id = ? "
                + "AND s.revoked_at IS NULL AND s.expires_at > UTC_TIMESTAMP() AND u.role <> 1";
        return queryOne(sql, sessionId.toString(), accessTokenId.toString());
    }

    @Override
    public Optional<ActiveSession> findByRefreshTokenHash(String refreshTokenHash) {
        String sql = "SELECT s.id, s.user_id, s.access_token_id, u.role FROM user_sessions s "
                + "JOIN users u ON u.id = s.user_id WHERE s.refresh_token_hash = ? "
                + "AND s.revoked_at IS NULL AND s.expires_at > UTC_TIMESTAMP() AND u.role <> 1";
        return queryOne(sql, refreshTokenHash);
    }

    private Optional<ActiveSession> queryOne(String sql, String... values) {
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) p.setString(i + 1, values[i]);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Optional.empty();
                return Optional.of(new ActiveSession(UUID.fromString(r.getString("id")),
                        UUID.fromString(r.getString("user_id")), UUID.fromString(r.getString("access_token_id")),
                        Role.fromCode(r.getInt("role"))));
            }
        } catch (SQLException e) { throw new PersistenceException("Não foi possível consultar a sessão.", e); }
    }

    @Override
    public boolean rotate(UUID sessionId, String currentRefreshTokenHash, UUID nextAccessTokenId,
                          String nextRefreshTokenHash) {
        String sql = "UPDATE user_sessions SET access_token_id = ?, refresh_token_hash = ? "
                + "WHERE id = ? AND refresh_token_hash = ? AND revoked_at IS NULL "
                + "AND expires_at > UTC_TIMESTAMP() AND EXISTS (SELECT 1 FROM users u "
                + "WHERE u.id = user_sessions.user_id AND u.role <> 1)";
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, nextAccessTokenId.toString()); p.setString(2, nextRefreshTokenHash);
            p.setString(3, sessionId.toString()); p.setString(4, currentRefreshTokenHash);
            return p.executeUpdate() == 1;
        } catch (SQLException e) { throw new PersistenceException("Não foi possível renovar a sessão.", e); }
    }

    @Override
    public void revokeByRefreshTokenHash(String refreshTokenHash) {
        String sql = "UPDATE user_sessions SET revoked_at = CURRENT_TIMESTAMP "
                + "WHERE refresh_token_hash = ? AND revoked_at IS NULL";
        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, refreshTokenHash); p.executeUpdate();
        } catch (SQLException e) { throw new PersistenceException("Não foi possível encerrar a sessão.", e); }
    }
}
