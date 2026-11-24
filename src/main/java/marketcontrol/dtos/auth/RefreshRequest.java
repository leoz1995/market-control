package marketcontrol.dtos.auth;

public record RefreshRequest(String refreshToken) {
    public RefreshRequest {
        if (refreshToken == null || refreshToken.isBlank()) throw new IllegalArgumentException("O refresh token é obrigatório.");
    }
}
