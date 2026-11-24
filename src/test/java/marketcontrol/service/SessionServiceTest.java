package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.enums.Role;
import marketcontrol.domain.repository.SessionRepository;
import marketcontrol.exception.InvalidTokenException;

class SessionServiceTest {
    private final InMemorySessions repository = new InMemorySessions();
    private final SessionService sessions = new SessionService("unit-test-secret-that-is-at-least-32-bytes", repository);

    @BeforeEach
    void clearSessions() {
        repository.clear();
    }

    @Test
    void issuedAccessTokenAuthenticatesAsPersistedRole() {
        var issued = issue(Role.STOCK_OPERATOR);

        assertEquals(Role.STOCK_OPERATOR, sessions.authenticate(issued.accessToken()));
    }

    @Test
    void refreshRotatesBothTokensAndRejectsThePreviousPair() {
        var original = issue(Role.ADMIN);
        var refreshed = sessions.refresh(original.refreshToken());

        assertThrows(InvalidTokenException.class, () -> sessions.authenticate(original.accessToken()));
        assertThrows(InvalidTokenException.class, () -> sessions.refresh(original.refreshToken()));
        assertEquals(Role.ADMIN, sessions.authenticate(refreshed.accessToken()));
    }

    @Test
    void logoutRevokesTheSessionAndItsAccessToken() {
        var issued = issue(Role.ADMIN);

        sessions.logout(issued.refreshToken());

        assertThrows(InvalidTokenException.class, () -> sessions.authenticate(issued.accessToken()));
        assertThrows(InvalidTokenException.class, () -> sessions.refresh(issued.refreshToken()));
    }

    @Test
    void malformedTokensAreRejected() {
        assertThrows(InvalidTokenException.class, () -> sessions.authenticate("not-a-jwt"));
    }

    private SessionService.IssuedSession issue(Role role) {
        UUID userId = UUID.randomUUID();
        repository.setRole(userId, role);
        return sessions.create(userId, role);
    }

    private static final class InMemorySessions implements SessionRepository {
        private final Map<UUID, ActiveSession> byId = new ConcurrentHashMap<>();
        private final Map<UUID, String> refreshHashes = new ConcurrentHashMap<>();
        private final Map<UUID, LocalDateTime> expirations = new ConcurrentHashMap<>();
        private final Map<UUID, Role> roles = new ConcurrentHashMap<>();
        private final Map<UUID, Boolean> revoked = new ConcurrentHashMap<>();

        void setRole(UUID userId, Role role) { roles.put(userId, role); }

        void clear() {
            byId.clear();
            refreshHashes.clear();
            expirations.clear();
            roles.clear();
            revoked.clear();
        }

        @Override
        public void create(UUID id, UUID userId, UUID accessTokenId, String refreshTokenHash, LocalDateTime expiresAt) {
            byId.put(id, new ActiveSession(id, userId, accessTokenId, roles.get(userId)));
            refreshHashes.put(id, refreshTokenHash);
            expirations.put(id, expiresAt);
            revoked.put(id, false);
        }

        @Override public void pruneExpired() {}

        @Override
        public Optional<ActiveSession> findActive(UUID sessionId, UUID accessTokenId) {
            return Optional.ofNullable(byId.get(sessionId))
                    .filter(active -> active.accessTokenId().equals(accessTokenId))
                    .filter(active -> !revoked.getOrDefault(sessionId, true))
                    .filter(active -> expirations.get(sessionId).isAfter(LocalDateTime.now()));
        }

        @Override
        public Optional<ActiveSession> findByRefreshTokenHash(String refreshTokenHash) {
            return byId.values().stream()
                    .filter(active -> refreshHashes.get(active.id()).equals(refreshTokenHash))
                    .filter(active -> !revoked.getOrDefault(active.id(), true))
                    .filter(active -> expirations.get(active.id()).isAfter(LocalDateTime.now()))
                    .findFirst();
        }

        @Override
        public boolean rotate(UUID sessionId, String currentRefreshTokenHash, UUID nextAccessTokenId,
                              String nextRefreshTokenHash) {
            ActiveSession active = byId.get(sessionId);
            if (active == null || !refreshHashes.get(sessionId).equals(currentRefreshTokenHash)
                    || revoked.getOrDefault(sessionId, true)
                    || !expirations.get(sessionId).isAfter(LocalDateTime.now())) return false;
            byId.put(sessionId, new ActiveSession(sessionId, active.userId(), nextAccessTokenId, active.role()));
            refreshHashes.put(sessionId, nextRefreshTokenHash);
            return true;
        }

        @Override
        public void revokeByRefreshTokenHash(String refreshTokenHash) {
            byId.values().stream().filter(active -> refreshHashes.get(active.id()).equals(refreshTokenHash))
                    .forEach(active -> revoked.put(active.id(), true));
        }
    }
}
