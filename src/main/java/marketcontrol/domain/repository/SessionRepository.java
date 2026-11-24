package marketcontrol.domain.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import marketcontrol.domain.model.enums.Role;

public interface SessionRepository {
    record ActiveSession(UUID id, UUID userId, UUID accessTokenId, Role role) {}

    void create(UUID id, UUID userId, UUID accessTokenId, String refreshTokenHash, LocalDateTime expiresAt);
    void pruneExpired();
    Optional<ActiveSession> findActive(UUID sessionId, UUID accessTokenId);
    Optional<ActiveSession> findByRefreshTokenHash(String refreshTokenHash);
    boolean rotate(UUID sessionId, String currentRefreshTokenHash, UUID nextAccessTokenId, String nextRefreshTokenHash);
    void revokeByRefreshTokenHash(String refreshTokenHash);
}
