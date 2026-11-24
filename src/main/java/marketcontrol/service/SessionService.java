package marketcontrol.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import marketcontrol.domain.model.enums.Role;
import marketcontrol.domain.repository.SessionRepository;
import marketcontrol.exception.InvalidTokenException;
import marketcontrol.exception.TokenExpiredException;
import marketcontrol.http.server.infrastructure.database.JwtSecretLoader;
import marketcontrol.http.server.infrastructure.persistence.jdbc.JdbcSessionRepository;

public class SessionService {
    private static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    private static final Duration REFRESH_LIFETIME = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    public record IssuedSession(String accessToken, String refreshToken, long expiresInSeconds,
                                UUID userId, Role role) {}

    private final Algorithm algorithm;
    private final SessionRepository sessions;

    public SessionService() { this(JwtSecretLoader.CONFIG, new JdbcSessionRepository()); }

    public SessionService(String secret, SessionRepository sessions) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalArgumentException("O segredo JWT deve ter ao menos 32 bytes.");
        if (sessions == null) throw new IllegalArgumentException("Repositório de sessão obrigatório.");
        this.algorithm = Algorithm.HMAC256(secret);
        this.sessions = sessions;
    }

    public IssuedSession create(UUID userId, Role role) {
        sessions.pruneExpired();
        UUID sessionId = UUID.randomUUID();
        UUID accessTokenId = UUID.randomUUID();
        String refreshToken = randomRefreshToken();
        sessions.create(sessionId, userId, accessTokenId, hash(refreshToken),
                LocalDateTime.ofInstant(Instant.now().plus(REFRESH_LIFETIME), ZoneOffset.UTC));
        return issue(sessionId, userId, role, accessTokenId, refreshToken);
    }

    public IssuedSession refresh(String refreshToken) {
        String oldHash = hashRequired(refreshToken);
        var active = sessions.findByRefreshTokenHash(oldHash).orElseThrow(InvalidTokenException::new);
        String nextRefreshToken = randomRefreshToken();
        UUID nextAccessTokenId = UUID.randomUUID();
        if (!sessions.rotate(active.id(), oldHash, nextAccessTokenId, hash(nextRefreshToken)))
            throw new InvalidTokenException();
        return issue(active.id(), active.userId(), active.role(), nextAccessTokenId, nextRefreshToken);
    }

    public UUID userIdForRefresh(String refreshToken) {
        return sessions.findByRefreshTokenHash(hashRequired(refreshToken))
                .orElseThrow(InvalidTokenException::new).userId();
    }

    public Role authenticate(String token) {
        DecodedJWT decoded = verify(token);
        try {
            UUID sessionId = UUID.fromString(decoded.getClaim("sid").asString());
            UUID accessTokenId = UUID.fromString(decoded.getId());
            var active = sessions.findActive(sessionId, accessTokenId).orElseThrow(InvalidTokenException::new);
            if (!active.userId().equals(UUID.fromString(decoded.getSubject()))) throw new InvalidTokenException();
            return active.role();
        } catch (InvalidTokenException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new InvalidTokenException();
        }
    }

    public void logout(String refreshToken) {
        sessions.revokeByRefreshTokenHash(hashRequired(refreshToken));
    }

    private DecodedJWT verify(String token) {
        if (token == null || token.isBlank()) throw new InvalidTokenException();
        try {
            return JWT.require(algorithm).withIssuer("market-control").build().verify(token);
        } catch (com.auth0.jwt.exceptions.TokenExpiredException ex) {
            throw new TokenExpiredException();
        } catch (JWTVerificationException | IllegalArgumentException ex) {
            throw new InvalidTokenException();
        }
    }

    private IssuedSession issue(UUID sessionId, UUID userId, Role role, UUID accessTokenId, String refreshToken) {
        String accessToken = JWT.create().withIssuer("market-control").withSubject(userId.toString())
                .withJWTId(accessTokenId.toString()).withClaim("sid", sessionId.toString())
                .withClaim("profile", role.getCode()).withIssuedAt(Instant.now())
                .withExpiresAt(Instant.now().plus(ACCESS_LIFETIME)).sign(algorithm);
        return new IssuedSession(accessToken, refreshToken, ACCESS_LIFETIME.toSeconds(), userId, role);
    }

    private static String randomRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashRequired(String token) {
        if (token == null || token.isBlank()) throw new InvalidTokenException();
        return hash(token);
    }

    private static String hash(String token) {
        try {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(
					MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 não está disponível.", ex);
        }
    }
}
