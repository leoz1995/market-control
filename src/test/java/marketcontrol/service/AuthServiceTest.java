package marketcontrol.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import marketcontrol.domain.model.User;
import marketcontrol.domain.model.enums.Role;
import marketcontrol.domain.repository.SessionRepository;
import marketcontrol.domain.repository.UserRepository;
import marketcontrol.dtos.auth.UserAuth;
import marketcontrol.exception.AuthenticationException;
import marketcontrol.exception.InactiveAccountException;
import marketcontrol.security.SecurityPassword;

class AuthServiceTest {
    private final SecurityPassword passwords = new SecurityPassword();

    @Test
    void validCredentialsIssueTokensAndReturnSafeUserData() {
        UserAuth user = user(Role.ADMIN);
        AuthService auth = service(user);

        var response = auth.authenticate(user.email(), "correct horse battery staple");

        assertFalse(response.accessToken().isBlank());
        assertFalse(response.refreshToken().isBlank());
        assertEquals("Bearer", response.tokenType());
        assertEquals(user.id(), response.user().id());
        assertEquals(Role.ADMIN, response.user().type());
        assertEquals(900, response.expiresInSeconds());
    }

    @Test
    void invalidPasswordDoesNotAuthenticate() {
        UserAuth user = user(Role.STOCK_OPERATOR);

        assertThrows(AuthenticationException.class,
                () -> service(user).authenticate(user.email(), "wrong password"));
    }

    @Test
    void inactiveAccountCannotStartASession() {
        UserAuth user = user(Role.INACTIVE);

        assertThrows(InactiveAccountException.class,
                () -> service(user).authenticate(user.email(), "correct horse battery staple"));
    }

    private AuthService service(UserAuth user) {
        UserRepository users = new UserRepository() {
            @Override public void save(User ignored) {}
            @Override public Optional<UserAuth> authUser(String email) {
                return user.email().equals(email) ? Optional.of(user) : Optional.empty();
            }
            @Override public boolean userEmailExists(String email) { return false; }
            @Override public List<UserAuth> findAll() { return List.of(user); }
            @Override public Optional<UserAuth> findById(UUID id) {
                return user.id().equals(id) ? Optional.of(user) : Optional.empty();
            }
            @Override public void updateRole(UUID id, Role role) {}
        };
        SessionRepository sessions = new SessionRepository() {
            @Override public void create(UUID id, UUID userId, UUID accessTokenId, String hash, LocalDateTime expiresAt) {}
            @Override public void pruneExpired() {}
            @Override public Optional<ActiveSession> findActive(UUID id, UUID accessTokenId) { return Optional.empty(); }
            @Override public Optional<ActiveSession> findByRefreshTokenHash(String hash) { return Optional.empty(); }
            @Override public boolean rotate(UUID id, String oldHash, UUID accessTokenId, String nextHash) { return false; }
            @Override public void revokeByRefreshTokenHash(String hash) {}
        };
        return new AuthService(users, passwords,
                new SessionService("unit-test-secret-that-is-at-least-32-bytes", sessions));
    }

    private UserAuth user(Role role) {
        LocalDateTime now = LocalDateTime.now();
        return new UserAuth(UUID.randomUUID(), "Test User", "TEST@EXAMPLE.LOCAL",
                passwords.encode("correct horse battery staple"), null, now, role);
    }
}
